/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.ir

import melt.parser.MeltParser

/** Unit tests for [[AstToIr]].
  *
  * Tests assert directly on the IR structure, not on generated Scala code.
  * This makes tests immune to whitespace/indent changes in emitters.
  */
class AstToIrSpec extends munit.FunSuite:

  private def lower(src: String, name: String = "Test"): IrComponent =
    MeltParser.parse(src) match
      case Left(err)  => fail(s"Parse error: $err")
      case Right(ast) => AstToIr.lower(ast, name, "", s"$name-scope")

  // ── lowerExpression ────────────────────────────────────────────────────────

  test("plain text expression lowers to IrDynamicText") {
    val ir = lower("<div>{count}</div>")
    ir.template match
      case List(IrNode.IrElement(_, _, _, List(IrNode.IrDynamicText(expr)), _)) =>
        assertEquals(expr.code, "count")
      case other => fail(s"Unexpected: $other")
  }

  test("{children} lowers to IrChildren") {
    assertEquals(AstToIr.lowerExpression("children"), IrNode.IrChildren)
  }

  test("unkeyed list lowers to IrList") {
    // InlineTemplate (mixed Scala+HTML) is kept as IrInlineTemplate in Phase 1-3.
    // lowerExpression is called directly with a pre-built code string (as emitters do in Phase 4).
    val code = "todos.value.map(t => dom.document.createElement(\"li\"))"
    AstToIr.lowerExpression(code) match
      case IrNode.IrList(source, renderFn) =>
        assertEquals(source.code, "todos")
        assert(renderFn.code.startsWith("t =>"), s"renderFn = ${renderFn.code}")
      case other => fail(s"Unexpected: $other")
  }

  test("keyed list lowers to IrKeyedList") {
    val code = "todos.keyed(_.id).map(t => t.text)"
    AstToIr.lowerExpression(code) match
      case IrNode.IrKeyedList(source, keyFn, renderFn) =>
        assertEquals(source.code, "todos")
        assertEquals(keyFn.code, "_.id")
      case other => fail(s"Unexpected: $other")
  }

  test("TrustedHtml expression lowers to IrRawHtml") {
    val code = """TrustedHtml("<b>bold</b>")"""
    AstToIr.lowerExpression(code) match
      case IrNode.IrRawHtml(_, expr) => assertEquals(expr.code, code)
      case other => fail(s"Unexpected: $other")
  }

  test("conditional DOM expression lowers to IrConditional") {
    val code = "if show then dom.document.createElement(\"span\") else dom.document.createElement(\"div\")"
    AstToIr.lowerExpression(code) match
      case IrNode.IrConditional(_, condAndBody) =>
        assertEquals(condAndBody.code, code)
      case other => fail(s"Unexpected: $other")
  }

  // ── Static / dynamic element classification ───────────────────────────────

  test("element with only static attrs and text lowers to IrStaticElement") {
    val ir = lower("""<p class="info">Hello</p>""")
    ir.template match
      case List(IrNode.IrStaticElement("p", _, attrs, _, _)) =>
        assert(attrs.exists { case IrAttr.StaticAttr("class", "info") => true; case _ => false })
      case other => fail(s"Expected IrStaticElement, got $other")
  }

  test("element with dynamic attr lowers to IrElement") {
    val ir = lower("""<div class={cls}>text</div>""")
    ir.template match
      case List(IrNode.IrElement("div", _, _, _, _)) => ()
      case other => fail(s"Expected IrElement, got $other")
  }

  // ── Attr lowering ─────────────────────────────────────────────────────────

  test("static attr lowers to IrAttr.StaticAttr") {
    val ir = lower("""<input type="text" />""")
    ir.template match
      case List(IrNode.IrStaticElement(_, _, attrs, _, _)) =>
        assert(attrs.exists { case IrAttr.StaticAttr("type", "text") => true; case _ => false })
      case other => fail(s"Unexpected: $other")
  }

  test("boolean html attr (disabled) lowers to IrAttr.DynamicBooleanAttr") {
    val ir = lower("""<button disabled={isDisabled}>click</button>""")
    ir.template match
      case List(IrNode.IrElement(_, _, attrs, _, _)) =>
        assert(attrs.exists { case IrAttr.DynamicBooleanAttr("disabled", _) => true; case _ => false })
      case other => fail(s"Unexpected: $other")
  }

  test("class:active lowers to IrAttr.ClassToggle") {
    val ir = lower("""<div class:active={flag}>x</div>""")
    ir.template match
      case List(IrNode.IrElement(_, _, attrs, _, _)) =>
        assert(attrs.exists { case IrAttr.ClassToggle("active", _) => true; case _ => false })
      case other => fail(s"Unexpected: $other")
  }

  test("bind:value on input lowers to IrAttr.BindInputValue") {
    val ir = lower("""<input bind:value={v} />""")
    ir.template match
      case List(IrNode.IrElement(_, _, attrs, _, _)) =>
        assert(attrs.exists { case IrAttr.BindInputValue(_) => true; case _ => false })
      case other => fail(s"Unexpected: $other")
  }

  test("bind:value on textarea lowers to IrAttr.BindTextareaValue") {
    val ir = lower("""<textarea bind:value={v}></textarea>""")
    ir.template match
      case List(IrNode.IrElement(_, _, attrs, _, _)) =>
        assert(attrs.exists { case IrAttr.BindTextareaValue(_) => true; case _ => false })
      case other => fail(s"Unexpected: $other")
  }

  test("event handler lowers to IrAttr.EventHandler") {
    val ir = lower("""<button onclick={handler}>click</button>""")
    ir.template match
      case List(IrNode.IrElement(_, _, attrs, _, _)) =>
        assert(attrs.exists { case IrAttr.EventHandler("click", _) => true; case _ => false })
      case other => fail(s"Unexpected: $other")
  }

  // ── Component lowering ────────────────────────────────────────────────────

  test("component invocation lowers to IrNode.IrComponent") {
    val ir = lower("""<Counter count={n} />""")
    ir.template match
      case List(IrNode.IrComponent("Counter", props, None, None, false, None)) =>
        assert(props.exists(_.name == "count"))
      case other => fail(s"Unexpected: $other")
  }

  test("component with children lowers to IrNode.IrComponent with IrChildrenSlot") {
    val ir = lower("""<Layout><p>body</p></Layout>""")
    ir.template match
      case List(IrNode.IrComponent("Layout", _, Some(IrChildrenSlot(nodes)), None, false, None)) =>
        assert(nodes.nonEmpty)
      case other => fail(s"Unexpected: $other")
  }

  // ── Namespace ─────────────────────────────────────────────────────────────

  test("svg element carries ns = Some(\"svg\")") {
    val ir = lower("""<svg><circle r="10" /></svg>""")
    ir.template match
      case List(IrNode.IrStaticElement("svg", Some("svg"), _, _, _)) => ()
      case other => fail(s"Expected svg namespace, got $other")
  }

  // ── extractReactiveSource ──────────────────────────────────────────────────

  test("extractReactiveSource from `if flag.value then`") {
    AstToIr.extractReactiveSource("if flag.value then dom.document.createElement(\"span\") else dom.document.createTextNode(\"\")") match
      case Some(src) => assertEquals(src.code, "flag")
      case None => fail("Expected Some")
  }

  test("extractReactiveSource returns None for plain if without reactive pattern") {
    val result = AstToIr.extractReactiveSource("if x > 0 then dom.document.createElement(\"span\") else dom.document.createTextNode(\"\")")
    assertEquals(result, None)
  }
