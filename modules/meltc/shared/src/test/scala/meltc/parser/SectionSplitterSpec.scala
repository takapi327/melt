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

  // ── Multiple style blocks ─────────────────────────────────────────────────

  test("only the first <style> block is extracted; second stays in template") {
    val src =
      """<p></p>
        |<style>p { color: red; }</style>
        |<div></div>
        |<style>div { margin: 0; }</style>""".stripMargin
    val sections = split(src).getOrElse(fail("unexpected error"))
    assertEquals(sections.style, Some("p { color: red; }"))
    assert(sections.templateSource.contains("div { margin: 0; }"))
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

  // ── Import statements in script body ─────────────────────────────────────

  test("single import is preserved verbatim in code") {
    val src =
      """<script lang="scala">
        |  import scala.collection.mutable.ListBuffer
        |  val xs = ListBuffer.empty[Int]
        |</script>
        |<p></p>""".stripMargin
    val sections = split(src).getOrElse(fail("unexpected error"))
    assert(sections.rawScript.map(_.code).exists(_.contains("import scala.collection.mutable.ListBuffer")))
  }

  test("grouped import with braces is preserved verbatim") {
    // import scala.{List, Map} — the {} must not confuse the splitter
    val src =
      """<script lang="scala">
        |  import scala.{List, Map}
        |</script>
        |<p></p>""".stripMargin
    val sections = split(src).getOrElse(fail("unexpected error"))
    assert(sections.rawScript.map(_.code).exists(_.contains("import scala.{List, Map}")))
  }

  test("Scala 3 import rename (as) is preserved verbatim") {
    val src =
      """<script lang="scala">
        |  import scala.collection.mutable.{ListBuffer as LB}
        |</script>
        |<p></p>""".stripMargin
    val sections = split(src).getOrElse(fail("unexpected error"))
    assert(sections.rawScript.map(_.code).exists(_.contains("import scala.collection.mutable.{ListBuffer as LB}")))
  }

  test("wildcard import is preserved verbatim") {
    val src =
      """<script lang="scala">
        |  import scala.math.*
        |</script>
        |<p></p>""".stripMargin
    val sections = split(src).getOrElse(fail("unexpected error"))
    assert(sections.rawScript.map(_.code).exists(_.contains("import scala.math.*")))
  }

  test("multiple imports are all preserved in code") {
    val src =
      """<script lang="scala">
        |  import scala.math.*
        |  import scala.collection.mutable.{Map as MMap, Set as MSet}
        |  import java.time.Instant
        |  val now = Instant.now()
        |</script>
        |<p></p>""".stripMargin
    val sections = split(src).getOrElse(fail("unexpected error"))
    val code     = sections.rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("import scala.math.*"))
    assert(code.contains("import scala.collection.mutable.{Map as MMap, Set as MSet}"))
    assert(code.contains("import java.time.Instant"))
  }

  // ── Scala language constructs in script body ─────────────────────────────

  test("case class definition is preserved verbatim") {
    val src =
      """<script lang="scala">
        |  case class User(name: String, age: Int)
        |</script>
        |<p></p>""".stripMargin
    val code = split(src).getOrElse(fail("error")).rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("case class User(name: String, age: Int)"))
  }

  test("enum definition is preserved verbatim") {
    val src =
      """<script lang="scala">
        |  enum Color:
        |    case Red, Green, Blue
        |</script>
        |<p></p>""".stripMargin
    val code = split(src).getOrElse(fail("error")).rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("enum Color:"))
    assert(code.contains("case Red, Green, Blue"))
  }

  test("given/using context parameters are preserved verbatim") {
    val src =
      """<script lang="scala">
        |  given Ordering[Int] = Ordering.Int
        |  def sorted[A](xs: List[A])(using ord: Ordering[A]): List[A] = xs.sorted
        |</script>
        |<p></p>""".stripMargin
    val code = split(src).getOrElse(fail("error")).rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("given Ordering[Int]"))
    assert(code.contains("using ord: Ordering[A]"))
  }

  test("extension method is preserved verbatim") {
    val src =
      """<script lang="scala">
        |  extension (s: String)
        |    def shout: String = s.toUpperCase + "!"
        |</script>
        |<p></p>""".stripMargin
    val code = split(src).getOrElse(fail("error")).rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("extension (s: String)"))
    assert(code.contains("def shout: String = s.toUpperCase"))
  }

  test("generic method with type parameters is preserved verbatim") {
    val src =
      """<script lang="scala">
        |  def identity[A](x: A): A = x
        |  def pair[A, B](a: A, b: B): (A, B) = (a, b)
        |</script>
        |<p></p>""".stripMargin
    val code = split(src).getOrElse(fail("error")).rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("def identity[A](x: A): A = x"))
    assert(code.contains("def pair[A, B](a: A, b: B): (A, B) = (a, b)"))
  }

  test("pattern matching expression is preserved verbatim") {
    val src =
      """<script lang="scala">
        |  val label = status match
        |    case 0 => "zero"
        |    case n if n > 0 => "positive"
        |    case _ => "negative"
        |</script>
        |<p></p>""".stripMargin
    val code = split(src).getOrElse(fail("error")).rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("status match"))
    assert(code.contains("""case 0 => "zero""""))
    assert(code.contains("""case _ => "negative""""))
  }

  test("for comprehension is preserved verbatim") {
    val src =
      """<script lang="scala">
        |  val result = for
        |    x <- List(1, 2, 3)
        |    y <- List(10, 20)
        |  yield x * y
        |</script>
        |<p></p>""".stripMargin
    val code = split(src).getOrElse(fail("error")).rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("for"))
    assert(code.contains("x <- List(1, 2, 3)"))
    assert(code.contains("yield x * y"))
  }

  test("type alias is preserved verbatim") {
    val src =
      """<script lang="scala">
        |  type UserId = String
        |  type Callback[A] = A => Unit
        |</script>
        |<p></p>""".stripMargin
    val code = split(src).getOrElse(fail("error")).rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("type UserId = String"))
    assert(code.contains("type Callback[A] = A => Unit"))
  }

  test("object definition is preserved verbatim") {
    val src =
      """<script lang="scala">
        |  object Config:
        |    val host = "localhost"
        |    val port = 8080
        |</script>
        |<p></p>""".stripMargin
    val code = split(src).getOrElse(fail("error")).rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("object Config:"))
    assert(code.contains("""val host = "localhost""""))
  }

  test("annotation on a definition is preserved verbatim") {
    val src =
      """<script lang="scala">
        |  @deprecated("use newMethod instead", "1.0")
        |  def oldMethod(): Unit = ()
        |</script>
        |<p></p>""".stripMargin
    val code = split(src).getOrElse(fail("error")).rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("@deprecated"))
    assert(code.contains("def oldMethod()"))
  }

  test("comparison operators < and > in script are not confused with tags") {
    val src =
      """<script lang="scala">
        |  val inRange = x > 0 && x < 100
        |</script>
        |<p></p>""".stripMargin
    val code = split(src).getOrElse(fail("error")).rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("x > 0 && x < 100"))
  }

  test("triple-quoted string in script is preserved verbatim") {
    val src =
      "<script lang=\"scala\">\n" +
        "  val msg = \"\"\"Hello\n  World\"\"\"\n" +
        "</script>\n<p></p>"
    val code = split(src).getOrElse(fail("error")).rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("\"\"\"Hello"))
  }

  test("HTML-like string literal in script is preserved (known limitation: </script> inside string breaks splitter)") {
    // A string containing HTML tags other than </script> is fine
    val src =
      """<script lang="scala">
        |  val html = "<div>hello</div>"
        |</script>
        |<p></p>""".stripMargin
    val code = split(src).getOrElse(fail("error")).rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("""val html = "<div>hello</div>""""))
  }

  // ── HTML-returning functions in script body ───────────────────────────────

  test("simple inline HTML-returning function is preserved verbatim") {
    val src =
      """<script lang="scala">
        |  def badge(text: String): Html = <span class="badge">{text}</span>
        |</script>
        |<p></p>""".stripMargin
    val code = split(src).getOrElse(fail("error")).rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("""def badge(text: String): Html = <span class="badge">{text}</span>"""))
  }

  test("multi-line HTML-returning function with braces is preserved verbatim") {
    val src =
      """<script lang="scala">
        |  def badge(text: String): Html = {
        |    <span class="badge">{text}</span>
        |  }
        |</script>
        |<p></p>""".stripMargin
    val code = split(src).getOrElse(fail("error")).rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("def badge(text: String): Html = {"))
    assert(code.contains("""<span class="badge">{text}</span>"""))
  }

  test("HTML-returning function with dynamic attribute is preserved verbatim") {
    val src =
      """<script lang="scala">
        |  def link(href: String, label: String): Html =
        |    <a href={href}>{label}</a>
        |</script>
        |<p></p>""".stripMargin
    val code = split(src).getOrElse(fail("error")).rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("def link(href: String, label: String): Html ="))
    assert(code.contains("<a href={href}>{label}</a>"))
  }

  test("HTML-returning function with nested elements is preserved verbatim") {
    val src =
      """<script lang="scala">
        |  def card(title: String, body: String): Html = {
        |    <div class="card">
        |      <h2>{title}</h2>
        |      <p>{body}</p>
        |    </div>
        |  }
        |</script>
        |<p></p>""".stripMargin
    val code = split(src).getOrElse(fail("error")).rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("""<div class="card">"""))
    assert(code.contains("<h2>{title}</h2>"))
    assert(code.contains("<p>{body}</p>"))
    assert(code.contains("</div>"))
  }

  test("HTML-returning function with void element is preserved verbatim") {
    val src =
      """<script lang="scala">
        |  def avatar(src: String, alt: String): Html =
        |    <img src={src} alt={alt} />
        |</script>
        |<p></p>""".stripMargin
    val code = split(src).getOrElse(fail("error")).rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("<img src={src} alt={alt} />"))
  }

  test("multiple HTML-returning functions in script are all preserved") {
    val src =
      """<script lang="scala">
        |  def icon(name: String): Html = <span class={s"icon-$name"}></span>
        |  def label(text: String): Html = <span class="label">{text}</span>
        |  def iconLabel(name: String, text: String): Html = {
        |    <span>{icon(name)}{label(text)}</span>
        |  }
        |</script>
        |<p></p>""".stripMargin
    val code = split(src).getOrElse(fail("error")).rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("def icon(name: String): Html"))
    assert(code.contains("def label(text: String): Html"))
    assert(code.contains("def iconLabel(name: String, text: String): Html"))
    assert(code.contains("{icon(name)}{label(text)}"))
  }

  test("closing HTML tags inside script do not confuse </script> detection") {
    // </div>, </p>, </span> etc. must NOT be mistaken for </script>
    val src =
      """<script lang="scala">
        |  def row(cells: List[String]): Html = {
        |    <tr>{cells.map(c => <td>{c}</td>)}</tr>
        |  }
        |</script>
        |<table></table>""".stripMargin
    val sections = split(src).getOrElse(fail("error"))
    assert(sections.rawScript.isDefined)
    val code = sections.rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("<tr>"))
    assert(code.contains("</tr>"))
    // Template is also correctly identified
    assert(sections.templateSource.contains("<table>"))
  }

  // ── Complex HTML patterns inside HTML-returning functions ─────────────────

  test("deeply nested HTML in script function is preserved verbatim") {
    val src =
      """<script lang="scala">
        |  def nested(): Html = {
        |    <div>
        |      <section>
        |        <article>
        |          <header><h1>{title}</h1></header>
        |          <main><p>{body}</p></main>
        |          <footer><small>{footer}</small></footer>
        |        </article>
        |      </section>
        |    </div>
        |  }
        |</script>
        |<p></p>""".stripMargin
    val code = split(src).getOrElse(fail("error")).rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("<section>"))
    assert(code.contains("<article>"))
    assert(code.contains("<header><h1>{title}</h1></header>"))
    assert(code.contains("</article>"))
    assert(code.contains("</section>"))
  }

  test("HTML function with many attribute types is preserved verbatim") {
    val src =
      """<script lang="scala">
        |  def fancyInput(): Html =
        |    <input
        |      type="text"
        |      bind:value={name}
        |      class:error={hasError}
        |      style:color={textColor}
        |      disabled={isDisabled}
        |      aria-label="Name"
        |      data-testid="name-input"
        |    />
        |</script>
        |<p></p>""".stripMargin
    val code = split(src).getOrElse(fail("error")).rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("bind:value={name}"))
    assert(code.contains("class:error={hasError}"))
    assert(code.contains("style:color={textColor}"))
    assert(code.contains("""aria-label="Name""""))
    assert(code.contains("""data-testid="name-input""""))
  }

  test("HTML function with SVG content is preserved verbatim") {
    val src =
      """<script lang="scala">
        |  def progressRing(pct: Int): Html = {
        |    <svg viewBox="0 0 36 36" class="ring">
        |      <circle cx="18" cy="18" r="15" />
        |      <text x="18" y="22" text-anchor="middle">{pct}%</text>
        |    </svg>
        |  }
        |</script>
        |<p></p>""".stripMargin
    val code = split(src).getOrElse(fail("error")).rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("""<svg viewBox="0 0 36 36" class="ring">"""))
    assert(code.contains("""<circle cx="18" cy="18" r="15" />"""))
    assert(code.contains("""text-anchor="middle">"""))
    assert(code.contains("</svg>"))
  }

  test("HTML function with table structure is preserved verbatim") {
    val src =
      """<script lang="scala">
        |  def tableRow(cells: List[String]): Html = {
        |    <tr>
        |      {cells.zipWithIndex.map { case (c, i) =>
        |        <td data-col={i.toString}>{c}</td>
        |      }}
        |    </tr>
        |  }
        |</script>
        |<table></table>""".stripMargin
    val code = split(src).getOrElse(fail("error")).rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("<tr>"))
    assert(code.contains("<td data-col={i.toString}>{c}</td>"))
    assert(code.contains("</tr>"))
  }

  test("HTML function returning a Component with props is preserved verbatim") {
    val src =
      """<script lang="scala">
        |  def wrappedCard(title: String): Html = {
        |    <Card title={title} variant="primary">
        |      <p>content</p>
        |    </Card>
        |  }
        |</script>
        |<div></div>""".stripMargin
    val code = split(src).getOrElse(fail("error")).rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("""<Card title={title} variant="primary">"""))
    assert(code.contains("<p>content</p>"))
    assert(code.contains("</Card>"))
  }

  // ── Malformed HTML inside HTML-returning functions ────────────────────────
  // SectionSplitter does not validate Scala or HTML — it preserves the raw
  // code string verbatim regardless of correctness.

  test("unclosed tag inside HTML function is preserved verbatim") {
    val src =
      """<script lang="scala">
        |  def broken(): Html = <div><p>text
        |</script>
        |<p></p>""".stripMargin
    val code = split(src).getOrElse(fail("error")).rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("<div><p>text"))
  }

  test("mismatched closing tag inside HTML function is preserved verbatim") {
    val src =
      """<script lang="scala">
        |  def broken(): Html = <div>hello</span>
        |</script>
        |<p></p>""".stripMargin
    val code = split(src).getOrElse(fail("error")).rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("<div>hello</span>"))
  }

  test("extra closing tag inside HTML function is preserved verbatim") {
    val src =
      """<script lang="scala">
        |  def broken(): Html = <div></div></div>
        |</script>
        |<p></p>""".stripMargin
    val code = split(src).getOrElse(fail("error")).rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("<div></div></div>"))
  }

  test("self-closing non-void element inside HTML function is preserved verbatim") {
    val src =
      """<script lang="scala">
        |  def broken(): Html = <div />
        |</script>
        |<p></p>""".stripMargin
    val code = split(src).getOrElse(fail("error")).rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("<div />"))
  }

  test("attribute with no value inside HTML function is preserved verbatim") {
    val src =
      """<script lang="scala">
        |  def broken(): Html = <div class=>text</div>
        |</script>
        |<p></p>""".stripMargin
    val code = split(src).getOrElse(fail("error")).rawScript.map(_.code).getOrElse(fail("no script"))
    assert(code.contains("<div class=>text</div>"))
  }

  test("malformed HTML in script does not corrupt the template section") {
    val src =
      """<script lang="scala">
        |  def broken(): Html = <div><p>unclosed
        |</script>
        |<main><p>ok</p></main>""".stripMargin
    val sections = split(src).getOrElse(fail("error"))
    assert(sections.rawScript.isDefined)
    assert(sections.templateSource.contains("<main>"))
    assert(sections.templateSource.contains("<p>ok</p>"))
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
