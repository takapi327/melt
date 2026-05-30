/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import munit.FunSuite

class TrustedHtmlSpec extends FunSuite:

  // ── TrustedHtml.unsafe ────────────────────────────────────────────────

  test("unsafe wraps the string as-is") {
    val th = TrustedHtml.unsafe("<b>hello</b>")
    assertEquals(th.value, "<b>hello</b>")
  }

  // ── TrustedHtml.sanitize ──────────────────────────────────────────────

  test("sanitize applies the sanitizer function") {
    val th = TrustedHtml.sanitize("<script>alert(1)</script><p>safe</p>", _.replace("<script>alert(1)</script>", ""))
    assertEquals(th.value, "<p>safe</p>")
  }

  test("sanitize returns TrustedHtml with the transformed string") {
    val th = TrustedHtml.sanitize("hello", _.toUpperCase)
    assertEquals(th.value, "HELLO")
  }

  test("sanitize with identity sanitizer preserves the original string") {
    val html = "<p>content</p>"
    val th   = TrustedHtml.sanitize(html, identity)
    assertEquals(th.value, html)
  }

  test("sanitize propagates exceptions thrown by the sanitizer") {
    intercept[RuntimeException] {
      TrustedHtml.sanitize("bad input", _ => throw new RuntimeException("sanitizer rejected input"))
    }
  }

  test("sanitize with a sanitizer that always returns empty string") {
    val th = TrustedHtml.sanitize("<script>evil()</script>", _ => "")
    assertEquals(th.value, "")
  }
