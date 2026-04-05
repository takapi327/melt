/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.parser

class SectionSplitterSpec extends munit.FunSuite:

  // ── Helpers ───────────────────────────────────────────────────────────────

  private def split(src: String) = SectionSplitter.split(src)

  // ── Happy-path splitting ──────────────────────────────────────────────────

  test("splits all three sections from a complete .melt file") {
    val src =
      """<script lang="scala">
        |  val x = 1
        |</script>
        |<div>Hello</div>
        |<style>
        |  div { color: red; }
        |</style>""".stripMargin
    val sections = split(src).getOrElse(fail("unexpected error"))
    assertEquals(sections.rawScript.map(_.code), Some("val x = 1"))
    assert(sections.templateSource.contains("<div>Hello</div>"))
    assertEquals(sections.style, Some("div { color: red; }"))
  }

  test("template-only file (no script, no style)") {
    val src      = "<div>Hello</div>"
    val sections = split(src).getOrElse(fail("unexpected error"))
    assertEquals(sections.rawScript, None)
    assertEquals(sections.templateSource, "<div>Hello</div>")
    assertEquals(sections.style, None)
  }

  test("script and template, no style") {
    val src =
      """<script lang="scala">val n = 0</script>
        |<p>{n}</p>""".stripMargin
    val sections = split(src).getOrElse(fail("unexpected error"))
    assert(sections.rawScript.isDefined)
    assert(sections.templateSource.contains("<p>{n}</p>"))
    assertEquals(sections.style, None)
  }

  test("template and style, no script") {
    val src =
      """<p>hi</p>
        |<style>p { margin: 0; }</style>""".stripMargin
    val sections = split(src).getOrElse(fail("unexpected error"))
    assertEquals(sections.rawScript, None)
    assertEquals(sections.style, Some("p { margin: 0; }"))
  }

  // ── props attribute extraction ────────────────────────────────────────────

  test("extracts propsType from props=\"...\" attribute") {
    val src =
      """<script lang="scala" props="Props">
        |  case class Props(label: String)
        |</script>
        |<div></div>""".stripMargin
    val sections = split(src).getOrElse(fail("unexpected error"))
    assertEquals(sections.rawScript.flatMap(_.propsType), Some("Props"))
  }

  test("propsType is None when props attribute is absent") {
    val src      = """<script lang="scala">val x = 1</script><div></div>"""
    val sections = split(src).getOrElse(fail("unexpected error"))
    assertEquals(sections.rawScript.flatMap(_.propsType), None)
  }

  test("attribute order: lang before props") {
    val src      = """<script lang="scala" props="MyProps">val x = 1</script><p></p>"""
    val sections = split(src).getOrElse(fail("unexpected error"))
    assertEquals(sections.rawScript.flatMap(_.propsType), Some("MyProps"))
  }

  test("attribute order: props before lang") {
    val src      = """<script props="MyProps" lang="scala">val x = 1</script><p></p>"""
    val sections = split(src).getOrElse(fail("unexpected error"))
    assertEquals(sections.rawScript.flatMap(_.propsType), Some("MyProps"))
  }

  // ── Plain <script> tags are NOT treated as Scala ──────────────────────────

  test("plain <script> without lang=\"scala\" stays in template") {
    val src =
      """<script>console.log("hi")</script>
        |<div>Hello</div>""".stripMargin
    val sections = split(src).getOrElse(fail("unexpected error"))
    assertEquals(sections.rawScript, None)
    assert(sections.templateSource.contains("<script>"))
  }

  test("<script type=\"module\"> is not the Scala section") {
    val src      = """<script type="module">import x from './lib.js'</script><span></span>"""
    val sections = split(src).getOrElse(fail("unexpected error"))
    assertEquals(sections.rawScript, None)
  }

  // ── Quote style variations ────────────────────────────────────────────────

  test("lang='scala' with single quotes is recognised") {
    val src      = "<script lang='scala'>val x = 1</script><p></p>"
    val sections = split(src).getOrElse(fail("unexpected error"))
    assert(sections.rawScript.isDefined)
    assertEquals(sections.rawScript.map(_.code), Some("val x = 1"))
  }

  test("props with single quotes is extracted") {
    val src      = "<script lang='scala' props='MyProps'>val x = 1</script><p></p>"
    val sections = split(src).getOrElse(fail("unexpected error"))
    assertEquals(sections.rawScript.flatMap(_.propsType), Some("MyProps"))
  }

  // ── Empty and whitespace-only bodies ──────────────────────────────────────

  test("empty script body") {
    val src      = """<script lang="scala"></script><div></div>"""
    val sections = split(src).getOrElse(fail("unexpected error"))
    assertEquals(sections.rawScript.map(_.code), Some(""))
  }

  test("empty style body") {
    val src      = """<div></div><style></style>"""
    val sections = split(src).getOrElse(fail("unexpected error"))
    assertEquals(sections.style, Some(""))
  }

  test("empty file") {
    val sections = split("").getOrElse(fail("unexpected error"))
    assertEquals(sections.rawScript, None)
    assertEquals(sections.templateSource, "")
    assertEquals(sections.style, None)
  }

  // ── Multi-line script tag ─────────────────────────────────────────────────

  test("attributes spread across lines in script tag") {
    val src =
      """<script
        |  lang="scala"
        |  props="Props">
        |  val x = 1
        |</script>
        |<p></p>""".stripMargin
    val sections = split(src).getOrElse(fail("unexpected error"))
    assertEquals(sections.rawScript.flatMap(_.propsType), Some("Props"))
    assertEquals(sections.rawScript.map(_.code), Some("val x = 1"))
  }

  // ── Script content does not interfere with style section ──────────────────

  test("template still correct when script contains angle-bracket-like content") {
    // Scala comparison operators in script body should not confuse splitter
    val src =
      """<script lang="scala">
        |  val ok = x < 10 && y > 0
        |</script>
        |<p>ok</p>
        |<style>p { color: blue; }</style>""".stripMargin
    val sections = split(src).getOrElse(fail("unexpected error"))
    assert(sections.rawScript.isDefined)
    assert(sections.templateSource.contains("<p>ok</p>"))
    assertEquals(sections.style, Some("p { color: blue; }"))
  }

  // ── Error cases ───────────────────────────────────────────────────────────

  test("returns Left for unclosed <script lang=\"scala\"> tag") {
    val src = """<script lang="scala">val x = 1"""
    assert(split(src).isLeft)
  }

  test("returns Left for unclosed <style> tag") {
    val src = "<div></div><style>div { color: red; }"
    assert(split(src).isLeft)
  }

  // ── Multiple scripts ──────────────────────────────────────────────────────

  test("only the first <script lang=\"scala\"> is used when there are two") {
    // Unusual but defensive: second script block should be left in template
    val src =
      """<script lang="scala">val a = 1</script>
        |<div></div>
        |<script lang="scala">val b = 2</script>""".stripMargin
    val sections = split(src).getOrElse(fail("unexpected error"))
    assertEquals(sections.rawScript.map(_.code), Some("val a = 1"))
    // Second script block ends up in template source
    assert(sections.templateSource.contains("val b = 2"))
  }

  // ── Style with attributes ─────────────────────────────────────────────────

  test("<style> with an extra attribute stays in template (not extracted)") {
    // <style scoped> is non-standard; the splitter only matches plain <style>
    val src      = "<p></p><style scoped>div { color: red; }</style>"
    val sections = split(src).getOrElse(fail("unexpected error"))
    assertEquals(sections.style, None)
    assert(sections.templateSource.contains("<style scoped>"))
  }

  // ── Multiline CSS in style section ────────────────────────────────────────

  test("multiline CSS is preserved as-is") {
    val src =
      """<p></p>
        |<style>
        |  .counter {
        |    text-align: center;
        |    padding: 2rem;
        |  }
        |  h1 { color: #ff3e00; }
        |</style>""".stripMargin
    val sections = split(src).getOrElse(fail("unexpected error"))
    val css      = sections.style.getOrElse(fail("style missing"))
    assert(css.contains(".counter"))
    assert(css.contains("text-align: center;"))
    assert(css.contains("h1 { color: #ff3e00; }"))
  }

  // ── Template content integrity ────────────────────────────────────────────

  test("template source does not contain script or style tags after splitting") {
    val src =
      """<script lang="scala">val x = 1</script>
        |<main><p>{x}</p></main>
        |<style>p { margin: 0; }</style>""".stripMargin
    val sections = split(src).getOrElse(fail("unexpected error"))
    assert(!sections.templateSource.contains("<script"))
    assert(!sections.templateSource.contains("<style"))
    assert(sections.templateSource.contains("<main>"))
  }
