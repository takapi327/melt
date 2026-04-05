/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.parser

import meltc.ast.{Attr, TemplateNode}

class TemplateParserSpec extends munit.FunSuite:

  private def parse(src: String) = TemplateParser.parse(src)

  // ── Text and Expression ───────────────────────────────────────────────────

  test("plain text node") {
    assertEquals(parse("Hello"), List(TemplateNode.Text("Hello")))
  }

  test("simple expression") {
    assertEquals(parse("{count}"), List(TemplateNode.Expression("count")))
  }

  test("whitespace-only text is discarded") {
    assertEquals(parse("   \n  "), Nil)
  }

  test("HTML comment is discarded") {
    assertEquals(parse("<!-- this is a comment --><p>hi</p>"),
      List(TemplateNode.Element("p", Nil, List(TemplateNode.Text("hi")))))
  }

  // ── Elements ──────────────────────────────────────────────────────────────

  test("element with no attributes and text child") {
    assertEquals(
      parse("<div>Hello</div>"),
      List(TemplateNode.Element("div", Nil, List(TemplateNode.Text("Hello"))))
    )
  }

  test("element with static attribute") {
    assertEquals(
      parse("""<div class="foo">bar</div>"""),
      List(TemplateNode.Element("div", List(Attr.Static("class", "foo")), List(TemplateNode.Text("bar"))))
    )
  }

  test("element with dynamic attribute") {
    assertEquals(
      parse("<div class={cls}></div>"),
      List(TemplateNode.Element("div", List(Attr.Dynamic("class", "cls")), Nil))
    )
  }

  test("element with boolean attribute") {
    assertEquals(
      parse("<input disabled />"),
      List(TemplateNode.Element("input", List(Attr.BooleanAttr("disabled")), Nil))
    )
  }

  test("event handler attribute (onclick)") {
    assertEquals(
      parse("<button onclick={handler}>Click</button>"),
      List(TemplateNode.Element(
        "button",
        List(Attr.EventHandler("click", "handler")),
        List(TemplateNode.Text("Click"))
      ))
    )
  }

  test("bind directive attribute") {
    assertEquals(
      parse("<input bind:value={name} />"),
      List(TemplateNode.Element("input", List(Attr.Directive("bind", "value", Some("name"))), Nil))
    )
  }

  test("class directive attribute") {
    assertEquals(
      parse("<div class:active={isActive}></div>"),
      List(TemplateNode.Element("div", List(Attr.Directive("class", "active", Some("isActive"))), Nil))
    )
  }

  test("self-closing tag with no children") {
    assertEquals(
      parse("<br />"),
      List(TemplateNode.Element("br", Nil, Nil))
    )
  }

  test("void element <input> without explicit />") {
    assertEquals(
      parse("""<input type="text">"""),
      List(TemplateNode.Element("input", List(Attr.Static("type", "text")), Nil))
    )
  }

  // ── Nested elements ────────────────────────────────────────────────────────

  test("nested elements") {
    val result = parse("<div><p>Hi</p></div>")
    assertEquals(result, List(
      TemplateNode.Element("div", Nil, List(
        TemplateNode.Element("p", Nil, List(TemplateNode.Text("Hi")))
      ))
    ))
  }

  test("sibling text and expression in same element") {
    val result = parse("<p>Count: {n}</p>")
    assertEquals(result, List(
      TemplateNode.Element("p", Nil, List(
        TemplateNode.Text("Count: "),
        TemplateNode.Expression("n")
      ))
    ))
  }

  // ── Component (uppercase tag) ─────────────────────────────────────────────

  test("uppercase tag becomes Component node") {
    assertEquals(
      parse("""<Counter label="x" />"""),
      List(TemplateNode.Component("Counter", List(Attr.Static("label", "x")), Nil))
    )
  }

  // ── Nested braces in expressions ──────────────────────────────────────────

  test("nested braces in expression") {
    assertEquals(
      parse("{Map(1 -> 2).getOrElse(1, 0)}"),
      List(TemplateNode.Expression("Map(1 -> 2).getOrElse(1, 0)"))
    )
  }

  test("expression with string literal containing braces") {
    assertEquals(
      parse("""{"hello {world}"}"""),
      List(TemplateNode.Expression(""""hello {world}""""))
    )
  }

  test("expression with interpolated string") {
    assertEquals(
      parse("""{s"Count: ${count.now()}"}"""),
      List(TemplateNode.Expression("""s"Count: ${count.now()}""""))
    )
  }

  test("function call with method chain (no nested braces)") {
    assertEquals(
      parse("{badge(internal.now().toString)}"),
      List(TemplateNode.Expression("badge(internal.now().toString)"))
    )
  }

  // ── Multiple attributes ────────────────────────────────────────────────────

  test("element with multiple attributes") {
    val result = parse("""<input type="text" bind:value={name} placeholder="Name" />""")
    assertEquals(result, List(
      TemplateNode.Element("input", List(
        Attr.Static("type", "text"),
        Attr.Directive("bind", "value", Some("name")),
        Attr.Static("placeholder", "Name")
      ), Nil)
    ))
  }

  // ── Multiple sibling elements ─────────────────────────────────────────────

  test("two sibling top-level elements") {
    val result = parse("<h1>Title</h1><p>Body</p>")
    assertEquals(result, List(
      TemplateNode.Element("h1", Nil, List(TemplateNode.Text("Title"))),
      TemplateNode.Element("p",  Nil, List(TemplateNode.Text("Body")))
    ))
  }

  test("expression between two elements") {
    val result = parse("<span>A</span>{sep}<span>B</span>")
    assertEquals(result, List(
      TemplateNode.Element("span", Nil, List(TemplateNode.Text("A"))),
      TemplateNode.Expression("sep"),
      TemplateNode.Element("span", Nil, List(TemplateNode.Text("B")))
    ))
  }

  // ── Deep nesting ──────────────────────────────────────────────────────────

  test("three levels of nesting") {
    val result = parse("<ul><li><a href=\"#\">link</a></li></ul>")
    assertEquals(result, List(
      TemplateNode.Element("ul", Nil, List(
        TemplateNode.Element("li", Nil, List(
          TemplateNode.Element("a", List(Attr.Static("href", "#")), List(TemplateNode.Text("link")))
        ))
      ))
    ))
  }

  test("sibling elements inside parent") {
    val result = parse("<div><span>A</span><span>B</span></div>")
    assertEquals(result, List(
      TemplateNode.Element("div", Nil, List(
        TemplateNode.Element("span", Nil, List(TemplateNode.Text("A"))),
        TemplateNode.Element("span", Nil, List(TemplateNode.Text("B")))
      ))
    ))
  }

  // ── Multiple consecutive expressions ──────────────────────────────────────

  test("two consecutive expressions with text between them") {
    val result = parse("<p>{first} and {second}</p>")
    assertEquals(result, List(
      TemplateNode.Element("p", Nil, List(
        TemplateNode.Expression("first"),
        TemplateNode.Text(" and "),
        TemplateNode.Expression("second")
      ))
    ))
  }

  test("three consecutive expressions without separator") {
    val result = parse("{a}{b}{c}")
    assertEquals(result, List(
      TemplateNode.Expression("a"),
      TemplateNode.Expression("b"),
      TemplateNode.Expression("c")
    ))
  }

  // ── If-then-else expression ───────────────────────────────────────────────

  test("if-then-else expression in element") {
    val result = parse("""<span class={if active then "on" else "off"}></span>""")
    assertEquals(result, List(
      TemplateNode.Element("span",
        List(Attr.Dynamic("class", """if active then "on" else "off"""")),
        Nil)
    ))
  }

  test("if-then-else expression as child node") {
    val result = parse("""<p>{if n > 0 then "positive" else "non-positive"}</p>""")
    assertEquals(result, List(
      TemplateNode.Element("p", Nil, List(
        TemplateNode.Expression("""if n > 0 then "positive" else "non-positive"""")
      ))
    ))
  }

  // ── Single-quoted attribute values ────────────────────────────────────────

  test("single-quoted static attribute") {
    assertEquals(
      parse("<div class='foo'></div>"),
      List(TemplateNode.Element("div", List(Attr.Static("class", "foo")), Nil))
    )
  }

  // ── Multiline attributes ──────────────────────────────────────────────────

  test("attributes spread across multiple lines") {
    val result = parse(
      """<input
        |  type="text"
        |  bind:value={name}
        |  placeholder="Enter name"
        |/>""".stripMargin
    )
    assertEquals(result, List(
      TemplateNode.Element("input", List(
        Attr.Static("type", "text"),
        Attr.Directive("bind", "value", Some("name")),
        Attr.Static("placeholder", "Enter name")
      ), Nil)
    ))
  }

  // ── Various void elements ─────────────────────────────────────────────────

  test("<hr> void element without />") {
    assertEquals(parse("<hr>"), List(TemplateNode.Element("hr", Nil, Nil)))
  }

  test("<img> with attributes") {
    val result = parse("""<img src="photo.png" alt="photo">""")
    assertEquals(result, List(
      TemplateNode.Element("img", List(
        Attr.Static("src", "photo.png"),
        Attr.Static("alt", "photo")
      ), Nil)
    ))
  }

  test("<meta> void element") {
    assertEquals(
      parse("""<meta charset="utf-8">"""),
      List(TemplateNode.Element("meta", List(Attr.Static("charset", "utf-8")), Nil))
    )
  }

  // ── Additional directives ─────────────────────────────────────────────────

  test("style: directive") {
    assertEquals(
      parse("<div style:color={textColor}></div>"),
      List(TemplateNode.Element("div", List(Attr.Directive("style", "color", Some("textColor"))), Nil))
    )
  }

  test("use: directive") {
    assertEquals(
      parse("<div use:tooltip={opts}></div>"),
      List(TemplateNode.Element("div", List(Attr.Directive("use", "tooltip", Some("opts"))), Nil))
    )
  }

  test("transition: directive") {
    assertEquals(
      parse("<div transition:fade></div>"),
      List(TemplateNode.Element("div", List(Attr.Directive("transition", "fade", None)), Nil))
    )
  }

  // ── Various event handlers ────────────────────────────────────────────────

  test("onkeydown event handler") {
    assertEquals(
      parse("<input onkeydown={handleKey} />"),
      List(TemplateNode.Element("input", List(Attr.EventHandler("keydown", "handleKey")), Nil))
    )
  }

  test("onsubmit event handler on form") {
    assertEquals(
      parse("<form onsubmit={handleSubmit}></form>"),
      List(TemplateNode.Element("form", List(Attr.EventHandler("submit", "handleSubmit")), Nil))
    )
  }

  // ── Component with children ───────────────────────────────────────────────

  test("Component with child elements") {
    val result = parse("<Card><p>content</p></Card>")
    assertEquals(result, List(
      TemplateNode.Component("Card", Nil, List(
        TemplateNode.Element("p", Nil, List(TemplateNode.Text("content")))
      ))
    ))
  }

  test("Component with dynamic prop and child text") {
    val result = parse("""<Modal title={t}>Hello</Modal>""")
    assertEquals(result, List(
      TemplateNode.Component("Modal", List(Attr.Dynamic("title", "t")),
        List(TemplateNode.Text("Hello")))
    ))
  }

  // ── Empty element ─────────────────────────────────────────────────────────

  test("empty element produces no children") {
    assertEquals(
      parse("<div></div>"),
      List(TemplateNode.Element("div", Nil, Nil))
    )
  }

  // ── Complex real-world HTML structures ────────────────────────────────────

  test("table structure") {
    val html =
      """<table>
        |  <thead><tr><th>Name</th><th>Score</th></tr></thead>
        |  <tbody><tr><td>{name}</td><td>{score}</td></tr></tbody>
        |</table>""".stripMargin
    val result = parse(html)
    assertEquals(result.size, 1)
    val table = result.head.asInstanceOf[TemplateNode.Element]
    assertEquals(table.tag, "table")
    val sections = table.children
    assertEquals(sections.size, 2)
    val thead = sections(0).asInstanceOf[TemplateNode.Element]
    assertEquals(thead.tag, "thead")
    val tbody = sections(1).asInstanceOf[TemplateNode.Element]
    assertEquals(tbody.tag, "tbody")
    // tbody > tr > td > Expression
    val td = tbody.children.head.asInstanceOf[TemplateNode.Element]
      .children.head.asInstanceOf[TemplateNode.Element]
    assertEquals(td.children, List(TemplateNode.Expression("name")))
  }

  test("form with various input types") {
    val html =
      """<form onsubmit={handleSubmit}>
        |  <input type="text" bind:value={username} placeholder="Username" />
        |  <input type="password" bind:value={password} />
        |  <input type="checkbox" bind:checked={remember} />
        |  <button type="submit">Login</button>
        |</form>""".stripMargin
    val result = parse(html)
    val form   = result.head.asInstanceOf[TemplateNode.Element]
    assertEquals(form.tag, "form")
    assertEquals(form.attrs, List(Attr.EventHandler("submit", "handleSubmit")))
    assertEquals(form.children.size, 4)
    // password input
    val pwInput = form.children(1).asInstanceOf[TemplateNode.Element]
    assertEquals(pwInput.attrs, List(
      Attr.Static("type", "password"),
      Attr.Directive("bind", "value", Some("password"))
    ))
    // checkbox
    val checkbox = form.children(2).asInstanceOf[TemplateNode.Element]
    assertEquals(checkbox.attrs, List(
      Attr.Static("type", "checkbox"),
      Attr.Directive("bind", "checked", Some("remember"))
    ))
  }

  test("inline elements mixed with text") {
    val html = "<p>Hello <strong>world</strong> and <em>everyone</em>!</p>"
    val result = parse(html)
    val p = result.head.asInstanceOf[TemplateNode.Element]
    assertEquals(p.children, List(
      TemplateNode.Text("Hello "),
      TemplateNode.Element("strong", Nil, List(TemplateNode.Text("world"))),
      TemplateNode.Text(" and "),
      TemplateNode.Element("em", Nil, List(TemplateNode.Text("everyone"))),
      TemplateNode.Text("!")
    ))
  }

  test("navigation list with links") {
    val html =
      """<nav>
        |  <ul>
        |    <li><a href="/">Home</a></li>
        |    <li><a href="/about">About</a></li>
        |    <li><a href="/contact">Contact</a></li>
        |  </ul>
        |</nav>""".stripMargin
    val result = parse(html)
    val nav  = result.head.asInstanceOf[TemplateNode.Element]
    val ul   = nav.children.head.asInstanceOf[TemplateNode.Element]
    assertEquals(ul.tag, "ul")
    assertEquals(ul.children.size, 3)
    val firstLi = ul.children.head.asInstanceOf[TemplateNode.Element]
    val anchor  = firstLi.children.head.asInstanceOf[TemplateNode.Element]
    assertEquals(anchor.tag, "a")
    assertEquals(anchor.attrs, List(Attr.Static("href", "/")))
    assertEquals(anchor.children, List(TemplateNode.Text("Home")))
  }

  test("div with mixed void and non-void children") {
    val html = "<div><img src=\"a.png\" alt=\"\"/><p>text</p><br/><span>x</span></div>"
    val div = parse(html).head.asInstanceOf[TemplateNode.Element]
    assertEquals(div.children.size, 4)
    assertEquals(div.children(0).asInstanceOf[TemplateNode.Element].tag, "img")
    assertEquals(div.children(1).asInstanceOf[TemplateNode.Element].tag, "p")
    assertEquals(div.children(2).asInstanceOf[TemplateNode.Element].tag, "br")
    assertEquals(div.children(3).asInstanceOf[TemplateNode.Element].tag, "span")
  }

  test("attribute with empty string value") {
    assertEquals(
      parse("""<div class="">content</div>"""),
      List(TemplateNode.Element("div", List(Attr.Static("class", "")), List(TemplateNode.Text("content"))))
    )
  }

  test("element with many attributes of different kinds") {
    val result = parse(
      """<div id="main" class={cls} style:color={color} use:tooltip={tip} aria-label="Main">
        |</div>""".stripMargin
    )
    val div = result.head.asInstanceOf[TemplateNode.Element]
    assertEquals(div.attrs, List(
      Attr.Static("id", "main"),
      Attr.Dynamic("class", "cls"),
      Attr.Directive("style", "color", Some("color")),
      Attr.Directive("use", "tooltip", Some("tip")),
      Attr.Static("aria-label", "Main")
    ))
  }

  // ── Malformed input (lenient parsing) ─────────────────────────────────────

  test("unclosed tag is treated leniently — children still parsed") {
    // <div> has no </div>; parser returns what it can without throwing
    val result = parse("<div><p>text</p>")
    // At minimum, we get one top-level node
    assert(result.nonEmpty)
    val el = result.head.asInstanceOf[TemplateNode.Element]
    assertEquals(el.tag, "div")
    // The <p> child should be present
    val p = el.children.head.asInstanceOf[TemplateNode.Element]
    assertEquals(p.tag, "p")
  }

  test("mismatched closing tag is consumed leniently") {
    // <div>...</span> — parser consumes </span> as the closing of <div>
    val result = parse("<div>hello</span>")
    assert(result.nonEmpty)
    val el = result.head.asInstanceOf[TemplateNode.Element]
    assertEquals(el.tag, "div")
    assertEquals(el.children, List(TemplateNode.Text("hello")))
  }

  test("stray closing tag at top level is silently ignored") {
    // A lone </div> at top level should not crash
    val result = parse("</div><p>ok</p>")
    // The </div> is not a valid open tag, so parser skips it and finds <p>
    assert(result.exists {
      case TemplateNode.Element("p", _, _) => true
      case _                               => false
    })
  }

  // ── Hyphenated tag names (Web Components) ────────────────────────────────

  test("hyphenated custom element tag is parsed as Element") {
    val result = parse("<my-component>text</my-component>")
    assertEquals(result, List(
      TemplateNode.Element("my-component", Nil, List(TemplateNode.Text("text")))
    ))
  }

  test("web component with attributes") {
    val result = parse("""<my-button variant="primary" onclick={handler}>Click</my-button>""")
    val el = result.head.asInstanceOf[TemplateNode.Element]
    assertEquals(el.tag, "my-button")
    assertEquals(el.attrs, List(
      Attr.Static("variant", "primary"),
      Attr.EventHandler("click", "handler")
    ))
  }

  // ── data-* and aria-* attributes ──────────────────────────────────────────

  test("data-* static attributes") {
    val result = parse("""<div data-id="123" data-label="item"></div>""")
    val div = result.head.asInstanceOf[TemplateNode.Element]
    assertEquals(div.attrs, List(
      Attr.Static("data-id", "123"),
      Attr.Static("data-label", "item")
    ))
  }

  test("data-* dynamic attribute") {
    val result = parse("<li data-index={idx} data-selected={isActive}></li>")
    val li = result.head.asInstanceOf[TemplateNode.Element]
    assertEquals(li.attrs, List(
      Attr.Dynamic("data-index", "idx"),
      Attr.Dynamic("data-selected", "isActive")
    ))
  }

  test("aria-* attributes") {
    val result = parse(
      """<button aria-expanded={isOpen} aria-controls="panel" aria-label="Toggle">+</button>"""
    )
    val btn = result.head.asInstanceOf[TemplateNode.Element]
    assertEquals(btn.attrs, List(
      Attr.Dynamic("aria-expanded", "isOpen"),
      Attr.Static("aria-controls", "panel"),
      Attr.Static("aria-label", "Toggle")
    ))
  }

  // ── Comparison operator > in attribute expression ─────────────────────────

  test("attribute expression with > operator does not break tag parsing") {
    val result = parse("""<div class={if count > 0 then "pos" else "zero"}></div>""")
    val div = result.head.asInstanceOf[TemplateNode.Element]
    assertEquals(div.attrs, List(
      Attr.Dynamic("class", """if count > 0 then "pos" else "zero"""")
    ))
    assertEquals(div.children, Nil)
  }

  test("attribute expression with chained comparisons") {
    val result = parse("""<span class={if x > 0 && y < 100 then "valid" else "invalid"}></span>""")
    val span = result.head.asInstanceOf[TemplateNode.Element]
    assertEquals(span.attrs, List(
      Attr.Dynamic("class", """if x > 0 && y < 100 then "valid" else "invalid"""")
    ))
  }

  // ── Self-closing without space (<br/>, <input/>) ──────────────────────────

  test("<br/> self-closing without space") {
    assertEquals(parse("<br/>"), List(TemplateNode.Element("br", Nil, Nil)))
  }

  test("<input/> self-closing without space") {
    assertEquals(
      parse("""<input type="text"/>"""),
      List(TemplateNode.Element("input", List(Attr.Static("type", "text")), Nil))
    )
  }

  // ── Multiple boolean attributes ───────────────────────────────────────────

  test("multiple boolean attributes") {
    val result = parse("<input disabled readonly required />")
    val input = result.head.asInstanceOf[TemplateNode.Element]
    assertEquals(input.attrs, List(
      Attr.BooleanAttr("disabled"),
      Attr.BooleanAttr("readonly"),
      Attr.BooleanAttr("required")
    ))
  }

  test("boolean attributes mixed with static and dynamic") {
    val result = parse("""<input type="text" disabled bind:value={v} readonly />""")
    val input = result.head.asInstanceOf[TemplateNode.Element]
    assertEquals(input.attrs, List(
      Attr.Static("type", "text"),
      Attr.BooleanAttr("disabled"),
      Attr.Directive("bind", "value", Some("v")),
      Attr.BooleanAttr("readonly")
    ))
  }

  // ── Unquoted attribute values ─────────────────────────────────────────────

  test("unquoted attribute value") {
    val result = parse("<div id=main></div>")
    assertEquals(result, List(
      TemplateNode.Element("div", List(Attr.Static("id", "main")), Nil)
    ))
  }

  test("multiple unquoted attribute values") {
    val result = parse("<input type=text id=username />")
    val input = result.head.asInstanceOf[TemplateNode.Element]
    assertEquals(input.attrs, List(
      Attr.Static("type", "text"),
      Attr.Static("id", "username")
    ))
  }

  // ── <script> and <style> elements in template ─────────────────────────────

  test("<script> without lang=scala in template is parsed as Element") {
    val result = parse("""<script>console.log("hi")</script>""")
    val el = result.head.asInstanceOf[TemplateNode.Element]
    assertEquals(el.tag, "script")
    // The JS content becomes a text child
    assertEquals(el.children.size, 1)
  }

  test("<script src=...> is parsed as Element") {
    val result = parse("""<script src="app.js"></script>""")
    val el = result.head.asInstanceOf[TemplateNode.Element]
    assertEquals(el.tag, "script")
    assertEquals(el.attrs, List(Attr.Static("src", "app.js")))
    assertEquals(el.children, Nil)
  }

  // ── SVG elements ──────────────────────────────────────────────────────────

  test("SVG element with viewBox attribute") {
    val result = parse("""<svg viewBox="0 0 100 100"></svg>""")
    assertEquals(result, List(
      TemplateNode.Element("svg", List(Attr.Static("viewBox", "0 0 100 100")), Nil)
    ))
  }

  test("SVG circle self-closing") {
    val result = parse("""<svg><circle cx="50" cy="50" r="30" /></svg>""")
    val svg = result.head.asInstanceOf[TemplateNode.Element]
    assertEquals(svg.tag, "svg")
    val circle = svg.children.head.asInstanceOf[TemplateNode.Element]
    assertEquals(circle.tag, "circle")
    assertEquals(circle.attrs, List(
      Attr.Static("cx", "50"),
      Attr.Static("cy", "50"),
      Attr.Static("r", "30")
    ))
    assertEquals(circle.children, Nil)
  }

  test("SVG with reactive fill attribute") {
    val result = parse("""<circle cx="50" cy="50" r="30" style:fill={color} />""")
    val circle = result.head.asInstanceOf[TemplateNode.Element]
    assertEquals(circle.attrs, List(
      Attr.Static("cx", "50"),
      Attr.Static("cy", "50"),
      Attr.Static("r", "30"),
      Attr.Directive("style", "fill", Some("color"))
    ))
  }

  test("nested SVG structure") {
    val result = parse("""<svg><g><rect x="10" y="10" width="80" height="80" /></g></svg>""")
    val svg = result.head.asInstanceOf[TemplateNode.Element]
    val g   = svg.children.head.asInstanceOf[TemplateNode.Element]
    assertEquals(g.tag, "g")
    val rect = g.children.head.asInstanceOf[TemplateNode.Element]
    assertEquals(rect.tag, "rect")
    assertEquals(rect.children, Nil)
  }

  // ── Text content containing literal < ────────────────────────────────────

  test("literal < in text content (not followed by letter)") {
    val result = parse("<p>price < $100</p>")
    val p = result.head.asInstanceOf[TemplateNode.Element]
    // '<' followed by ' ' is not a tag start, so it becomes literal text
    assertEquals(p.children, List(TemplateNode.Text("price < $100")))
  }

  test("left-shift operator << in text content") {
    val result = parse("<p>x << 2</p>")
    val p = result.head.asInstanceOf[TemplateNode.Element]
    assertEquals(p.children, List(TemplateNode.Text("x << 2")))
  }

  test("expression with < operator in template text") {
    val result = parse("<p>{items.filter(x => x < 10).size} items</p>")
    val p = result.head.asInstanceOf[TemplateNode.Element]
    assertEquals(p.children, List(
      TemplateNode.Expression("items.filter(x => x < 10).size"),
      TemplateNode.Text(" items")
    ))
  }

  // ── DOCTYPE declaration ───────────────────────────────────────────────────

  test("<!DOCTYPE html> is parsed as text and does not crash") {
    val result = parse("<!DOCTYPE html><div>content</div>")
    // DOCTYPE is not a valid tag start ('!' is not a letter), so it's included in text
    // followed by the normal div element
    val elements = result.filter(_.isInstanceOf[TemplateNode.Element])
    assertEquals(elements.size, 1)
    val div = elements.head.asInstanceOf[TemplateNode.Element]
    assertEquals(div.tag, "div")
    assertEquals(div.children, List(TemplateNode.Text("content")))
  }

  // ── Multiple HTML comments ────────────────────────────────────────────────

  test("multiple comments are all discarded") {
    val result = parse("<!--first--><p>text</p><!--second-->")
    assertEquals(result, List(
      TemplateNode.Element("p", Nil, List(TemplateNode.Text("text")))
    ))
  }

  test("comment between sibling elements is discarded") {
    val result = parse("<h1>Title</h1><!-- separator --><p>Body</p>")
    assertEquals(result, List(
      TemplateNode.Element("h1", Nil, List(TemplateNode.Text("Title"))),
      TemplateNode.Element("p",  Nil, List(TemplateNode.Text("Body")))
    ))
  }

  // ── Counter.melt integration test ─────────────────────────────────────────

  test("Counter.melt template parses correctly (Phase 2 acceptance test)") {
    val template =
      """<div class="counter">
        |  <h1>{props.label}</h1>
        |  {badge(internal.now().toString)}
        |  <button onclick={increment}>+1</button>
        |  <p>Doubled: {doubled}</p>
        |</div>""".stripMargin

    val result = parse(template)
    assertEquals(result.size, 1)

    val div = result.head.asInstanceOf[TemplateNode.Element]
    assertEquals(div.tag, "div")
    assertEquals(div.attrs, List(Attr.Static("class", "counter")))

    val children = div.children
    assertEquals(children.size, 4)

    // <h1>{props.label}</h1>
    val h1 = children(0).asInstanceOf[TemplateNode.Element]
    assertEquals(h1.tag, "h1")
    assertEquals(h1.children, List(TemplateNode.Expression("props.label")))

    // {badge(internal.now().toString)}
    assertEquals(children(1), TemplateNode.Expression("badge(internal.now().toString)"))

    // <button onclick={increment}>+1</button>
    val button = children(2).asInstanceOf[TemplateNode.Element]
    assertEquals(button.tag, "button")
    assertEquals(button.attrs, List(Attr.EventHandler("click", "increment")))
    assertEquals(button.children, List(TemplateNode.Text("+1")))

    // <p>Doubled: {doubled}</p>
    val p = children(3).asInstanceOf[TemplateNode.Element]
    assertEquals(p.tag, "p")
    assertEquals(p.children, List(
      TemplateNode.Text("Doubled: "),
      TemplateNode.Expression("doubled")
    ))
  }
