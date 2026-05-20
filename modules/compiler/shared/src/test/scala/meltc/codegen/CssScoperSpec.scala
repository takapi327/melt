/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.codegen

class CssScoperSpec extends munit.FunSuite:

  private val sid = "melt-abc123"

  private def scope(css: String): String = CssScoper.scope(css, sid)

  // ── Simple selectors ────────────────────────────────────────────────────

  test("type selector") {
    val result = scope("h1 { color: red; }")
    assert(result.contains("h1.melt-abc123"), result)
    assert(result.contains("color: red;"), result)
  }

  test("class selector") {
    val result = scope(".foo { margin: 0; }")
    assert(result.contains(".foo.melt-abc123"), result)
  }

  test("id selector") {
    val result = scope("#bar { padding: 1em; }")
    assert(result.contains("#bar.melt-abc123"), result)
  }

  test("universal selector") {
    val result = scope("* { box-sizing: border-box; }")
    assert(result.contains("*.melt-abc123"), result)
  }

  // ── Compound selectors with combinators ────────────────────────────────

  test("descendant combinator scopes last segment") {
    val result = scope("div p { color: green; }")
    assert(result.contains("p.melt-abc123"), result)
    // First segment should NOT be scoped
    assert(!result.contains("div.melt-abc123"), result)
  }

  test("child combinator scopes last segment") {
    val result = scope("ul > li { list-style: none; }")
    assert(result.contains("li.melt-abc123"), result)
    assert(!result.contains("ul.melt-abc123"), result)
  }

  test("adjacent sibling combinator") {
    val result = scope("h1 + p { margin-top: 0; }")
    assert(result.contains("p.melt-abc123"), result)
    assert(!result.contains("h1.melt-abc123"), result)
  }

  test("general sibling combinator") {
    val result = scope("h1 ~ p { color: gray; }")
    assert(result.contains("p.melt-abc123"), result)
  }

  // ── Pseudo-elements ────────────────────────────────────────────────────

  test("::before pseudo-element") {
    val result = scope("p::before { content: ''; }")
    assert(result.contains("p.melt-abc123::before"), result)
  }

  test("::after pseudo-element") {
    val result = scope("p::after { content: ''; }")
    assert(result.contains("p.melt-abc123::after"), result)
  }

  test("::placeholder pseudo-element") {
    val result = scope("input::placeholder { color: gray; }")
    assert(result.contains("input.melt-abc123::placeholder"), result)
  }

  // ── Pseudo-classes ─────────────────────────────────────────────────────

  test(":hover pseudo-class") {
    val result = scope("a:hover { color: blue; }")
    assert(result.contains("a.melt-abc123:hover"), result)
  }

  test(":focus pseudo-class") {
    val result = scope("input:focus { outline: none; }")
    assert(result.contains("input.melt-abc123:focus"), result)
  }

  test(":nth-child pseudo-class") {
    val result = scope("li:nth-child(2n) { background: #eee; }")
    assert(result.contains("li.melt-abc123:nth-child(2n)"), result)
  }

  // ── :global() ─────────────────────────────────────────────────────────

  test(":global() passthrough") {
    val result = scope(":global(.external) { color: red; }")
    assert(result.contains(".external {") || result.contains(".external{"), result)
    assert(!result.contains("melt-abc123"), result)
  }

  // ── Group selectors (comma-separated) ──────────────────────────────────

  test("group selector scopes each part") {
    val result = scope("h1, h2, h3 { font-weight: bold; }")
    assert(result.contains("h1.melt-abc123"), result)
    assert(result.contains("h2.melt-abc123"), result)
    assert(result.contains("h3.melt-abc123"), result)
  }

  // ── @media query ──────────────────────────────────────────────────────

  test("@media — selectors inside are scoped") {
    val result = scope("@media (max-width: 600px) { .nav { display: none; } }")
    assert(result.contains("@media (max-width: 600px)"), result)
    assert(result.contains(".nav.melt-abc123"), result)
  }

  // ── @keyframes ────────────────────────────────────────────────────────

  test("@keyframes content is NOT scoped") {
    val result = scope("@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }")
    assert(result.contains("@keyframes fadeIn"), result)
    assert(!result.contains("from.melt-abc123"), result)
    assert(!result.contains("to.melt-abc123"), result)
  }

  // ── Multiple rules ────────────────────────────────────────────────────

  test("multiple rules are all scoped") {
    val css    = "h1 { color: red; }\np { color: blue; }"
    val result = scope(css)
    assert(result.contains("h1.melt-abc123"), result)
    assert(result.contains("p.melt-abc123"), result)
  }

  // ── CSS comments ─────────────────────────────────────────────────────

  test("CSS comments are preserved") {
    val result = scope("/* heading */ h1 { color: red; }")
    assert(result.contains("/* heading */"), result)
    assert(result.contains("h1.melt-abc123"), result)
  }

  // ── Attribute selectors ──────────────────────────────────────────────

  test("attribute selector") {
    val result = scope("""input[type="text"] { border: 1px solid; }""")
    assert(result.contains("""input[type="text"].melt-abc123"""), result)
  }

  // ── Empty CSS ────────────────────────────────────────────────────────

  test("empty CSS returns empty string") {
    assertEquals(scope(""), "")
    assertEquals(scope("   "), "")
  }

  // ── Real-world hello-world CSS ────────────────────────────────────────

  // ── @supports ────────────────────────────────────────────────────────

  test("@supports — selectors inside are scoped") {
    val result = scope("@supports (display: grid) { .container { display: grid; } }")
    assert(result.contains("@supports (display: grid)"), result)
    assert(result.contains(".container.melt-abc123"), result)
  }

  // ── Nested at-rules ──────────────────────────────────────────────────

  test("@media nested inside @supports — selectors are scoped") {
    val css    = "@supports (display: grid) { @media (min-width: 600px) { .nav { display: flex; } } }"
    val result = scope(css)
    assert(result.contains("@supports (display: grid)"), result)
    assert(result.contains("@media (min-width: 600px)"), result)
    assert(result.contains(".nav.melt-abc123"), result)
  }

  // ── Real-world hello-world CSS ────────────────────────────────────────

  // ── CSS custom properties transparency ──────────────────────────────

  test("CSS custom property values (var()) are not mangled by scoping") {
    val result = scope(".btn { background: var(--btn-bg, blue); color: var(--text-color); }")
    assert(result.contains("var(--btn-bg, blue)"), result)
    assert(result.contains("var(--text-color)"), result)
    assert(result.contains(".btn.melt-abc123"), result)
  }

  test("CSS custom property declarations are preserved unchanged") {
    val result = scope(":root { --btn-bg: #007bff; --text-color: #333; }")
    assert(result.contains("--btn-bg: #007bff"), result)
    assert(result.contains("--text-color: #333"), result)
  }

  // ── Real-world hello-world CSS ────────────────────────────────────────

  test("hello-world CSS is scoped correctly") {
    val css    = "h1 { color: #ff3e00; }\np  { color: #555; }"
    val result = scope(css)
    assert(result.contains("h1.melt-abc123"), result)
    assert(result.contains("p.melt-abc123"), result)
    assert(result.contains("color: #ff3e00;"), result)
    assert(result.contains("color: #555;"), result)
  }
