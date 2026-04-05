/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.parser

import meltc.ast.{ Attr, MeltFile, ScriptSection, StyleSection, TemplateNode }

class MeltParserSpec extends munit.FunSuite:

  private def parse(src: String) = MeltParser.parse(src)

  // ── Counter.melt (Appendix A) — Phase 2 acceptance test ───────────────────

  test("parses Counter.melt from Appendix A correctly") {
    val src =
      """<script lang="scala" props="Props">
        |  case class Props(label: String, count: Int = 0)
        |
        |  val internal = Var(props.count)
        |  val doubled = internal.map(_ * 2)
        |  def increment(): Unit = internal += 1
        |
        |  def badge(text: String): Html = {
        |    <span class="badge">{text}</span>
        |  }
        |</script>
        |
        |<div class="counter">
        |  <h1>{props.label}</h1>
        |  {badge(internal.now().toString)}
        |  <button onclick={increment}>+1</button>
        |  <p>Doubled: {doubled}</p>
        |</div>
        |
        |<style>
        |.counter { text-align: center; }
        |</style>""".stripMargin

    val meltFile = parse(src).getOrElse(fail("unexpected parse error"))

    // ── Script section ────────────────────────────────────────────────────
    val script = meltFile.script.getOrElse(fail("script section missing"))
    assertEquals(script.propsType, Some("Props"))
    assert(script.code.contains("case class Props(label: String, count: Int = 0)"))
    assert(script.code.contains("val internal = Var(props.count)"))

    // ── Template ──────────────────────────────────────────────────────────
    assertEquals(meltFile.template.size, 1)
    val div = meltFile.template.head.asInstanceOf[TemplateNode.Element]
    assertEquals(div.tag, "div")
    assertEquals(div.attrs, List(Attr.Static("class", "counter")))
    assertEquals(div.children.size, 4)

    // ── Style section ─────────────────────────────────────────────────────
    val style = meltFile.style.getOrElse(fail("style section missing"))
    assertEquals(style.css, ".counter { text-align: center; }")
  }

  // ── Minimal files ─────────────────────────────────────────────────────────

  test("template-only file") {
    val meltFile = parse("<p>Hello</p>").getOrElse(fail("unexpected error"))
    assertEquals(meltFile.script, None)
    assertEquals(meltFile.template, List(TemplateNode.Element("p", Nil, List(TemplateNode.Text("Hello")))))
    assertEquals(meltFile.style, None)
  }

  test("empty file produces empty MeltFile") {
    val meltFile = parse("").getOrElse(fail("unexpected error"))
    assertEquals(meltFile, MeltFile(None, Nil, None))
  }

  test("script-only file") {
    val src      = """<script lang="scala">val x = 42</script>"""
    val meltFile = parse(src).getOrElse(fail("unexpected error"))
    assertEquals(meltFile.script.map(_.code), Some("val x = 42"))
    assertEquals(meltFile.template, Nil)
    assertEquals(meltFile.style, None)
  }

  // ── Nested braces in template ─────────────────────────────────────────────

  test("template expression with nested braces is correctly extracted") {
    val src =
      """<script lang="scala">val m = Map(1 -> "a")</script>
        |<p>{m.getOrElse(1, "?")}</p>""".stripMargin
    val meltFile = parse(src).getOrElse(fail("unexpected error"))
    val p        = meltFile.template.head.asInstanceOf[TemplateNode.Element]
    assertEquals(p.children, List(TemplateNode.Expression("""m.getOrElse(1, "?")""")))
  }

  // ── Component in template ─────────────────────────────────────────────────

  test("component reference in template is parsed as Component node") {
    val src =
      """<script lang="scala">val t = "Hi"</script>
        |<Card title={t} />""".stripMargin
    val meltFile = parse(src).getOrElse(fail("unexpected error"))
    assertEquals(meltFile.template.size, 1)
    val card = meltFile.template.head.asInstanceOf[TemplateNode.Component]
    assertEquals(card.name, "Card")
    assertEquals(card.attrs, List(Attr.Dynamic("title", "t")))
  }

  // ── Multiple top-level template nodes ─────────────────────────────────────

  test("multiple top-level template elements") {
    val src =
      """<script lang="scala">val x = 1</script>
        |<h1>Title</h1>
        |<p>Body</p>""".stripMargin
    val meltFile = parse(src).getOrElse(fail("unexpected error"))
    assertEquals(meltFile.template.size, 2)
    assertEquals(meltFile.template(0).asInstanceOf[TemplateNode.Element].tag, "h1")
    assertEquals(meltFile.template(1).asInstanceOf[TemplateNode.Element].tag, "p")
  }

  // ── lang='scala' single-quote form ────────────────────────────────────────

  test("lang='scala' single-quote variant is accepted") {
    val src      = "<script lang='scala'>val n = 42</script><p>{n}</p>"
    val meltFile = parse(src).getOrElse(fail("unexpected error"))
    assertEquals(meltFile.script.map(_.code), Some("val n = 42"))
  }

  // ── Error cases ───────────────────────────────────────────────────────────

  test("returns Left for unclosed <script lang=\"scala\"> tag") {
    assert(parse("""<script lang="scala">val x = 1""").isLeft)
  }

  test("returns Left for unclosed <style> tag") {
    assert(parse("<p></p><style>div { color: red; }").isLeft)
  }
