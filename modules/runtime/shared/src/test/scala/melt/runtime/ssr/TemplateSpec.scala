/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.ssr

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
    val t    = mk("%melt.body%|%melt.title%|%melt.lang%")
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

  // ── §12.3.9 title fallback to RenderResult.title ───────────────────────

  test("explicit title argument takes precedence over result.title") {
    val t      = mk("<title>%melt.title%</title>")
    val result = sampleResult.copy(title = Some("FromComponent"))
    val html   = t.render(result, title = "FromCaller")
    assertEquals(html, "<title>FromCaller</title>")
  }

  test("empty title argument falls back to result.title") {
    val t      = mk("<title>%melt.title%</title>")
    val result = sampleResult.copy(title = Some("&lt;ComponentTitle&gt;"))
    val html   = t.render(result, title = "")
    // result.title is already HTML-escaped when produced by SsrRenderer,
    // so Template should pass it through verbatim.
    assertEquals(html, "<title>&lt;ComponentTitle&gt;</title>")
  }

  test("empty title argument and no result.title yields empty <title>") {
    val t    = mk("<title>%melt.title%</title>")
    val html = t.render(sampleResult, title = "")
    assertEquals(html, "<title></title>")
  }

  test("explicit title is HTML-escaped") {
    val t    = mk("<title>%melt.title%</title>")
    val html = t.render(sampleResult, title = "<script>")
    assert(html.contains("&lt;script&gt;"), html)
  }

  // ── Phase C §C4: Hydration overload ────────────────────────────────────

  private def templateWithAll =
    mk("""<!doctype html>
         |<html lang="%melt.lang%">
         |<head>
         |%melt.head%
         |</head>
         |<body>
         |%melt.body%
         |</body>
         |</html>""".stripMargin)

  test("hydration overload injects modulepreload and stylesheet for tracked components") {
    val result = RenderResult(
      body       = "<main/>",
      head       = "<style>x{}</style>",
      css        = Set.empty,
      components = Set("counter")
    )
    val manifest = ViteManifest.fromString(
      """{
        |  "scalajs:counter.js": {
        |    "file":    "assets/counter.js",
        |    "css":     ["assets/counter.css"]
        |  }
        |}""".stripMargin
    )
    val html = templateWithAll.render(result, manifest)
    assert(html.contains("""<link rel="modulepreload" href="/assets/assets/counter.js">"""), html)
    assert(html.contains("""<link rel="stylesheet" href="/assets/assets/counter.css">"""), html)
    // Bootstrap script dynamically imports the chunk and calls hydrate().
    assert(
      html.contains("""import("/assets/assets/counter.js").then(m => m.hydrate())"""),
      html
    )
  }

  test("hydration overload uses shared chunks in dependency order (preload)") {
    val result = RenderResult(
      body       = "",
      head       = "",
      css        = Set.empty,
      components = Set("counter")
    )
    val manifest = ViteManifest.fromString(
      """{
        |  "scalajs:counter.js": {
        |    "file":    "assets/counter.js",
        |    "imports": ["_shared.js"]
        |  },
        |  "_shared.js": { "file": "assets/shared.js" }
        |}""".stripMargin
    )
    val html = templateWithAll.render(result, manifest)
    // `modulepreload` entries are emitted in dependency order: shared first.
    val preloadSharedIdx = html.indexOf("modulepreload\" href=\"/assets/assets/shared.js")
    val preloadOwnIdx    = html.indexOf("modulepreload\" href=\"/assets/assets/counter.js")
    assert(
      preloadSharedIdx >= 0 && preloadOwnIdx > preloadSharedIdx,
      s"preload order wrong\n$html"
    )
    // The bootstrap script must call the component's OWN entry chunk
    // (not the shared chunk) via dynamic import.
    assert(
      html.contains("""import("/assets/assets/counter.js").then(m => m.hydrate())"""),
      html
    )
  }

  test("hydration overload strips trailing slash from basePath") {
    val result = RenderResult(
      body       = "",
      head       = "",
      css        = Set.empty,
      components = Set("counter")
    )
    val manifest = ViteManifest.fromString(
      """{ "scalajs:counter.js": { "file": "assets/counter.js" } }"""
    )
    val html =
      templateWithAll.render(result, manifest, title = "", lang = "en", basePath = "/public/", vars = Map.empty)
    assert(html.contains("href=\"/public/assets/counter.js\""), html)
    assert(!html.contains("//assets"), html)
  }

  test("hydration overload with no tracked components leaves template alone") {
    val html = templateWithAll.render(sampleResult, ViteManifest.empty)
    assert(!html.contains("modulepreload"), html)
    assert(!html.contains("import("), html)
  }

  test("hydration overload honours the title fallback from result.title") {
    val t      = mk("<title>%melt.title%</title>")
    val result = sampleResult.copy(title = Some("FromComponent"))
    val html   = t.render(result, ViteManifest.empty)
    assertEquals(html, "<title>FromComponent</title>")
  }

  // ── §12.3.11 Props serialisation ───────────────────────────────────────

  test("hydration overload emits data-melt-props script tag for each component") {
    val result = RenderResult(
      body           = "<main/>",
      head           = "",
      css            = Set.empty,
      components     = Set("home"),
      hydrationProps = Map("home" -> """{"userName":"Melt","count":1}""")
    )
    val manifest = ViteManifest.fromString(
      """{ "scalajs:home.js": { "file": "assets/home.js" } }"""
    )
    val html = templateWithAll.render(result, manifest)
    assert(
      html.contains(
        """<script type="application/json" data-melt-props="home">{"userName":"Melt","count":1}</script>"""
      ),
      html
    )
    // The Props tag must appear before the hydration bootstrap import
    // so that the component's hydrate() function can read it
    // synchronously from the DOM.
    val propsIdx     = html.indexOf("data-melt-props")
    val bootstrapIdx = html.indexOf("m => m.hydrate()")
    assert(propsIdx >= 0 && bootstrapIdx > propsIdx, s"props tag must precede bootstrap\n$html")
  }

  test("hydration overload omits Props tag when none was tracked") {
    val result = RenderResult(
      body       = "",
      head       = "",
      components = Set("counter")
    )
    val manifest = ViteManifest.fromString(
      """{ "scalajs:counter.js": { "file": "assets/counter.js" } }"""
    )
    val html = templateWithAll.render(result, manifest)
    assert(!html.contains("data-melt-props"), html)
  }

  test("hydration overload only emits Props tags for tracked components") {
    // A component that appears in hydrationProps but NOT in
    // `components` must not be emitted — the canonical source of
    // truth for "which components should hydrate" is `components`.
    val result = RenderResult(
      body           = "",
      head           = "",
      components     = Set("home"),
      hydrationProps = Map(
        "home"  -> """{"a":1}""",
        "ghost" -> """{"b":2}"""
      )
    )
    val manifest = ViteManifest.fromString(
      """{ "scalajs:home.js": { "file": "assets/home.js" } }"""
    )
    val html = templateWithAll.render(result, manifest)
    assert(html.contains("""data-melt-props="home""""), html)
    assert(!html.contains("""data-melt-props="ghost""""), html)
  }
