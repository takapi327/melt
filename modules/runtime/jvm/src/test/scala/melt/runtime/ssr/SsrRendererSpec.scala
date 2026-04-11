/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.ssr

import munit.FunSuite

import melt.runtime.MeltWarnings

/** Phase A tests for [[SsrRenderer]].
  *
  * Covers:
  *   - body / head buffering and `result()` assembly
  *   - CSS deduplication
  *   - component tracking and `merge`
  *   - §12.2.1 recursion depth limit
  *   - §12.2.2 output size limit
  *   - spread attribute filtering (§12.1.2 + §12.1.4)
  */
class SsrRendererSpec extends FunSuite:

  test("push writes to the body buffer and is observable via result()") {
    val r = SsrRenderer()
    r.push("<div>")
    r.push("hello")
    r.push("</div>")
    val result = r.result()
    assertEquals(result.body, "<div>hello</div>")
  }

  test("head buffer is separate from body") {
    val r = SsrRenderer()
    r.push("<div>body</div>")
    r.head.push("<meta name=\"x\">")
    val result = r.result()
    assert(result.body.contains("<div>body</div>"))
    assert(result.head.contains("<meta name=\"x\">"))
  }

  test("head.title HTML-escapes its argument") {
    val r = SsrRenderer()
    r.head.title("<script>")
    val result = r.result()
    assert(result.head.contains("<title>&lt;script&gt;</title>"), result.head)
  }

  test("CSS deduplication — identical entries collapse") {
    val r = SsrRenderer()
    r.css.add("melt-a", ".a { color: red; }")
    r.css.add("melt-a", ".a { color: red; }") // duplicate
    r.css.add("melt-b", ".b { color: blue; }")
    val result = r.result()
    assertEquals(result.css.size, 2)
  }

  test("trackComponent records module IDs") {
    val r = SsrRenderer()
    r.trackComponent("counter")
    r.trackComponent("todo-list")
    assertEquals(r.result().components, Set("counter", "todo-list"))
  }

  test("merge propagates body, head, css, and components from children") {
    val r = SsrRenderer()
    r.push("<parent>")
    val child = RenderResult(
      body       = "<child/>",
      head       = "<meta name=\"c\">",
      css        = Set(CssEntry("melt-c", ".c{}")),
      components = Set("child-id")
    )
    r.merge(child)
    r.push("</parent>")
    val result = r.result()
    assertEquals(result.body, "<parent><child/></parent>")
    assert(result.head.contains("<meta name=\"c\">"))
    assert(result.css.contains(CssEntry("melt-c", ".c{}")))
    assert(result.components.contains("child-id"))
  }

  // ── §12.2.1 recursion depth limit ─────────────────────────────────────

  test("enterComponent within limit succeeds") {
    val r = SsrRenderer(SsrRenderer.Config(maxComponentDepth = 1000))
    (1 to 999).foreach(_ => r.enterComponent("ok"))
  }

  test("enterComponent beyond limit throws MeltRenderException") {
    val r = SsrRenderer(SsrRenderer.Config(maxComponentDepth = 3))
    r.enterComponent("A")
    r.enterComponent("B")
    r.enterComponent("C")
    val e = intercept[MeltRenderException] {
      r.enterComponent("D")
    }
    assert(e.getMessage.contains("3"), e.getMessage)
  }

  test("exitComponent decrements the counter") {
    val r = SsrRenderer(SsrRenderer.Config(maxComponentDepth = 2))
    r.enterComponent("A")
    r.enterComponent("B")
    r.exitComponent()
    r.enterComponent("C") // A=1, B exited, C=2 → OK
  }

  // ── §12.2.2 output size limit ──────────────────────────────────────────

  test("push within size limit succeeds") {
    val r = SsrRenderer(SsrRenderer.Config(maxOutputBytes = 1024))
    r.push("x" * 500) // 1000 bytes (UTF-16 approx)
  }

  test("push beyond size limit throws MeltRenderException") {
    val r = SsrRenderer(SsrRenderer.Config(maxOutputBytes = 1024))
    val e = intercept[MeltRenderException] {
      r.push("x" * 1000) // 2000 bytes
    }
    assert(e.getMessage.contains("1024"), e.getMessage)
  }

  test("body + head counted against the same limit") {
    val r = SsrRenderer(SsrRenderer.Config(maxOutputBytes = 100))
    r.push("a" * 20)      // 40 bytes
    r.head.push("b" * 20) // 40 bytes → total 80
    intercept[MeltRenderException] {
      r.push("c" * 20)    // would reach 120 — over limit
    }
  }

  test("duplicate CSS does not double-count size") {
    val r = SsrRenderer(SsrRenderer.Config(maxOutputBytes = 200))
    r.css.add("melt-a", "x" * 50) // added
    r.css.add("melt-a", "x" * 50) // duplicate → no extra tracking
  }

  // ── §12.1.2 spread attribute filtering ─────────────────────────────────

  test("spreadAttrs drops invalid keys and on* handlers") {
    MeltWarnings.mute()
    try
      val r = SsrRenderer()
      r.spreadAttrs("button",
        Map(
          "type"                      -> "button",
          "onclick"                   -> "alert(1)",
          """class" onmouseover="x""" -> "bad"
        )
      )
      val body = r.result().body
      assert(body.contains("""type="button""""), body)
      assert(!body.contains("onclick"), body)
      assert(!body.contains("onmouseover"), body)
    finally MeltWarnings.resetHandler()
  }

  test("spreadAttrs null / None values are dropped silently") {
    val r = SsrRenderer()
    r.spreadAttrs("div", Map("a" -> null, "b" -> None, "c" -> "ok"))
    val body = r.result().body
    assert(body.contains("""c="ok""""), body)
    assert(!body.contains("""a="""), body)
    assert(!body.contains("""b="""), body)
  }

  test("spreadAttrs uses Escape.url for URL attributes") {
    MeltWarnings.mute()
    try
      val r = SsrRenderer()
      r.spreadAttrs("a", Map("href" -> "javascript:alert(1)"))
      val body = r.result().body
      // Dangerous URL is blocked by Escape.url → attribute value is empty
      assert(!body.contains("javascript:"), body)
    finally MeltWarnings.resetHandler()
  }
