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

  // ── Import statements ─────────────────────────────────────────────────────

  test("script section preserves a plain import statement") {
    val src =
      """<script lang="scala">
        |  import scala.math.sqrt
        |  val x = sqrt(4.0)
        |</script>
        |<p>{x}</p>""".stripMargin
    val meltFile = parse(src).getOrElse(fail("unexpected error"))
    val code     = meltFile.script.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("import scala.math.sqrt"))
    assert(code.contains("val x = sqrt(4.0)"))
  }

  test("grouped import with curly braces is preserved verbatim") {
    // import scala.{List, Map} must not be confused with template expressions
    val src =
      """<script lang="scala">
        |  import scala.collection.mutable.{ArrayBuffer, ListBuffer}
        |</script>
        |<p></p>""".stripMargin
    val meltFile = parse(src).getOrElse(fail("unexpected error"))
    val code     = meltFile.script.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("import scala.collection.mutable.{ArrayBuffer, ListBuffer}"))
  }

  test("Scala 3 import rename with 'as' keyword is preserved verbatim") {
    val src =
      """<script lang="scala">
        |  import scala.collection.mutable.{Map as MMap}
        |  val m = MMap.empty[String, Int]
        |</script>
        |<p></p>""".stripMargin
    val meltFile = parse(src).getOrElse(fail("unexpected error"))
    val code     = meltFile.script.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("import scala.collection.mutable.{Map as MMap}"))
  }

  test("wildcard import is preserved verbatim") {
    val src =
      """<script lang="scala">
        |  import scala.math.*
        |</script>
        |<p></p>""".stripMargin
    val meltFile = parse(src).getOrElse(fail("unexpected error"))
    val code     = meltFile.script.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("import scala.math.*"))
  }

  test("multiple imports of different styles are all preserved") {
    val src =
      """<script lang="scala">
        |  import scala.math.*
        |  import scala.collection.mutable.{Map as MMap, Set as MSet}
        |  import java.time.{Instant, ZoneId}
        |  val now = Instant.now()
        |</script>
        |<div>{now.toString}</div>""".stripMargin
    val meltFile = parse(src).getOrElse(fail("unexpected error"))
    val code     = meltFile.script.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("import scala.math.*"))
    assert(code.contains("import scala.collection.mutable.{Map as MMap, Set as MSet}"))
    assert(code.contains("import java.time.{Instant, ZoneId}"))
    // Template is also correctly parsed
    assertEquals(meltFile.template.size, 1)
    assertEquals(meltFile.template.head.asInstanceOf[TemplateNode.Element].tag, "div")
  }

  // ── Scala language constructs in script section ───────────────────────────

  test("enum and case class definitions in script are preserved") {
    val src =
      """<script lang="scala">
        |  enum Status:
        |    case Active, Inactive
        |  case class Item(id: Int, status: Status)
        |</script>
        |<p></p>""".stripMargin
    val meltFile = parse(src).getOrElse(fail("unexpected error"))
    val code     = meltFile.script.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("enum Status:"))
    assert(code.contains("case Active, Inactive"))
    assert(code.contains("case class Item(id: Int, status: Status)"))
  }

  test("given/using context parameters in script are preserved") {
    val src =
      """<script lang="scala">
        |  given Ordering[Int] = Ordering.Int
        |  def sorted[A](xs: List[A])(using ord: Ordering[A]): List[A] = xs.sorted
        |</script>
        |<p></p>""".stripMargin
    val meltFile = parse(src).getOrElse(fail("unexpected error"))
    val code     = meltFile.script.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("given Ordering[Int]"))
    assert(code.contains("using ord: Ordering[A]"))
  }

  test("extension method in script is preserved") {
    val src =
      """<script lang="scala">
        |  extension (s: String)
        |    def shout: String = s.toUpperCase + "!"
        |  val msg = "hello".shout
        |</script>
        |<p>{msg}</p>""".stripMargin
    val meltFile = parse(src).getOrElse(fail("unexpected error"))
    val code     = meltFile.script.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("extension (s: String)"))
    assert(code.contains("""val msg = "hello".shout"""))
    // Template still correctly parsed
    val p = meltFile.template.head.asInstanceOf[TemplateNode.Element]
    assertEquals(p.children, List(TemplateNode.Expression("msg")))
  }

  test("generic methods and type aliases in script are preserved") {
    val src =
      """<script lang="scala">
        |  type Id[A] = A
        |  def wrap[A](x: A): List[A] = List(x)
        |</script>
        |<p></p>""".stripMargin
    val meltFile = parse(src).getOrElse(fail("unexpected error"))
    val code     = meltFile.script.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("type Id[A] = A"))
    assert(code.contains("def wrap[A](x: A): List[A] = List(x)"))
  }

  test("pattern matching in script is preserved") {
    val src =
      """<script lang="scala">
        |  val label = count match
        |    case 0 => "none"
        |    case 1 => "one"
        |    case n => s"$n items"
        |</script>
        |<p>{label}</p>""".stripMargin
    val meltFile = parse(src).getOrElse(fail("unexpected error"))
    val code     = meltFile.script.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("count match"))
    assert(code.contains("""case 0 => "none""""))
    assert(code.contains("""case n => s"$n items""""))
  }

  test("comparison operators < and > in script do not affect template parsing") {
    val src =
      """<script lang="scala">
        |  val ok = x > 0 && x < 100
        |</script>
        |<p>{ok}</p>""".stripMargin
    val meltFile = parse(src).getOrElse(fail("unexpected error"))
    val code     = meltFile.script.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("x > 0 && x < 100"))
    assertEquals(meltFile.template.size, 1)
  }

  test("HTML-like string in script does not confuse section splitting") {
    val src =
      """<script lang="scala">
        |  val tag = "<div>hello</div>"
        |</script>
        |<p>{tag}</p>""".stripMargin
    val meltFile = parse(src).getOrElse(fail("unexpected error"))
    val code     = meltFile.script.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("""val tag = "<div>hello</div>""""))
    assertEquals(meltFile.template.size, 1)
  }

  // ── HTML-returning functions in script section ────────────────────────────

  test("inline HTML-returning function in script is preserved and template is correctly parsed") {
    val src =
      """<script lang="scala">
        |  def badge(text: String): Html = <span class="badge">{text}</span>
        |</script>
        |<div>{badge("new")}</div>""".stripMargin
    val meltFile = parse(src).getOrElse(fail("unexpected error"))
    val code     = meltFile.script.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("""def badge(text: String): Html = <span class="badge">{text}</span>"""))
    // Template: <div>{badge("new")}</div>
    val div = meltFile.template.head.asInstanceOf[TemplateNode.Element]
    assertEquals(div.tag, "div")
    assertEquals(div.children, List(TemplateNode.Expression("""badge("new")""")))
  }

  test("multi-line HTML-returning function in script is preserved verbatim") {
    val src =
      """<script lang="scala">
        |  def card(title: String): Html = {
        |    <div class="card">
        |      <h2>{title}</h2>
        |    </div>
        |  }
        |</script>
        |<main>{card("Hello")}</main>""".stripMargin
    val meltFile = parse(src).getOrElse(fail("unexpected error"))
    val code     = meltFile.script.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("""<div class="card">"""))
    assert(code.contains("<h2>{title}</h2>"))
    assert(code.contains("</div>"))
    assertEquals(meltFile.template.size, 1)
    val main = meltFile.template.head.asInstanceOf[TemplateNode.Element]
    assertEquals(main.tag, "main")
  }

  test("HTML function calling another HTML function is preserved and template parses correctly") {
    val src =
      """<script lang="scala">
        |  def icon(name: String): Html = <i class={s"icon $name"}></i>
        |  def button(label: String, ico: String): Html = {
        |    <button>{icon(ico)}{label}</button>
        |  }
        |</script>
        |<div>{button("Save", "save")}</div>""".stripMargin
    val meltFile = parse(src).getOrElse(fail("unexpected error"))
    val code     = meltFile.script.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("def icon(name: String): Html"))
    assert(code.contains("def button(label: String, ico: String): Html"))
    assert(code.contains("{icon(ico)}{label}"))
    val div = meltFile.template.head.asInstanceOf[TemplateNode.Element]
    assertEquals(div.children, List(TemplateNode.Expression("""button("Save", "save")""")))
  }

  test("closing HTML tags inside script do not interfere with section splitting") {
    // </div>, </tr>, </td> etc. inside script must not confuse </script> detection
    val src =
      """<script lang="scala">
        |  def row(a: String, b: String): Html =
        |    <tr><td>{a}</td><td>{b}</td></tr>
        |</script>
        |<table>{row("x", "y")}</table>""".stripMargin
    val meltFile = parse(src).getOrElse(fail("unexpected error"))
    val code     = meltFile.script.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("<tr>"))
    assert(code.contains("</tr>"))
    // Template must not accidentally include the script's HTML
    val table = meltFile.template.head.asInstanceOf[TemplateNode.Element]
    assertEquals(table.tag, "table")
    assertEquals(table.children, List(TemplateNode.Expression("""row("x", "y")""")))
  }

  test("Counter.melt badge function (from Appendix A) is preserved in ScriptSection.code") {
    val src =
      """<script lang="scala" props="Props">
        |  case class Props(label: String, count: Int = 0)
        |  val internal = Var(props.count)
        |  def badge(text: String): Html = {
        |    <span class="badge">{text}</span>
        |  }
        |</script>
        |<div>{badge("hi")}</div>""".stripMargin
    val meltFile = parse(src).getOrElse(fail("unexpected error"))
    val code     = meltFile.script.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("def badge(text: String): Html = {"))
    assert(code.contains("""<span class="badge">{text}</span>"""))
  }

  // ── Complex HTML patterns inside HTML-returning functions ─────────────────

  test("deeply nested HTML in script function is preserved; template is unaffected") {
    val src =
      """<script lang="scala">
        |  def layout(): Html = {
        |    <div>
        |      <header><nav><a href="/">{siteName}</a></nav></header>
        |      <main><section><article>{content}</article></section></main>
        |      <footer><p>{year}</p></footer>
        |    </div>
        |  }
        |</script>
        |<div>{layout()}</div>""".stripMargin
    val meltFile = parse(src).getOrElse(fail("unexpected error"))
    val code     = meltFile.script.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("<header><nav>"))
    assert(code.contains("</article></section></main>"))
    assert(code.contains("<footer><p>{year}</p></footer>"))
    val div = meltFile.template.head.asInstanceOf[TemplateNode.Element]
    assertEquals(div.tag, "div")
    assertEquals(div.children, List(TemplateNode.Expression("layout()")))
  }

  test("HTML function with directive attributes is preserved; template is unaffected") {
    val src =
      """<script lang="scala">
        |  def animatedBox(): Html =
        |    <div transition:fade={opts} use:tooltip={tip} class:active={on}>
        |      {content}
        |    </div>
        |</script>
        |<section>{animatedBox()}</section>""".stripMargin
    val meltFile = parse(src).getOrElse(fail("unexpected error"))
    val code     = meltFile.script.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("transition:fade={opts}"))
    assert(code.contains("use:tooltip={tip}"))
    assert(code.contains("class:active={on}"))
    assertEquals(meltFile.template.size, 1)
  }

  test("HTML function with SVG is preserved; template is unaffected") {
    val src =
      """<script lang="scala">
        |  def icon(color: String): Html =
        |    <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10" style:fill={color} /></svg>
        |</script>
        |<p>{icon("red")}</p>""".stripMargin
    val meltFile = parse(src).getOrElse(fail("unexpected error"))
    val code     = meltFile.script.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("""<svg viewBox="0 0 24 24">"""))
    assert(code.contains("style:fill={color}"))
    val p = meltFile.template.head.asInstanceOf[TemplateNode.Element]
    assertEquals(p.children, List(TemplateNode.Expression("""icon("red")""")))
  }

  // ── Malformed HTML inside HTML-returning functions ────────────────────────
  // The script body is a raw string — the parser does not validate its contents.
  // Malformed HTML in a script function must not corrupt section splitting or
  // template parsing.

  test("unclosed tag in HTML function is stored verbatim; template parses correctly") {
    val src =
      """<script lang="scala">
        |  def broken(): Html = <div><p>unclosed
        |</script>
        |<main><p>ok</p></main>""".stripMargin
    val meltFile = parse(src).getOrElse(fail("unexpected error"))
    val code     = meltFile.script.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("<div><p>unclosed"))
    val main = meltFile.template.head.asInstanceOf[TemplateNode.Element]
    assertEquals(main.tag, "main")
    assertEquals(main.children, List(TemplateNode.Element("p", Nil, List(TemplateNode.Text("ok")))))
  }

  test("mismatched closing tag in HTML function is stored verbatim; template parses correctly") {
    val src =
      """<script lang="scala">
        |  def broken(): Html = <div>hello</span>
        |</script>
        |<p>world</p>""".stripMargin
    val meltFile = parse(src).getOrElse(fail("unexpected error"))
    val code     = meltFile.script.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("<div>hello</span>"))
    val p = meltFile.template.head.asInstanceOf[TemplateNode.Element]
    assertEquals(p.tag, "p")
    assertEquals(p.children, List(TemplateNode.Text("world")))
  }

  test("extra closing tag in HTML function is stored verbatim; template parses correctly") {
    val src =
      """<script lang="scala">
        |  def broken(): Html = <div></div></div>
        |</script>
        |<span>ok</span>""".stripMargin
    val meltFile = parse(src).getOrElse(fail("unexpected error"))
    val code     = meltFile.script.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("<div></div></div>"))
    assertEquals(meltFile.template.head.asInstanceOf[TemplateNode.Element].tag, "span")
  }

  test("self-closing non-void in HTML function is stored verbatim; template parses correctly") {
    val src =
      """<script lang="scala">
        |  def broken(): Html = <section />
        |</script>
        |<p>ok</p>""".stripMargin
    val meltFile = parse(src).getOrElse(fail("unexpected error"))
    val code     = meltFile.script.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("<section />"))
    assertEquals(meltFile.template.head.asInstanceOf[TemplateNode.Element].tag, "p")
  }

  // ── Error cases ───────────────────────────────────────────────────────────

  test("returns Left for unclosed <script lang=\"scala\"> tag") {
    assert(parse("""<script lang="scala">val x = 1""").isLeft)
  }

  test("returns Left for unclosed <style> tag") {
    assert(parse("<p></p><style>div { color: red; }").isLeft)
  }
