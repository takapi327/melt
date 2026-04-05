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
