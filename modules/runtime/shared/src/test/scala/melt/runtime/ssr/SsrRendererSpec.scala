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
      r.push("c" * 20) // would reach 120 — over limit
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
      r.spreadAttrs(
        "button",
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

  // ── §12.1.4 spread attribute polish ──────────────────────────────────

  test("spreadAttrs unwraps Some(x)") {
    val r = SsrRenderer()
    r.spreadAttrs("div", Map("data-id" -> Some("42")))
    assert(r.result().body.contains("""data-id="42""""))
  }

  test("spreadAttrs drops function-valued entries with a warning") {
    MeltWarnings.mute()
    try
      val r = SsrRenderer()
      val fn: () => Unit = () => ()
      r.spreadAttrs("button", Map("onclick" -> "alert()", "cb" -> fn, "id" -> "ok"))
      val body = r.result().body
      assert(body.contains("""id="ok""""), body)
      assert(!body.contains("cb"), body)
      assert(!body.contains("onclick"), body)
    finally MeltWarnings.resetHandler()
  }

  // ── S-1: isFunction covers Function6-Function22 and FunctionXXL ──────

  test("spreadAttrs drops Function6-valued entry (S-1)") {
    MeltWarnings.mute()
    try
      val r  = SsrRenderer()
      val fn = (a: Int, b: Int, c: Int, d: Int, e: Int, f: Int) => a + b + c + d + e + f
      r.spreadAttrs("div", Map("cb" -> fn, "id" -> "ok"))
      val body = r.result().body
      assert(body.contains("""id="ok""""), body)
      assert(!body.contains("cb"), body)
    finally MeltWarnings.resetHandler()
  }

  test("spreadAttrs drops Function22-valued entry (S-1)") {
    MeltWarnings.mute()
    try
      val r = SsrRenderer()
      val fn: (
        Int,
        Int,
        Int,
        Int,
        Int,
        Int,
        Int,
        Int,
        Int,
        Int,
        Int,
        Int,
        Int,
        Int,
        Int,
        Int,
        Int,
        Int,
        Int,
        Int,
        Int,
        Int
      ) => Int =
        (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r2, s, t, u, v) => a + b
      r.spreadAttrs("div", Map("cb" -> fn, "id" -> "ok"))
      val body = r.result().body
      assert(body.contains("""id="ok""""), body)
      assert(!body.contains("cb"), body)
    finally MeltWarnings.resetHandler()
  }

  // ── S-1: isTuple covers Tuple and Named Tuple values ─────────────────

  test("spreadAttrs drops Tuple-valued entry with a warning (S-1)") {
    MeltWarnings.mute()
    try
      val r = SsrRenderer()
      r.spreadAttrs("div", Map("pair" -> ("Alice", 30), "id" -> "ok"))
      val body = r.result().body
      assert(body.contains("""id="ok""""), body)
      assert(!body.contains("pair"), body)
      assert(!body.contains("Alice"), body)
    finally MeltWarnings.resetHandler()
  }

  test("spreadAttrs Tuple drop warning contains attribute name (S-1)") {
    var warned = ""
    MeltWarnings.setHandler(msg => if msg.contains("Tuple") then warned = msg)
    try
      val r = SsrRenderer()
      r.spreadAttrs("div", Map("meta" -> ("Alice", 30)))
      assert(warned.contains("meta"), s"got: $warned")
    finally MeltWarnings.resetHandler()
  }

  test("spreadAttrs drops keys starting with $$ ($$slots reserved)") {
    val r = SsrRenderer()
    r.spreadAttrs("div", Map("$$slots" -> "default", "id" -> "x"))
    val body = r.result().body
    assert(body.contains("""id="x""""), body)
    assert(!body.contains("$$slots"), body)
  }

  test("spreadAttrs — true boolean value emits bare attribute") {
    val r = SsrRenderer()
    r.spreadAttrs("input", Map("disabled" -> true))
    val body = r.result().body
    // HTML boolean attribute convention: `disabled` with no `="..."`.
    assert(body.contains(" disabled"), body)
    assert(!body.contains("""disabled=""""), body)
  }

  test("spreadAttrs — false boolean value is dropped") {
    val r = SsrRenderer()
    r.spreadAttrs("input", Map("disabled" -> false, "id" -> "x"))
    val body = r.result().body
    assert(body.contains("""id="x""""), body)
    assert(!body.contains("disabled"), body)
  }

  // ── §12.3.9 Head dedup ────────────────────────────────────────────────

  test("head.title dedup — last call wins") {
    val r = SsrRenderer()
    r.head.title("First")
    r.head.title("Second")
    r.head.title("Third")
    val result = r.result()
    assertEquals(result.title, Some("Third"))
    assert(result.head.contains("<title>Third</title>"), result.head)
    assert(!result.head.contains("<title>First</title>"), result.head)
    assert(!result.head.contains("<title>Second</title>"), result.head)
  }

  test("head.title escapes the content") {
    val r = SsrRenderer()
    r.head.title("<script>alert(1)</script>")
    val result = r.result()
    assertEquals(result.title, Some("&lt;script&gt;alert(1)&lt;/script&gt;"))
    assert(result.head.contains("&lt;script&gt;alert(1)&lt;/script&gt;"), result.head)
  }

  test("head.meta dedup — last call wins per name") {
    val r = SsrRenderer()
    r.head.meta("description", "First description")
    r.head.meta("description", "Second description")
    r.head.meta("keywords", "a, b, c")
    val result = r.result()
    assertEquals(result.metaTags("description"), "Second description")
    assertEquals(result.metaTags("keywords"), "a, b, c")
    assert(result.head.contains("""<meta name="description" content="Second description">"""), result.head)
    assert(!result.head.contains("First description"), result.head)
  }

  test("head.meta escapes name and content attribute values") {
    val r = SsrRenderer()
    r.head.meta("description", """safe "then" dangerous""")
    val result = r.result()
    assertEquals(result.metaTags("description"), """safe &quot;then&quot; dangerous""")
  }

  test("merge — child's title overrides parent's") {
    val parent = SsrRenderer()
    parent.head.title("Parent")
    val childResult = RenderResult(
      body     = "",
      head     = "",
      title    = Some("Child"),
      metaTags = Map.empty
    )
    parent.merge(childResult)
    assertEquals(parent.result().title, Some("Child"))
  }

  test("merge — parent keeps title if child has none") {
    val parent = SsrRenderer()
    parent.head.title("Parent")
    val childResult = RenderResult(body = "", head = "")
    parent.merge(childResult)
    assertEquals(parent.result().title, Some("Parent"))
  }

  // ── §12.3.7 error boundary ─────────────────────────────────────────────

  test("boundary catches exceptions and pushes the fallback HTML") {
    val r = SsrRenderer()
    r.push("<div>")
    r.boundary(e => s"<p class=\"err\">${ e.getMessage }</p>") { inner =>
      inner.push("<span>hi</span>")
      sys.error("boom")
    }
    r.push("</div>")
    val body = r.result().body
    // Rendering up to the failure point is kept verbatim, then fallback
    // is appended in its place.
    assert(body.contains("<span>hi</span>"), body)
    assert(body.contains("<p class=\"err\">boom</p>"), body)
    assert(body.endsWith("</div>"), body)
  }

  test("boundary passes cleanly when body succeeds") {
    val r = SsrRenderer()
    r.boundary(_ => "<err/>") { inner =>
      inner.push("<ok/>")
    }
    assert(r.result().body.contains("<ok/>"))
    assert(!r.result().body.contains("<err/>"))
  }

  test("boundary swallows exceptions raised by the fallback itself") {
    val r = SsrRenderer()
    r.boundary(_ => throw new RuntimeException("double")) { inner =>
      inner.push("before")
      sys.error("boom")
    }
    val body = r.result().body
    assert(body.contains("before"), body)
    // Second failure: fallback threw — nothing else was appended.
    assert(!body.contains("double"), body)
  }

  test("boundary catches MeltRenderException from the output-size guard") {
    val r = SsrRenderer(SsrRenderer.Config(maxOutputBytes = 200))
    r.boundary(_ => "<trimmed/>") { inner =>
      inner.push("x" * 500) // would exceed the 200-byte limit
    }
    val body = r.result().body
    assert(body.contains("<trimmed/>"), body)
  }

  test("merge — meta tags from child override parent's on name collision") {
    val parent = SsrRenderer()
    parent.head.meta("description", "Parent desc")
    parent.head.meta("author", "Parent author")

    val childResult = RenderResult(
      body     = "",
      head     = "",
      title    = None,
      metaTags = Map("description" -> "Child desc") // override description only
    )
    parent.merge(childResult)

    val metas = parent.result().metaTags
    assertEquals(metas("description"), "Child desc")
    assertEquals(metas("author"), "Parent author")
  }
