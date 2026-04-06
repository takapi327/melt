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
