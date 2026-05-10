/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import melt.runtime.render.RenderResult

import meltkit.*

/** Tests for [[ImportTagResolver]] and [[ViteManifest.fileForSourcePath]].
  *
  * Covers the end-to-end flow: import paths declared in `.melt` script sections
  * are resolved via the Vite manifest and injected as `<link>` / `<script>` tags
  * into the component's head by the adapter contexts.
  */
class TemplateImportTest extends munit.FunSuite:

  // Manifest keys are WITHOUT leading `/` (matching real Vite manifest JSON format)
  val manifest: ViteManifest = ViteManifest.fromEntries(
    Map(
      "styles/global.css" -> ViteManifest.Entry(file = "assets/global-abc123.css"),
      "styles/theme.css"  -> ViteManifest.Entry(file = "assets/theme-def456.css"),
      "plugins/app.js"    -> ViteManifest.Entry(file = "assets/app-xyz789.js")
    ),
    uriPrefix = "scalajs"
  )

  // ── ViteManifest.fileForSourcePath ────────────────────────────────────

  test("fileForSourcePath strips leading slash and returns hashed path") {
    assertEquals(
      manifest.fileForSourcePath("/styles/global.css"),
      Some("assets/global-abc123.css")
    )
  }

  test("fileForSourcePath also matches when source path has no leading slash") {
    assertEquals(
      manifest.fileForSourcePath("styles/global.css"),
      Some("assets/global-abc123.css")
    )
  }

  test("fileForSourcePath returns None when path is not in manifest") {
    assertEquals(manifest.fileForSourcePath("/styles/missing.css"), None)
  }

  test("fileForSourcePath returns None on empty manifest") {
    assertEquals(ViteManifest.empty.fileForSourcePath("/styles/global.css"), None)
  }

  test("fileForSourcePath returns None when entry has empty file string") {
    val m = ViteManifest.fromEntries(
      Map("styles/broken.css" -> ViteManifest.Entry(file = "")),
      uriPrefix = "scalajs"
    )
    assertEquals(m.fileForSourcePath("/styles/broken.css"), None)
  }

  // ── ImportTagResolver.resolveTag ─────────────────────────────────────

  test("resolveTag: CSS path found in manifest emits <link> with hashed href") {
    val tag = ImportTagResolver.resolveTag("/styles/global.css", manifest, basePath = "")
    assertEquals(tag, """<link rel="stylesheet" href="/assets/global-abc123.css">""")
  }

  test("resolveTag: CSS path NOT in manifest is served as-is") {
    val tag = ImportTagResolver.resolveTag("/styles/unknown.css", manifest, basePath = "")
    assertEquals(tag, """<link rel="stylesheet" href="/styles/unknown.css">""")
  }

  test("resolveTag: JS path found in manifest emits <script type=module>") {
    val tag = ImportTagResolver.resolveTag("/plugins/app.js", manifest, basePath = "")
    assertEquals(tag, """<script type="module" src="/assets/app-xyz789.js"></script>""")
  }

  test("resolveTag: basePath is prepended to the manifest-resolved path") {
    val tag = ImportTagResolver.resolveTag("/styles/global.css", manifest, basePath = "/myapp")
    assertEquals(tag, """<link rel="stylesheet" href="/myapp/assets/global-abc123.css">""")
  }

  test("resolveTag: unknown extension returns empty string") {
    val tag = ImportTagResolver.resolveTag("/data/config.yaml", manifest, basePath = "")
    assertEquals(tag, "")
  }

  test("resolveTag: .scss extension emits <link rel=stylesheet>") {
    val scssManifest = ViteManifest.fromEntries(
      Map("styles/app.scss" -> ViteManifest.Entry(file = "assets/app-hash.css")),
      uriPrefix = "scalajs"
    )
    val tag = ImportTagResolver.resolveTag("/styles/app.scss", scssManifest, basePath = "")
    assertEquals(tag, """<link rel="stylesheet" href="/assets/app-hash.css">""")
  }

  test("resolveTag: .less and .sass extensions emit <link rel=stylesheet>") {
    val tag1 = ImportTagResolver.resolveTag("/styles/vars.less", ViteManifest.empty, basePath = "")
    assertEquals(tag1, """<link rel="stylesheet" href="/styles/vars.less">""")
    val tag2 = ImportTagResolver.resolveTag("/styles/base.sass", ViteManifest.empty, basePath = "")
    assertEquals(tag2, """<link rel="stylesheet" href="/styles/base.sass">""")
  }

  test("resolveTag: .mjs, .ts, .jsx, .tsx extensions emit <script type=module>") {
    val paths = List("/util.mjs", "/comp.ts", "/view.jsx", "/page.tsx")
    paths.foreach { p =>
      val tag = ImportTagResolver.resolveTag(p, ViteManifest.empty, basePath = "")
      assert(tag.startsWith("""<script type="module""""), s"Expected script tag for $p, got: $tag")
    }
  }

  test("resolveTag: Escape.attr is applied to href — special chars in path are escaped") {
    // A path with a double-quote in it should be HTML-attribute-escaped.
    val tag = ImportTagResolver.resolveTag("/styles/a\"b.css", ViteManifest.empty, basePath = "")
    assert(!tag.contains("\"b.css"), s"Unescaped quote should not appear in tag: $tag")
    assert(tag.contains("&quot;") || tag.contains("&#34;") || tag.contains("%22"), s"Expected escaped quote in: $tag")
  }

  // ── ImportTagResolver.resolveTags ────────────────────────────────────

  test("resolveTags: returns empty string for empty list") {
    assertEquals(ImportTagResolver.resolveTags(Nil, manifest, ""), "")
  }

  test("resolveTags: multiple paths produce newline-joined tags") {
    val result = ImportTagResolver.resolveTags(
      List("/styles/global.css", "/styles/theme.css"),
      manifest,
      basePath = ""
    )
    val lines = result.split("\n").toList
    assertEquals(lines.length, 2)
    assert(lines.head.contains("global-abc123.css"))
    assert(lines(1).contains("theme-def456.css"))
  }

  test("resolveTags: unknown extensions are filtered out") {
    val result = ImportTagResolver.resolveTags(
      List("/styles/global.css", "/data/config.yaml"),
      manifest,
      basePath = ""
    )
    assert(!result.contains("yaml"), result)
    assert(result.contains("global-abc123.css"), result)
  }

  test("resolveTags: duplicate paths are deduplicated") {
    val result = ImportTagResolver.resolveTags(
      List("/styles/global.css", "/styles/global.css"),
      manifest,
      basePath = ""
    )
    assertEquals(result.split("\n").length, 1)
  }

  // ── CSP nonce ────────────────────────────────────────────────────────

  test("resolveTag: JS tag with nonce includes nonce attribute") {
    val tag = ImportTagResolver.resolveTag("/plugins/app.js", manifest, basePath = "", nonce = Some("abc123"))
    assertEquals(tag, """<script type="module" src="/assets/app-xyz789.js" nonce="abc123"></script>""")
  }

  test("resolveTag: CSS tag with nonce does NOT include nonce attribute") {
    val tag = ImportTagResolver.resolveTag("/styles/global.css", manifest, basePath = "", nonce = Some("abc123"))
    assertEquals(tag, """<link rel="stylesheet" href="/assets/global-abc123.css">""")
    assert(!tag.contains("nonce"), s"CSS link should not have nonce: $tag")
  }

  test("resolveTag: JS tag without nonce has no nonce attribute") {
    val tag = ImportTagResolver.resolveTag("/plugins/app.js", manifest, basePath = "", nonce = None)
    assertEquals(tag, """<script type="module" src="/assets/app-xyz789.js"></script>""")
    assert(!tag.contains("nonce"), s"Expected no nonce attribute: $tag")
  }

  test("resolveTags: nonce is applied to JS tags and not to CSS tags") {
    val result = ImportTagResolver.resolveTags(
      List("/styles/global.css", "/plugins/app.js"),
      manifest,
      basePath = "",
      nonce    = Some("xyz789")
    )
    val lines = result.split("\n").toList
    assertEquals(lines.length, 2)
    assert(!lines.head.contains("nonce"),        s"CSS link should not have nonce: ${ lines.head }")
    assert(lines(1).contains("""nonce="xyz789""""), s"JS script should have nonce: ${ lines(1) }")
  }

  // ── Import injection into result.head ────────────────────────────────

  test("import tags are prepended to existing head content") {
    val imports = List("/styles/global.css")
    val tags    = ImportTagResolver.resolveTags(imports, manifest, basePath = "")
    val existingHead = "<meta charset=\"UTF-8\">"
    val newHead      = if existingHead.isEmpty then tags else s"$tags\n$existingHead"
    assert(newHead.startsWith("""<link rel="stylesheet""""), newHead)
    assert(newHead.contains("<meta charset"), newHead)
    val linkIdx = newHead.indexOf("<link")
    val metaIdx = newHead.indexOf("<meta")
    assert(linkIdx < metaIdx, "import tag should appear before existing head content")
  }

  test("no imports: result.head is unchanged") {
    val result = RenderResult(body = "<div></div>", head = "<meta charset=\"UTF-8\">")
    val augmented =
      if result.imports.isEmpty then result
      else
        val tags    = ImportTagResolver.resolveTags(result.imports, manifest, "")
        val newHead = if result.head.isEmpty then tags else s"$tags\n${ result.head }"
        result.copy(head = newHead)
    assertEquals(augmented.head, "<meta charset=\"UTF-8\">")
  }
