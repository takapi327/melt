/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import munit.FunSuite

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

  // ── Null safety (§12.3.1) ──────────────────────────────────────────────

  test("Escape.html(null) is empty string") {
    assertEquals(Escape.html(null), "")
  }

  test("Escape.attr(null) is empty string") {
    assertEquals(Escape.attr(null), "")
  }

  test("Escape.url(null) is empty string") {
    assertEquals(Escape.url(null), "")
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
    val nullyObj = new Object { override def toString: String = null }
    assertEquals(Escape.html(nullyObj), "")
  }

  // ── URL protocol blocking (§12.1.1) ────────────────────────────────────

  test("Escape.url blocks javascript:") {
    MeltWarnings.mute()
    try assertEquals(Escape.url("javascript:alert(1)"), "")
    finally MeltWarnings.resetHandler()
  }

  test("Escape.url blocks JAVASCRIPT: (case-insensitive)") {
    MeltWarnings.mute()
    try assertEquals(Escape.url("JAVASCRIPT:alert(1)"), "")
    finally MeltWarnings.resetHandler()
  }

  test("Escape.url blocks whitespace-bypass javascript:") {
    MeltWarnings.mute()
    try assertEquals(Escape.url("   javascript:alert(1)"), "")
    finally MeltWarnings.resetHandler()
  }

  test("Escape.url blocks tab-bypass javascript:") {
    MeltWarnings.mute()
    try assertEquals(Escape.url("java\tscript:alert(1)"), "")
    finally MeltWarnings.resetHandler()
  }

  test("Escape.url blocks vbscript:") {
    MeltWarnings.mute()
    try assertEquals(Escape.url("vbscript:msgbox(1)"), "")
    finally MeltWarnings.resetHandler()
  }

  test("Escape.url blocks file:") {
    MeltWarnings.mute()
    try assertEquals(Escape.url("file:///etc/passwd"), "")
    finally MeltWarnings.resetHandler()
  }

  test("Escape.url blocks data:text/html") {
    MeltWarnings.mute()
    try
      assertEquals(
        Escape.url("data:text/html,<script>alert(1)</script>"),
        ""
      )
    finally MeltWarnings.resetHandler()
  }

  test("Escape.url blocks data:image/svg+xml") {
    MeltWarnings.mute()
    try
      assertEquals(
        Escape.url("data:image/svg+xml,<svg><script>alert(1)</script></svg>"),
        ""
      )
    finally MeltWarnings.resetHandler()
  }

  test("Escape.url allows data:image/png") {
    val url = "data:image/png;base64,iVBORw0KGgo="
    assertEquals(Escape.url(url), Escape.attr(url))
  }

  test("Escape.url allows https://") {
    val url = "https://example.com/path?q=1"
    assertEquals(Escape.url(url), Escape.attr(url))
  }

  test("Escape.url allows relative URLs") {
    assertEquals(Escape.url("/page"), "/page")
    assertEquals(Escape.url("./page"), "./page")
    assertEquals(Escape.url("#anchor"), "#anchor")
    assertEquals(Escape.url("?query=1"), "?query=1")
  }

  test("Escape.url allows mailto:") {
    val url = "mailto:test@example.com"
    assertEquals(Escape.url(url), Escape.attr(url))
  }

  test("Escape.url forwards TrustedUrl without validation") {
    val tu     = TrustedUrl.unsafe("javascript:safeCode()")
    val result = Escape.url(tu)
    assert(result.contains("javascript:safeCode()"), s"got: $result")
  }

  test("MeltWarnings handler receives a block notification") {
    var warned = ""
    MeltWarnings.setHandler(msg => warned = msg)
    try
      Escape.url("javascript:alert(1)")
      assert(warned.contains("Blocked dangerous URL"), s"got: $warned")
    finally MeltWarnings.resetHandler()
  }

  // ── CSS value escaping (§12.1.5) ───────────────────────────────────────

  test("Escape.cssValue passes safe values through") {
    assertEquals(Escape.cssValue("red"), "red")
    assertEquals(Escape.cssValue("10px"), "10px")
    assertEquals(Escape.cssValue("#ff3e00"), "#ff3e00")
    assertEquals(Escape.cssValue("rgba(0,0,0,.5)"), "rgba(0,0,0,.5)")
  }

  test("Escape.cssValue blocks url(javascript:...)") {
    MeltWarnings.mute()
    try assertEquals(Escape.cssValue("url(javascript:alert(1))"), "")
    finally MeltWarnings.resetHandler()
  }

  test("Escape.cssValue blocks expression(...)") {
    MeltWarnings.mute()
    try assertEquals(Escape.cssValue("expression(alert(1))"), "")
    finally MeltWarnings.resetHandler()
  }

  test("Escape.cssValue blocks @import") {
    MeltWarnings.mute()
    try assertEquals(Escape.cssValue("@import 'http://evil/'"), "")
    finally MeltWarnings.resetHandler()
  }

  test("Escape.cssValue blocks whitespace-obfuscated javascript:") {
    MeltWarnings.mute()
    try assertEquals(Escape.cssValue("url( java\tscript:alert(1) )"), "")
    finally MeltWarnings.resetHandler()
  }

  test("Escape.cssValue blocks vbscript:") {
    MeltWarnings.mute()
    try assertEquals(Escape.cssValue("url(vbscript:msgbox(1))"), "")
    finally MeltWarnings.resetHandler()
  }

  test("Escape.cssValue(null) is empty string") {
    assertEquals(Escape.cssValue(null), "")
  }

  test("Escape.cssValue(None) is empty string") {
    assertEquals(Escape.cssValue(None), "")
  }

  test("Escape.cssValue escapes HTML-special chars like other attr values") {
    // The output must be safe for use inside an HTML attribute value.
    assertEquals(Escape.cssValue("red\"><script>"), "red&quot;&gt;&lt;script&gt;")
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
    assertEquals(Escape.cssValue("10px\n"), "10px&#10;")
  }
