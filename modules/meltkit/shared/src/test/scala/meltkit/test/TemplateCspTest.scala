/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import melt.runtime.render.{ CssEntry, RenderResult }

import meltkit.*

class TemplateCspTest extends munit.FunSuite:

  // ── Helpers ───────────────────────────────────────────────────────────────

  val nonce = "test-nonce-value"

  val manifest: ViteManifest = ViteManifest.fromEntries(
    Map("scalajs:app.js" -> ViteManifest.Entry(file = "app-hash.js", isEntry = true)),
    uriPrefix = "scalajs"
  )

  val resultWithComponent: RenderResult =
    RenderResult(body = "<div>content</div>", head = "", components = Set("app"))

  val resultEmpty: RenderResult =
    RenderResult(body = "<div>content</div>", head = "")

  // ── Bootstrap script nonce injection ──────────────────────────────────────

  test("render with nonce injects nonce attribute into bootstrap script"):
    val template = Template.fromString("<html><body>%melt.body%</body></html>")
    val html     = template.render(
      resultWithComponent,
      manifest,
      title    = "",
      lang     = "en",
      basePath = "/assets",
      vars     = Map.empty,
      nonce    = Some(nonce)
    )
    assert(html.contains(s"""nonce="$nonce""""), s"Expected nonce attribute in: $html")

  test("render without nonce produces no nonce attribute in bootstrap script"):
    val template = Template.fromString("<html><body>%melt.body%</body></html>")
    val html     = template.render(
      resultWithComponent,
      manifest,
      title    = "",
      lang     = "en",
      basePath = "/assets",
      vars     = Map.empty,
      nonce    = None
    )
    assert(!html.contains("nonce="), s"Expected no nonce attribute in: $html")

  test("render with nonce keeps script type=module intact"):
    val template = Template.fromString("<html><body>%melt.body%</body></html>")
    val html     = template.render(
      resultWithComponent,
      manifest,
      title    = "",
      lang     = "en",
      basePath = "/assets",
      vars     = Map.empty,
      nonce    = Some(nonce)
    )
    assert(
      html.contains(s"""<script type="module" nonce="$nonce">"""),
      s"Expected <script type=\"module\" nonce=\"...\"> in: $html"
    )

  test("render with no components produces no bootstrap script"):
    val template = Template.fromString("<html><body>%melt.body%</body></html>")
    val html     = template.render(
      resultEmpty,
      manifest,
      title    = "",
      lang     = "en",
      basePath = "/assets",
      vars     = Map.empty,
      nonce    = Some(nonce)
    )
    assert(!html.contains("<script type=\"module\""), s"Expected no bootstrap script when no components: $html")

  // ── %melt.nonce% placeholder ──────────────────────────────────────────────

  test("%melt.nonce% is replaced with nonce value"):
    val template = Template.fromString("""<script nonce="%melt.nonce%">window.x=1</script>""")
    val html     = template.render(
      resultEmpty,
      manifest,
      title    = "",
      lang     = "en",
      basePath = "/assets",
      vars     = Map.empty,
      nonce    = Some(nonce)
    )
    assert(html.contains(s"""nonce="$nonce""""), s"Expected nonce value in: $html")

  test("%melt.nonce% is replaced with empty string when nonce is None"):
    val template = Template.fromString("""<script nonce="%melt.nonce%">window.x=1</script>""")
    val html     = template.render(
      resultEmpty,
      manifest,
      title    = "",
      lang     = "en",
      basePath = "/assets",
      vars     = Map.empty,
      nonce    = None
    )
    assert(html.contains("""nonce="""""), s"Expected empty nonce attribute in: $html")
    assert(!html.contains("%melt.nonce%"), s"Expected placeholder to be replaced in: $html")

  test("vars with key 'nonce' does not override %melt.nonce% placeholder"):
    val template = Template.fromString("""<script nonce="%melt.nonce%">x</script>""")
    val html     = template.render(
      resultEmpty,
      manifest,
      title    = "",
      lang     = "en",
      basePath = "/assets",
      vars     = Map("nonce" -> "hacked"),
      nonce    = Some(nonce)
    )
    assert(html.contains(s"""nonce="$nonce""""), s"Expected real nonce, not 'hacked', in: $html")
    assert(!html.contains("hacked"), s"Expected vars nonce key to be ignored: $html")

  // ── Short overloads delegate correctly ───────────────────────────────────

  test("2-arg render delegates with nonce = None (no nonce attribute)"):
    val template = Template.fromString("<html><body>%melt.body%</body></html>")
    val html     = template.render(resultWithComponent, manifest)
    assert(!html.contains("nonce="), s"Expected no nonce in 2-arg render: $html")

  test("3-arg render delegates with nonce = None (no nonce attribute)"):
    val template = Template.fromString("<html><body>%melt.body%</body></html>")
    val html     = template.render(resultWithComponent, manifest, "My Title")
    assert(!html.contains("nonce="), s"Expected no nonce in 3-arg render: $html")

  test("6-arg render delegates with nonce = None (no nonce attribute)"):
    val template = Template.fromString("<html><body>%melt.body%</body></html>")
    val html     =
      template.render(resultWithComponent, manifest, title = "", lang = "en", basePath = "/assets", vars = Map.empty)
    assert(!html.contains("nonce="), s"Expected no nonce in 6-arg render: $html")

  // ── Component <style> tag nonce injection ─────────────────────────────────

  test("render with nonce injects nonce attribute into component <style> tags"):
    val template = Template.fromString("<html><head>%melt.head%</head><body>%melt.body%</body></html>")
    val result   = RenderResult(
      body = "<div>content</div>",
      head = "",
      css  = Set(CssEntry("scope-abc", "body { color: red }"))
    )
    val html =
      template.render(result, manifest, title = "", lang = "en", basePath = "", vars = Map.empty, nonce = Some(nonce))
    assert(html.contains(s"""<style id="scope-abc" nonce="$nonce">"""), s"Expected nonce on <style> tag: $html")

  test("render without nonce produces no nonce on component <style> tags"):
    val template = Template.fromString("<html><head>%melt.head%</head><body>%melt.body%</body></html>")
    val result   = RenderResult(
      body = "<div>content</div>",
      head = "",
      css  = Set(CssEntry("scope-abc", "body { color: red }"))
    )
    val html = template.render(result, manifest, title = "", lang = "en", basePath = "", vars = Map.empty, nonce = None)
    assert(html.contains("""<style id="scope-abc">"""), s"Expected plain <style> tag without nonce: $html")
    assert(!html.contains("nonce="), s"Expected no nonce attribute when nonce is None: $html")

  // ── Hardening: escape trusted-but-defended values (memo vuln-07 follow-up) ──

  test("render attribute-escapes a nonce so it cannot break out of the attribute"):
    val template = Template.fromString("<html><body>%melt.body%</body></html>")
    val evil     = """x" onload="alert(1)"""
    val html     = template.render(
      resultWithComponent,
      manifest,
      title    = "",
      lang     = "en",
      basePath = "/assets",
      vars     = Map.empty,
      nonce    = Some(evil)
    )
    assert(!html.contains("""onload="alert(1)""""), s"nonce broke out of the attribute: $html")
    assert(html.contains("&quot;"), s"Expected the nonce quote to be entity-escaped: $html")

  test("render JSON-encodes the hydration import() path"):
    val template = Template.fromString("<html><body>%melt.body%</body></html>")
    val html     = template.render(
      resultWithComponent,
      manifest,
      title    = "",
      lang     = "en",
      basePath = "/assets",
      vars     = Map.empty,
      nonce    = None
    )
    // The import argument must be a quoted JS string literal, not bare text.
    assert(html.contains("""import("/assets/app-hash.js")"""), s"Expected JSON-quoted import path: $html")
