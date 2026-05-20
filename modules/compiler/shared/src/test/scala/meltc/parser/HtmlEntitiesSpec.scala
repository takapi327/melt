/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.parser

class HtmlEntitiesSpec extends munit.FunSuite:

  // ── Named entities ─────────────────────────────────────────────────────

  test("&amp; decodes to &") {
    assertEquals(HtmlEntities.decode("&amp;"), "&")
  }

  test("&lt; and &gt; decode to < and >") {
    assertEquals(HtmlEntities.decode("&lt;3 &gt;5"), "<3 >5")
  }

  test("&quot; decodes to double quote") {
    assertEquals(HtmlEntities.decode("&quot;hello&quot;"), "\"hello\"")
  }

  test("&apos; decodes to single quote") {
    assertEquals(HtmlEntities.decode("&apos;hello&apos;"), "'hello'")
  }

  test("&nbsp; decodes to non-breaking space") {
    assertEquals(HtmlEntities.decode("hello&nbsp;world"), "hello\u00a0world")
  }

  test("&lbrace; and &rbrace; decode to curly braces") {
    assertEquals(HtmlEntities.decode("&lbrace;x&rbrace;"), "{x}")
  }

  // ── Decimal numeric entities ────────────────────────────────────────────

  test("&#123; decodes to {") {
    assertEquals(HtmlEntities.decode("&#123;"), "{")
  }

  test("&#125; decodes to }") {
    assertEquals(HtmlEntities.decode("&#125;"), "}")
  }

  test("&#65; decodes to A") {
    assertEquals(HtmlEntities.decode("&#65;"), "A")
  }

  // ── Hexadecimal numeric entities ────────────────────────────────────────

  test("&#x7B; decodes to {") {
    assertEquals(HtmlEntities.decode("&#x7B;"), "{")
  }

  test("&#x41; decodes to A") {
    assertEquals(HtmlEntities.decode("&#x41;"), "A")
  }

  test("&#X41; (uppercase X) decodes to A") {
    assertEquals(HtmlEntities.decode("&#X41;"), "A")
  }

  // ── Mixed content ──────────────────────────────────────────────────────

  test("mixed entities and plain text") {
    assertEquals(HtmlEntities.decode("a &amp; b &lt; c"), "a & b < c")
  }

  test("multiple entities in a row") {
    assertEquals(HtmlEntities.decode("&lt;&gt;&amp;"), "<>&")
  }

  // ── No entities ────────────────────────────────────────────────────────

  test("text without entities is returned unchanged") {
    assertEquals(HtmlEntities.decode("Hello, World!"), "Hello, World!")
  }

  test("empty string") {
    assertEquals(HtmlEntities.decode(""), "")
  }

  // ── Unrecognised entities ──────────────────────────────────────────────

  test("unrecognised named entity is left as-is") {
    assertEquals(HtmlEntities.decode("&foo;"), "&foo;")
  }

  test("& not followed by ; is left as-is") {
    assertEquals(HtmlEntities.decode("a & b"), "a & b")
  }

  test("incomplete entity reference is left as-is") {
    assertEquals(HtmlEntities.decode("&amp"), "&amp")
  }

  // ── S-4: Codepoint validation ───────────────────────────────────────────

  test("surrogate codepoint &#xD800; is rejected (left as-is)") {
    assertEquals(HtmlEntities.decode("&#xD800;"), "&#xD800;")
  }

  test("surrogate codepoint &#xDFFF; is rejected (left as-is)") {
    assertEquals(HtmlEntities.decode("&#xDFFF;"), "&#xDFFF;")
  }

  test("surrogate decimal &#55296; is rejected (left as-is)") {
    // 55296 == 0xD800
    assertEquals(HtmlEntities.decode("&#55296;"), "&#55296;")
  }

  test("non-character &#xFFFE; is rejected (left as-is)") {
    assertEquals(HtmlEntities.decode("&#xFFFE;"), "&#xFFFE;")
  }

  test("non-character &#xFFFF; is rejected (left as-is)") {
    assertEquals(HtmlEntities.decode("&#xFFFF;"), "&#xFFFF;")
  }

  test("codepoint above Unicode max &#x110000; is rejected (left as-is)") {
    assertEquals(HtmlEntities.decode("&#x110000;"), "&#x110000;")
  }

  test("valid supplementary codepoint &#x1F600; decodes correctly") {
    // U+1F600 GRINNING FACE — requires a surrogate pair in UTF-16
    assertEquals(HtmlEntities.decode("&#x1F600;"), "\uD83D\uDE00")
  }

  test("last valid BMP codepoint &#xD7FF; decodes correctly") {
    assertEquals(HtmlEntities.decode("&#xD7FF;"), "\uD7FF")
  }

  test("first valid codepoint after surrogates &#xE000; decodes correctly") {
    assertEquals(HtmlEntities.decode("&#xE000;"), "\uE000")
  }
