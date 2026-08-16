/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import munit.FunSuite

/** Discards warnings for the call under test. Scoped to the call, so suites
  * running in parallel cannot clobber each other the way a global handler swap
  * would.
  */
private val silent: MeltWarning => Unit = _ => ()

/** Phase A tests for [[Escape]] covering:
  *   - §12.3.1 null / None / Option handling
  *   - §12.1.1 URL protocol blocking and `TrustedUrl` bypass
  *   - Basic HTML / attribute escaping
  *
  * Tests live in `shared/src/test` so they run on both the JVM (where the
  * actual SSR usage happens) and Scala.js (to validate that the same
  * escaping primitives are available on both platforms).
  */
class EscapeSpec extends FunSuite:

  // ── HTML escaping ──────────────────────────────────────────────────────

  test("Escape.html escapes &, <, >") {
    assertEquals(
      Escape.html("<script>alert(1)</script>"),
      "&lt;script&gt;alert(1)&lt;/script&gt;"
    )
  }

  test("Escape.html preserves ASCII punctuation") {
    assertEquals(Escape.html("hello, world!"), "hello, world!")
  }

  test("Escape.attr also escapes double quotes") {
    assertEquals(
      Escape.attr("""value "with" quotes"""),
      "value &quot;with&quot; quotes"
    )
  }

  test("Escape.attr escapes single quotes") {
    assertEquals(Escape.attr("value 'with' quotes"), "value &#39;with&#39; quotes")
  }

  // ── Null safety (§12.3.1) ──────────────────────────────────────────────

  test("Escape.html(null) is empty string") {
    assertEquals(Escape.html(null), "")
  }

  test("Escape.attr(null) is empty string") {
    assertEquals(Escape.attr(null), "")
  }

  test("Escape.url(null) is empty string") {
    assertEquals(Escape.url(null, silent), "")
  }

  test("Escape.html(None) is empty string") {
    assertEquals(Escape.html(None), "")
  }

  test("Escape.html(Some(x)) escapes the inner value") {
    assertEquals(Escape.html(Some("<b>")), "&lt;b&gt;")
  }

  test("Escape.html(Some(Some(x))) recurses through multiple layers") {
    assertEquals(Escape.html(Some(Some("foo"))), "foo")
  }

  test("Escape.html(Some(null)) is empty string") {
    assertEquals(Escape.html(Some(null)), "")
  }

  test("Escape.html handles numeric values via toString") {
    assertEquals(Escape.html(42), "42")
    assertEquals(Escape.html(3.14), "3.14")
  }

  test("Escape.html handles booleans via toString") {
    assertEquals(Escape.html(true), "true")
    assertEquals(Escape.html(false), "false")
  }

  test("Escape.html tolerates toString returning null") {
    val nullyObj = new Object:
      override def toString: String = null
    assertEquals(Escape.html(nullyObj), "")
  }

  // ── URL protocol blocking (§12.1.1) ────────────────────────────────────

  test("Escape.url blocks javascript:") {
    assertEquals(Escape.url("javascript:alert(1)", silent), "")
  }

  test("Escape.url blocks JAVASCRIPT: (case-insensitive)") {
    assertEquals(Escape.url("JAVASCRIPT:alert(1)", silent), "")
  }

  test("Escape.url blocks whitespace-bypass javascript:") {
    assertEquals(Escape.url("   javascript:alert(1)", silent), "")
  }

  test("Escape.url blocks tab-bypass javascript:") {
    assertEquals(Escape.url("java\tscript:alert(1)", silent), "")
  }

  test("Escape.url blocks vbscript:") {
    assertEquals(Escape.url("vbscript:msgbox(1)", silent), "")
  }

  test("Escape.url blocks file:") {
    assertEquals(Escape.url("file:///etc/passwd", silent), "")
  }

  test("Escape.url blocks blob:") {
    assertEquals(Escape.url("blob:https://example.com/uuid", silent), "")
  }

  test("Escape.url blocks data:text/html") {
    assertEquals(
      Escape.url("data:text/html,<script>alert(1)</script>", silent),
      ""
    )
  }

  test("Escape.url blocks data:image/svg+xml") {
    assertEquals(
      Escape.url("data:image/svg+xml,<svg><script>alert(1)</script></svg>", silent),
      ""
    )
  }

  test("Escape.url allows data:image/png") {
    val url = "data:image/png;base64,iVBORw0KGgo="
    assertEquals(Escape.url(url, silent), Escape.attr(url))
  }

  test("Escape.url allows https://") {
    val url = "https://example.com/path?q=1"
    assertEquals(Escape.url(url, silent), Escape.attr(url))
  }

  test("Escape.url allows relative URLs") {
    assertEquals(Escape.url("/page", silent), "/page")
    assertEquals(Escape.url("./page", silent), "./page")
    assertEquals(Escape.url("#anchor", silent), "#anchor")
    assertEquals(Escape.url("?query=1", silent), "?query=1")
  }

  test("Escape.url allows mailto:") {
    val url = "mailto:test@example.com"
    assertEquals(Escape.url(url, silent), Escape.attr(url))
  }

  test("Escape.url forwards TrustedUrl without validation") {
    val tu     = TrustedUrl.unsafe("javascript:safeCode()")
    val result = Escape.url(tu, silent)
    assert(result.contains("javascript:safeCode()"), s"got: $result")
  }

  test("the sink receives a block notification") {
    val warned = List.newBuilder[MeltWarning]
    Escape.url("javascript:alert(1)", warned += _)
    val ws = warned.result()
    assertEquals(ws.map(_.kind), List(MeltWarningKind.BlockedUrl))
    assert(ws.head.value.exists(_.contains("javascript:")), s"got: $ws")
  }

  // ── CSS value escaping (§12.1.5) ───────────────────────────────────────

  test("Escape.cssValue passes safe values through") {
    assertEquals(Escape.cssValue("red", silent), "red")
    assertEquals(Escape.cssValue("10px", silent), "10px")
    assertEquals(Escape.cssValue("#ff3e00", silent), "#ff3e00")
    assertEquals(Escape.cssValue("rgba(0,0,0,.5)", silent), "rgba(0,0,0,.5)")
  }

  test("Escape.cssValue blocks url(javascript:...)") {
    assertEquals(Escape.cssValue("url(javascript:alert(1))", silent), "")
  }

  test("Escape.cssValue blocks expression(...)") {
    assertEquals(Escape.cssValue("expression(alert(1))", silent), "")
  }

  test("Escape.cssValue blocks @import") {
    assertEquals(Escape.cssValue("@import 'http://evil/'", silent), "")
  }

  test("Escape.cssValue blocks whitespace-obfuscated javascript:") {
    assertEquals(Escape.cssValue("url( java\tscript:alert(1) )", silent), "")
  }

  test("Escape.cssValue blocks vbscript:") {
    assertEquals(Escape.cssValue("url(vbscript:msgbox(1))", silent), "")
  }

  test("Escape.cssValue blocks url(file:...)") {
    assertEquals(Escape.cssValue("url(file:///etc/passwd)", silent), "")
  }

  test("Escape.cssValue does NOT block values containing 'file:' outside url()") {
    assertEquals(Escape.cssValue("profile file: path", silent), "profile file: path")
  }

  test("Escape.cssValue blocks url(blob:...)") {
    assertEquals(Escape.cssValue("url(blob:https://example.com/uuid)", silent), "")
  }

  test("Escape.cssValue blocks url('blob:...') single-quoted") {
    assertEquals(Escape.cssValue("url('blob:https://example.com/uuid')", silent), "")
  }

  test("Escape.cssValue blocks url(\"blob:...\") double-quoted") {
    assertEquals(Escape.cssValue("""url("blob:https://example.com/uuid")""", silent), "")
  }

  test("Escape.cssValue blocks url(data:text/html,...)") {
    assertEquals(Escape.cssValue("url(data:text/html,<h1>hi</h1>)", silent), "")
  }

  test("Escape.cssValue blocks url('data:text/html,...') single-quoted") {
    assertEquals(Escape.cssValue("url('data:text/html,<h1>hi</h1>')", silent), "")
  }

  test("Escape.cssValue blocks url('file:...') single-quoted") {
    assertEquals(Escape.cssValue("url('file:///etc/passwd')", silent), "")
  }

  test("Escape.cssValue blocks url(data:text/css,...)") {
    assertEquals(Escape.cssValue("url(data:text/css,body{color:red})", silent), "")
  }

  test("Escape.cssValue blocks url(data:image/svg+xml,...)") {
    assertEquals(Escape.cssValue("url(data:image/svg+xml,<svg><script>alert(1)</script></svg>)", silent), "")
  }

  test("Escape.cssValue blocks url(data:application/javascript,...)") {
    assertEquals(Escape.cssValue("url(data:application/javascript,alert(1))", silent), "")
  }

  test("Escape.cssValue does NOT block url(data:image/png;base64,...)") {
    val val1 = Escape.cssValue("url(data:image/png;base64,iVBORw0KGgo=)", silent)
    assert(val1.nonEmpty, "raster image data URI should be allowed")
  }

  test("Escape.cssValue does NOT block url(data:image/webp;base64,...)") {
    val val1 = Escape.cssValue("url(data:image/webp;base64,UklGRg==)", silent)
    assert(val1.nonEmpty, "raster image data URI should be allowed")
  }

  test("Escape.cssValue(null) is empty string") {
    assertEquals(Escape.cssValue(null, silent), "")
  }

  test("Escape.cssValue(None) is empty string") {
    assertEquals(Escape.cssValue(None, silent), "")
  }

  test("Escape.cssValue escapes HTML-special chars like other attr values") {
    // The output must be safe for use inside an HTML attribute value.
    assertEquals(Escape.cssValue("red\"><script>", silent), "red&quot;&gt;&lt;script&gt;")
  }

  // ── S-3: Escape.attr newline / tab escaping ────────────────────────────

  test("Escape.attr escapes newline to &#10;") {
    assertEquals(Escape.attr("line1\nline2"), "line1&#10;line2")
  }

  test("Escape.attr escapes carriage return to &#13;") {
    assertEquals(Escape.attr("line1\rline2"), "line1&#13;line2")
  }

  test("Escape.attr escapes tab to &#9;") {
    assertEquals(Escape.attr("col1\tcol2"), "col1&#9;col2")
  }

  test("Escape.attr escapes CRLF sequence") {
    assertEquals(Escape.attr("a\r\nb"), "a&#13;&#10;b")
  }

  test("Escape.cssValue also escapes newline (via escapeAttrInner)") {
    assertEquals(Escape.cssValue("10px\n", silent), "10px&#10;")
  }

  // ── urlForDom (client-side setAttribute counterpart of url) ────────────

  test("Escape.urlForDom blocks javascript:") {
    assertEquals(Escape.urlForDom("javascript:alert(1)", silent), "")
  }

  test("Escape.urlForDom blocks whitespace/tab-bypass javascript:") {
    assertEquals(Escape.urlForDom("  javascript:alert(1)", silent), "")
    assertEquals(Escape.urlForDom("java\tscript:alert(1)", silent), "")
  }

  test("Escape.urlForDom blocks data:image/svg+xml") {
    assertEquals(Escape.urlForDom("data:image/svg+xml,<svg onload=alert(1)>", silent), "")
  }

  test("Escape.urlForDom returns safe URLs verbatim (no entity escaping)") {
    // Unlike Escape.url, the DOM setAttribute path must NOT entity-escape:
    // ampersands stay literal so the browser receives the intended value.
    assertEquals(Escape.urlForDom("https://example.com/p?a=1&b=2", silent), "https://example.com/p?a=1&b=2")
    assertEquals(Escape.urlForDom("/page", silent), "/page")
    assertEquals(Escape.urlForDom("data:image/png;base64,iVBORw0KGgo=", silent), "data:image/png;base64,iVBORw0KGgo=")
  }

  test("Escape.urlForDom(null / None) is empty string") {
    assertEquals(Escape.urlForDom(null, silent), "")
    assertEquals(Escape.urlForDom(None, silent), "")
  }

  test("Escape.urlForDom bypasses validation for TrustedUrl") {
    assertEquals(Escape.urlForDom(TrustedUrl.unsafe("javascript:trusted()"), silent), "javascript:trusted()")
  }
