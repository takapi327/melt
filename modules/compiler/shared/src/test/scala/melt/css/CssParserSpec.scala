/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.css

class CssParserSpec extends munit.FunSuite:

  test("simple style rule parses to StyleRule") {
    val nodes = CssParser.parse("h1 { color: red; }")
    assertEquals(nodes, List(CssNode.StyleRule("h1", List(CssNode.RawText("color: red;")))))
  }

  test("@media parses to AtRule with body") {
    val nodes = CssParser.parse("@media (max-width: 600px) { h1 { color: red; } }")
    assertEquals(
      nodes.head,
      CssNode.AtRule(
        "media",
        "(max-width: 600px)",
        Some(List(CssNode.StyleRule("h1", List(CssNode.RawText("color: red;")))))
      )
    )
  }

  test("@layer declaration (bodyless) parses to AtRule with None body") {
    val nodes = CssParser.parse("@layer base, components, utilities;")
    assertEquals(nodes, List(CssNode.AtRule("layer", "base, components, utilities", None)))
  }

  test("@import parses to AtRule with None body") {
    val nodes = CssParser.parse("""@import url("reset.css");""")
    assertEquals(nodes.head, CssNode.AtRule("import", """url("reset.css")""", None))
  }

  test("@charset parses to AtRule with None body") {
    val nodes = CssParser.parse("""@charset "UTF-8";""")
    assertEquals(nodes.head, CssNode.AtRule("charset", """"UTF-8"""", None))
  }

  test("CSS Nesting: nested style rule") {
    val nodes = CssParser.parse(".card { color: red; & .title { font-size: 1em; } }")
    val card  = nodes.head.asInstanceOf[CssNode.StyleRule]
    assert(card.body.exists { case _: CssNode.StyleRule => true; case _ => false })
  }

  test("CSS Nesting: nested @media inside style rule") {
    val nodes = CssParser.parse(".card { color: red; @media (max-width: 600px) { display: none; } }")
    val card  = nodes.head.asInstanceOf[CssNode.StyleRule]
    assert(card.body.exists { case _: CssNode.AtRule => true; case _ => false })
  }

  test("@keyframes is passthrough (body as RawText)") {
    val nodes = CssParser.parse("@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }")
    val kf    = nodes.head.asInstanceOf[CssNode.AtRule]
    assertEquals(kf.name, "keyframes")
    assert(kf.body.get.forall { case _: CssNode.RawText => true; case _ => false })
  }

  test("@font-face is passthrough (body as RawText)") {
    val nodes = CssParser.parse("""@font-face { font-family: "MyFont"; src: url("myfont.woff2"); }""")
    val ff    = nodes.head.asInstanceOf[CssNode.AtRule]
    assertEquals(ff.name, "font-face")
    assert(ff.body.get.forall { case _: CssNode.RawText => true; case _ => false })
  }

  test("comment is preserved as CssNode.Comment") {
    val nodes = CssParser.parse("/* heading */ h1 { color: red; }")
    assert(nodes.exists { case _: CssNode.Comment => true; case _ => false })
  }

  test("multiple rules parse to multiple nodes") {
    val nodes = CssParser.parse("h1 { color: red; }\np { color: blue; }")
    assertEquals(nodes.length, 2)
  }

  test("empty CSS returns empty list") {
    assertEquals(CssParser.parse(""), Nil)
    assertEquals(CssParser.parse("   "), Nil)
  }

  test("stray top-level } is consumed without error (malformed CSS recovery)") {
    val nodes = CssParser.parse("h1 { color: red; } } p { color: blue; }")
    assert(nodes.exists { case CssNode.StyleRule("h1", _) => true; case _ => false })
    assert(nodes.exists { case CssNode.StyleRule("p", _) => true; case _ => false })
  }

  test("unclosed block is handled without infinite loop (malformed CSS recovery)") {
    val nodes = CssParser.parse(".card { color red") // malformed CSS: no `;` or `}`
    // Verify no panic occurs; the exact structure of the result is implementation-defined
    assert(nodes.nonEmpty || nodes.isEmpty) // just verify no infinite loop
  }
