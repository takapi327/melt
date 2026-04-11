/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.ssr

import java.io.FileNotFoundException
import java.nio.file.Files

import munit.FunSuite

/** Phase A tests for [[Template]]. */
class TemplateSpec extends FunSuite:

  private def mk(content: String): Template = Template.fromString(content)

  private val sampleResult = RenderResult(
    body       = "<main>hi</main>",
    head       = "<style>body{}</style>",
    css        = Set.empty,
    components = Set.empty
  )

  // ── Core placeholders ──────────────────────────────────────────────────

  test("head placeholder is substituted with RenderResult.head") {
    val t    = mk("<head>%melt.head%</head><body/>")
    val html = t.render(sampleResult)
    assert(html.contains("<head><style>body{}</style></head>"), html)
  }

  test("body placeholder is substituted with RenderResult.body") {
    val t    = mk("<body>%melt.body%</body>")
    val html = t.render(sampleResult)
    assert(html.contains("<body><main>hi</main></body>"), html)
  }

  test("title placeholder is HTML-escaped") {
    val t    = mk("<title>%melt.title%</title>")
    val html = t.render(sampleResult, title = "<script>alert(1)</script>")
    assert(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"), html)
    assert(!html.contains("<script>alert(1)"), html)
  }

  test("empty title substitutes an empty string") {
    val t    = mk("<title>%melt.title%</title>")
    val html = t.render(sampleResult, title = "")
    assertEquals(html, "<title></title>")
  }

  test("lang placeholder uses attribute escaping (default en)") {
    val t    = mk("""<html lang="%melt.lang%">""")
    val html = t.render(sampleResult)
    assertEquals(html, """<html lang="en">""")
  }

  test("lang placeholder is configurable") {
    val t    = mk("""<html lang="%melt.lang%">""")
    val html = t.render(sampleResult, lang = "ja")
    assertEquals(html, """<html lang="ja">""")
  }

  test("lang placeholder escapes attribute-special characters") {
    val t    = mk("""<html lang="%melt.lang%">""")
    val html = t.render(sampleResult, lang = "\"x\"")
    assert(!html.contains("""lang=""x""""), html)
    assert(html.contains("&quot;"), html)
  }

  // ── Custom variables ───────────────────────────────────────────────────

  test("custom vars are substituted and HTML-escaped") {
    val t    = mk("""<meta name="x" content="%melt.theme%">""")
    val html = t.render(sampleResult, vars = Map("theme" -> "dark<>&"))
    assert(html.contains("dark&lt;&gt;&amp;"), html)
  }

  test("multiple custom vars are all substituted") {
    val t    = mk("%melt.a% %melt.b% %melt.c%")
    val html = t.render(sampleResult, vars = Map("a" -> "1", "b" -> "2", "c" -> "3"))
    assertEquals(html, "1 2 3")
  }

  test("unused custom vars do not appear in output") {
    val t    = mk("%melt.body%")
    val html = t.render(sampleResult, vars = Map("unused" -> "x"))
    assertEquals(html, "<main>hi</main>")
  }

  test("missing custom var placeholder is left as-is") {
    val t    = mk("before %melt.missing% after")
    val html = t.render(sampleResult)
    // A real application should remove unused placeholders from the
    // template; this test just documents that the substitution is
    // best-effort and does not strip unknown placeholders.
    assert(html.contains("%melt.missing%"), html)
  }

  test("custom var cannot override reserved placeholders") {
    val t    = mk("%melt.head%")
    val html = t.render(sampleResult, vars = Map("head" -> "<script>alert(1)</script>"))
    // Reserved placeholders are always resolved first, and vars with
    // reserved names are ignored.
    assert(html.contains("<style>body{}</style>"), html)
    assert(!html.contains("<script>alert(1)"), html)
  }

  test("custom var cannot override reserved body / title / lang") {
    val t = mk("%melt.body%|%melt.title%|%melt.lang%")
    val html = t.render(
      sampleResult,
      title = "ok",
      lang  = "en",
      vars  = Map("body" -> "bad", "title" -> "bad", "lang" -> "bad")
    )
    assertEquals(html, "<main>hi</main>|ok|en")
  }

  // ── Full document example (sanity check) ───────────────────────────────

  test("end-to-end template substitution resembles SvelteKit app.html") {
    val tpl = """<!doctype html>
                |<html lang="%melt.lang%">
                |<head>
                |<meta charset="UTF-8">
                |<title>%melt.title%</title>
                |%melt.head%
                |</head>
                |<body>
                |%melt.body%
                |</body>
                |</html>""".stripMargin
    val t    = mk(tpl)
    val html = t.render(sampleResult, title = "Home", lang = "en")
    assert(html.contains("<!doctype html>"))
    assert(html.contains("""<html lang="en">"""))
    assert(html.contains("<title>Home</title>"))
    assert(html.contains("<style>body{}</style>"))
    assert(html.contains("<main>hi</main>"))
  }

  // ── Factory methods ────────────────────────────────────────────────────

  test("fromFile reads a UTF-8 template from disk") {
    val tmp = Files.createTempFile("melt-template", ".html")
    try
      Files.writeString(tmp, "<div>%melt.body% 日本語</div>")
      val t    = Template.fromFile(tmp)
      val html = t.render(sampleResult)
      assert(html.contains("<main>hi</main>"), html)
      assert(html.contains("日本語"), html)
    finally Files.deleteIfExists(tmp)
  }

  test("fromResource raises a clear error when the resource is missing") {
    val e = intercept[FileNotFoundException] {
      Template.fromResource("/this-does-not-exist.html")
    }
    assert(e.getMessage.contains("src/main/resources"), e.getMessage)
    assert(e.getMessage.contains("/this-does-not-exist.html"), e.getMessage)
  }

  test("fromResource normalises leading-slash-less paths") {
    val e = intercept[FileNotFoundException] {
      Template.fromResource("nope.html")
    }
    // The resolved path should have a leading slash in the error message.
    assert(e.getMessage.contains("/nope.html"), e.getMessage)
  }
