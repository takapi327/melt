/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.lsp

import java.nio.file.{ Files, Path }

import org.eclipse.lsp4j.{ FileChangeType, FileEvent }

class ImportPathCompletionProviderSpec extends munit.FunSuite:

  // ── detectImportPrefix ────────────────────────────────────────────────────

  test("detectImportPrefix returns Some(\"\") when cursor is right after opening quote") {
    val line = """import """"
    assertEquals(ImportPathCompletionProvider.detectImportPrefix(line, line.length), Some(""))
  }

  test("detectImportPrefix returns Some(partial) when cursor is inside an import string") {
    val line = """import "/sty"""
    assertEquals(ImportPathCompletionProvider.detectImportPrefix(line, line.length), Some("/sty"))
  }

  test("detectImportPrefix returns Some with leading whitespace stripped from line") {
    val line = """  import "/styles/"""
    assertEquals(ImportPathCompletionProvider.detectImportPrefix(line, line.length), Some("/styles/"))
  }

  test("detectImportPrefix returns None for a closed import string (cursor after closing quote)") {
    val line = """import "/styles/global.css" """
    // cursor is past the closing quote
    assertEquals(ImportPathCompletionProvider.detectImportPrefix(line, line.length), None)
  }

  test("detectImportPrefix returns None for a regular Scala import") {
    val line = "import scala.math.*"
    assertEquals(ImportPathCompletionProvider.detectImportPrefix(line, line.length), None)
  }

  test("detectImportPrefix returns None for an unrelated line") {
    val line = "val x = 1"
    assertEquals(ImportPathCompletionProvider.detectImportPrefix(line, line.length), None)
  }

  test("detectImportPrefix is sensitive to cursor position — before closing quote") {
    // `import "/styles/global.css"` — cursor at position right after the slash
    val line     = """import "/styles/global.css""""
    val afterSlash = """import "/styles/""".length
    assertEquals(ImportPathCompletionProvider.detectImportPrefix(line, afterSlash), Some("/styles/"))
  }

  // ── completionsFor ────────────────────────────────────────────────────────

  test("completionsFor returns Nil when workspaceRoot is None") {
    val items = ImportPathCompletionProvider.completionsFor("/styles/", None)
    assertEquals(items, Nil)
  }

  test("completionsFor returns Nil when workspaceRoot does not exist") {
    val nonExistent = Path.of("/tmp/melt-lsp-test-nonexistent-12345")
    val items       = ImportPathCompletionProvider.completionsFor("/", Some(nonExistent))
    assertEquals(items, Nil)
  }

  test("completionsFor finds .css files under workspace root") {
    val tmpDir = Files.createTempDirectory("melt-lsp-test")
    val stylesDir = tmpDir.resolve("styles")
    Files.createDirectories(stylesDir)
    Files.createFile(stylesDir.resolve("global.css"))
    Files.createFile(stylesDir.resolve("theme.css"))

    try
      val items = ImportPathCompletionProvider.completionsFor("/", Some(tmpDir))
      val labels = items.map(_.getLabel)
      assert(labels.contains("/styles/global.css"), s"Expected /styles/global.css in $labels")
      assert(labels.contains("/styles/theme.css"), s"Expected /styles/theme.css in $labels")
    finally
      Files.walk(tmpDir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)
  }

  test("completionsFor finds .scss and .js files") {
    val tmpDir = Files.createTempDirectory("melt-lsp-test")
    Files.createFile(tmpDir.resolve("app.scss"))
    Files.createFile(tmpDir.resolve("plugin.js"))

    try
      val items  = ImportPathCompletionProvider.completionsFor("/", Some(tmpDir))
      val labels = items.map(_.getLabel)
      assert(labels.contains("/app.scss"), s"Expected /app.scss in $labels")
      assert(labels.contains("/plugin.js"), s"Expected /plugin.js in $labels")
    finally
      Files.walk(tmpDir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)
  }

  test("completionsFor does not include unsupported file types") {
    val tmpDir = Files.createTempDirectory("melt-lsp-test")
    Files.createFile(tmpDir.resolve("readme.md"))
    Files.createFile(tmpDir.resolve("config.json"))
    Files.createFile(tmpDir.resolve("style.css"))

    try
      val items  = ImportPathCompletionProvider.completionsFor("/", Some(tmpDir))
      val labels = items.map(_.getLabel)
      assert(!labels.exists(_.endsWith(".md")),   s"Unexpected .md file in $labels")
      assert(!labels.exists(_.endsWith(".json")), s"Unexpected .json file in $labels")
      assert(labels.contains("/style.css"), s"Expected /style.css in $labels")
    finally
      Files.walk(tmpDir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)
  }

  test("completionsFor filters by prefix") {
    val tmpDir    = Files.createTempDirectory("melt-lsp-test")
    val stylesDir = tmpDir.resolve("styles")
    val pluginDir = tmpDir.resolve("plugins")
    Files.createDirectories(stylesDir)
    Files.createDirectories(pluginDir)
    Files.createFile(stylesDir.resolve("global.css"))
    Files.createFile(pluginDir.resolve("analytics.js"))

    try
      val items = ImportPathCompletionProvider.completionsFor("/styles/", Some(tmpDir))
      val labels = items.map(_.getLabel)
      assert(labels.contains("/styles/global.css"),    s"Expected /styles/global.css in $labels")
      assert(!labels.contains("/plugins/analytics.js"), s"Unexpected /plugins/analytics.js in $labels")
    finally
      Files.walk(tmpDir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)
  }

  test("completionsFor returns items sorted alphabetically") {
    val tmpDir = Files.createTempDirectory("melt-lsp-test")
    Files.createFile(tmpDir.resolve("z-last.css"))
    Files.createFile(tmpDir.resolve("a-first.css"))
    Files.createFile(tmpDir.resolve("m-middle.css"))

    try
      val labels = ImportPathCompletionProvider.completionsFor("/", Some(tmpDir)).map(_.getLabel)
      assertEquals(labels, labels.sorted)
    finally
      Files.walk(tmpDir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)
  }

  test("CSS completion item has File kind and 'CSS import' detail") {
    val tmpDir = Files.createTempDirectory("melt-lsp-test")
    Files.createFile(tmpDir.resolve("global.css"))

    try
      val items = ImportPathCompletionProvider.completionsFor("/", Some(tmpDir))
      val item  = items.find(_.getLabel == "/global.css").getOrElse(fail("item not found"))
      assertEquals(item.getKind, org.eclipse.lsp4j.CompletionItemKind.File)
      assertEquals(item.getDetail, "CSS import")
    finally
      Files.walk(tmpDir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)
  }

  test("JS completion item has Module kind") {
    val tmpDir = Files.createTempDirectory("melt-lsp-test")
    Files.createFile(tmpDir.resolve("app.js"))

    try
      val items = ImportPathCompletionProvider.completionsFor("/", Some(tmpDir))
      val item  = items.find(_.getLabel == "/app.js").getOrElse(fail("item not found"))
      assertEquals(item.getKind, org.eclipse.lsp4j.CompletionItemKind.Module)
    finally
      Files.walk(tmpDir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)
  }

  // ── excluded directories ──────────────────────────────────────────────────

  test("completionsFor excludes files under node_modules") {
    val tmpDir      = Files.createTempDirectory("melt-lsp-test")
    val nodeModules = tmpDir.resolve("node_modules/some-pkg")
    Files.createDirectories(nodeModules)
    Files.createFile(nodeModules.resolve("index.css"))
    Files.createFile(tmpDir.resolve("app.css"))

    try
      val labels = ImportPathCompletionProvider.completionsFor("/", Some(tmpDir)).map(_.getLabel)
      assert(!labels.exists(_.contains("node_modules")), s"node_modules should be excluded: $labels")
      assert(labels.contains("/app.css"), s"Expected /app.css in $labels")
    finally
      Files.walk(tmpDir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)
  }

  test("completionsFor excludes files under .git") {
    val tmpDir  = Files.createTempDirectory("melt-lsp-test")
    val gitDir  = tmpDir.resolve(".git")
    Files.createDirectories(gitDir)
    Files.createFile(gitDir.resolve("config.css")) // artificial
    Files.createFile(tmpDir.resolve("main.css"))

    try
      val labels = ImportPathCompletionProvider.completionsFor("/", Some(tmpDir)).map(_.getLabel)
      assert(!labels.exists(_.contains(".git")), s".git should be excluded: $labels")
      assert(labels.contains("/main.css"), s"Expected /main.css in $labels")
    finally
      Files.walk(tmpDir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)
  }

  test("completionsFor excludes files under target") {
    val tmpDir    = Files.createTempDirectory("melt-lsp-test")
    val targetDir = tmpDir.resolve("target/classes")
    Files.createDirectories(targetDir)
    Files.createFile(targetDir.resolve("output.css")) // artificial
    Files.createFile(tmpDir.resolve("src.css"))

    try
      val labels = ImportPathCompletionProvider.completionsFor("/", Some(tmpDir)).map(_.getLabel)
      assert(!labels.exists(_.contains("target")), s"target should be excluded: $labels")
      assert(labels.contains("/src.css"), s"Expected /src.css in $labels")
    finally
      Files.walk(tmpDir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)
  }

  // ── additional supported extensions ──────────────────────────────────────

  test("completionsFor finds .less, .sass, .mjs, .ts, .jsx, .tsx files") {
    val tmpDir = Files.createTempDirectory("melt-lsp-test")
    val exts   = List("app.less", "theme.sass", "util.mjs", "component.ts", "view.jsx", "page.tsx")
    exts.foreach(name => Files.createFile(tmpDir.resolve(name)))

    try
      val labels = ImportPathCompletionProvider.completionsFor("/", Some(tmpDir)).map(_.getLabel)
      exts.foreach { name =>
        assert(labels.contains(s"/$name"), s"Expected /$name in $labels")
      }
    finally
      Files.walk(tmpDir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)
  }

  test(".ts and .tsx completion items have Module kind") {
    val tmpDir = Files.createTempDirectory("melt-lsp-test")
    Files.createFile(tmpDir.resolve("comp.ts"))
    Files.createFile(tmpDir.resolve("page.tsx"))

    try
      val items = ImportPathCompletionProvider.completionsFor("/", Some(tmpDir))
      val ts    = items.find(_.getLabel == "/comp.ts").getOrElse(fail("/comp.ts not found"))
      val tsx   = items.find(_.getLabel == "/page.tsx").getOrElse(fail("/page.tsx not found"))
      assertEquals(ts.getKind, org.eclipse.lsp4j.CompletionItemKind.Module)
      assertEquals(tsx.getKind, org.eclipse.lsp4j.CompletionItemKind.Module)
    finally
      Files.walk(tmpDir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)
  }

  test(".less and .sass completion items have File kind and 'CSS import' detail") {
    val tmpDir = Files.createTempDirectory("melt-lsp-test")
    Files.createFile(tmpDir.resolve("vars.less"))
    Files.createFile(tmpDir.resolve("base.sass"))

    try
      val items = ImportPathCompletionProvider.completionsFor("/", Some(tmpDir))
      val less  = items.find(_.getLabel == "/vars.less").getOrElse(fail("/vars.less not found"))
      val sass  = items.find(_.getLabel == "/base.sass").getOrElse(fail("/base.sass not found"))
      assertEquals(less.getKind, org.eclipse.lsp4j.CompletionItemKind.File)
      assertEquals(less.getDetail, "CSS import")
      assertEquals(sass.getKind, org.eclipse.lsp4j.CompletionItemKind.File)
      assertEquals(sass.getDetail, "CSS import")
    finally
      Files.walk(tmpDir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)
  }

  // ── handleFileEvent ───────────────────────────────────────────────────────

  test("handleFileEvent: Created event adds a supported file to the index") {
    val tmpDir  = Files.createTempDirectory("melt-lsp-handle-test")
    val css     = tmpDir.resolve("styles/global.css")
    Files.createDirectories(css.getParent)
    Files.createFile(css)
    val index = scala.collection.concurrent.TrieMap.empty[String, org.eclipse.lsp4j.CompletionItem]

    try
      val event = FileEvent(css.toUri.toString, FileChangeType.Created)
      ImportPathCompletionProvider.handleFileEvent(tmpDir, event, index)
      assert(index.contains("/styles/global.css"), s"Expected /styles/global.css in index: ${ index.keys }")
    finally
      Files.walk(tmpDir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)
  }

  test("handleFileEvent: Deleted event removes a file from the index") {
    val tmpDir = Files.createTempDirectory("melt-lsp-handle-test")
    val css    = tmpDir.resolve("app.css")
    Files.createFile(css)
    val existing = org.eclipse.lsp4j.CompletionItem("/app.css")
    val index    = scala.collection.concurrent.TrieMap("/app.css" -> existing)

    try
      val event = FileEvent(css.toUri.toString, FileChangeType.Deleted)
      ImportPathCompletionProvider.handleFileEvent(tmpDir, event, index)
      assert(!index.contains("/app.css"), "Expected /app.css to be removed from index")
    finally
      Files.walk(tmpDir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)
  }

  test("handleFileEvent: Changed event updates the entry in the index") {
    val tmpDir = Files.createTempDirectory("melt-lsp-handle-test")
    val js     = tmpDir.resolve("app.js")
    Files.createFile(js)
    val stale = org.eclipse.lsp4j.CompletionItem("stale")
    val index = scala.collection.concurrent.TrieMap("/app.js" -> stale)

    try
      val event = FileEvent(js.toUri.toString, FileChangeType.Changed)
      ImportPathCompletionProvider.handleFileEvent(tmpDir, event, index)
      val item = index.getOrElse("/app.js", fail("/app.js not found in index"))
      assertEquals(item.getLabel, "/app.js")
    finally
      Files.walk(tmpDir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)
  }

  test("handleFileEvent: unsupported extension is not added to index") {
    val tmpDir = Files.createTempDirectory("melt-lsp-handle-test")
    val md     = tmpDir.resolve("readme.md")
    Files.createFile(md)
    val index = scala.collection.concurrent.TrieMap.empty[String, org.eclipse.lsp4j.CompletionItem]

    try
      val event = FileEvent(md.toUri.toString, FileChangeType.Created)
      ImportPathCompletionProvider.handleFileEvent(tmpDir, event, index)
      assert(index.isEmpty, s"Expected empty index for unsupported extension, got: ${ index.keys }")
    finally
      Files.walk(tmpDir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)
  }

  test("handleFileEvent: file inside excluded directory is not added to index") {
    val tmpDir = Files.createTempDirectory("melt-lsp-handle-test")
    val nm     = tmpDir.resolve("node_modules/pkg/index.css")
    Files.createDirectories(nm.getParent)
    Files.createFile(nm)
    val index = scala.collection.concurrent.TrieMap.empty[String, org.eclipse.lsp4j.CompletionItem]

    try
      val event = FileEvent(nm.toUri.toString, FileChangeType.Created)
      ImportPathCompletionProvider.handleFileEvent(tmpDir, event, index)
      assert(index.isEmpty, s"Expected empty index for excluded dir, got: ${ index.keys }")
    finally
      Files.walk(tmpDir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)
  }

  test("handleFileEvent: invalid URI is swallowed without crashing") {
    val tmpDir = Files.createTempDirectory("melt-lsp-handle-test")
    val index  = scala.collection.concurrent.TrieMap.empty[String, org.eclipse.lsp4j.CompletionItem]

    try
      val event = FileEvent("not-a-valid:::uri", FileChangeType.Created)
      ImportPathCompletionProvider.handleFileEvent(tmpDir, event, index)
      assert(index.isEmpty)
    finally
      Files.walk(tmpDir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)
  }
