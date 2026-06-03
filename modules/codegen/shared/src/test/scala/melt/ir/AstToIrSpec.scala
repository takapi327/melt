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
      case List(IrNode.IrElement(_, _, _, List(IrNode.IrDynamicText(expr, _, _)), _)) =>
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
        assert(renderFn.code.startsWith("t =>"), s"renderFn = ${ renderFn.code }")
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
      case other                     => fail(s"Unexpected: $other")
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
      case other                                     => fail(s"Expected IrElement, got $other")
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
      case other                                                     => fail(s"Expected svg namespace, got $other")
  }

  // ── extractReactiveSource ──────────────────────────────────────────────────

  test("extractReactiveSource from `if flag.value then`") {
    AstToIr.extractReactiveSource(
      "if flag.value then dom.document.createElement(\"span\") else dom.document.createTextNode(\"\")"
    ) match
      case Some(src) => assertEquals(src.code, "flag")
      case None      => fail("Expected Some")
  }

  test("extractReactiveSource returns None for plain if without reactive pattern") {
    val result = AstToIr.extractReactiveSource(
      "if x > 0 then dom.document.createElement(\"span\") else dom.document.createTextNode(\"\")"
    )
    assertEquals(result, None)
  }

  // ── detectPropsType (Named Tuple Props auto-detection) ─────────────────────

  test("detectPropsType: case class Props — detected without props= attribute") {
    val src = """<script lang="scala">
                |case class Props(label: String, count: Int)
                |</script>
                |<p>{props.label}</p>""".stripMargin
    val ir = lower(src)
    assertEquals(ir.propsType.map(_.typeName), Some("Props"))
    assertEquals(ir.propsType.map(_.isNamedTuple), Some(false))
  }

  test("detectPropsType: inline Named Tuple Props") {
    val src = """<script lang="scala">
                |type Props = (label: String, count: Int)
                |</script>
                |<p>{props.label}</p>""".stripMargin
    val ir = lower(src)
    val pt = ir.propsType.getOrElse(fail("propsType expected"))
    assertEquals(pt.typeName, "Props")
    assertEquals(pt.baseName, "Props")
    assert(pt.isNamedTuple)
    assertEquals(pt.namedTupleFields, List("label" -> "String", "count" -> "Int"))
    assertEquals(pt.allHaveDefaults, false)
  }

  test("detectPropsType: generic Named Tuple Props") {
    val src = """<script lang="scala">
                |type Props[T] = (value: T, label: String)
                |</script>
                |<p>{props.label}</p>""".stripMargin
    val ir = lower(src)
    val pt = ir.propsType.getOrElse(fail("propsType expected"))
    assertEquals(pt.typeName, "Props[T]")
    assertEquals(pt.typeParams, "[T]")
    assert(pt.isNamedTuple)
    assertEquals(pt.namedTupleFields, List("value" -> "T", "label" -> "String"))
  }

  test("detectPropsType: Named Tuple alias in same script") {
    val src = """<script lang="scala">
                |type Hoge = (name: String, age: Int)
                |type Props = Hoge
                |</script>
                |<p>{props.name}</p>""".stripMargin
    val ir = lower(src)
    val pt = ir.propsType.getOrElse(fail("propsType expected"))
    assertEquals(pt.typeName, "Props")
    assertEquals(pt.baseName, "Hoge")
    assert(pt.isNamedTuple)
    assertEquals(pt.namedTupleFields, List("name" -> "String", "age" -> "Int"))
  }

  test("detectPropsType: case class alias in same script (baseName != Props)") {
    val src = """<script lang="scala">
                |case class HomeProps(user: String = "guest")
                |type Props = HomeProps
                |</script>
                |<p>{props.user}</p>""".stripMargin
    val ir = lower(src)
    val pt = ir.propsType.getOrElse(fail("propsType expected"))
    assertEquals(pt.typeName, "HomeProps")
    assertEquals(pt.baseName, "HomeProps")
    assert(!pt.isNamedTuple)
    // "type Props = HomeProps" must be filtered out from typeDecls (emitter re-generates it)
    assert(!ir.typeDecls.exists(_.trim.startsWith("type Props =")), s"typeDecls: ${ ir.typeDecls }")
  }

  test("detectPropsType: no Props type in script yields None") {
    val ir = lower("<div>hello</div>")
    assertEquals(ir.propsType, None)
  }

  test("collectBalanced fix: type Props = HomeProps does not absorb next typeDecl") {
    val src = """<script lang="scala">
                |type Props = HomeProps
                |case class Foo(x: Int)
                |</script>
                |<div/>""".stripMargin
    val ir = lower(src)
    // Both "type Props = HomeProps" and "case class Foo(x: Int)" must be in separate decls.
    // The Props alias is filtered from effectiveTypeDecls but Foo must remain.
    assert(ir.typeDecls.exists(_.trim.startsWith("case class Foo")), s"typeDecls: ${ ir.typeDecls }")
  }

  test("collectBalanced fix: case class Props with complex type params on one line") {
    val src =
      """<script lang="scala">
        |case class Props[T <: Ordered[T]](items: Seq[T])
        |</script>
        |<div/>""".stripMargin
    val ir = lower(src)
    val pt = ir.propsType.getOrElse(fail("propsType expected"))
    assertEquals(pt.typeParams, "[T <: Ordered[T]]")
    assertEquals(pt.typeName, "Props[T <: Ordered[T]]")
  }

  // ── module script / moduleBody ─────────────────────────────────────────────

  test("module script body is stored in IrComponent.moduleBody") {
    val src =
      """<script lang="scala" module>
        |val total = State(0)
        |def format(n: Int): String = s"#$n"
        |</script>
        |<p></p>""".stripMargin
    val ir = lower(src)
    assert(ir.moduleBody.contains("val total = State(0)"), ir.moduleBody)
    assert(ir.moduleBody.contains("def format"), ir.moduleBody)
  }

  test("moduleBody is empty string when no module script is present") {
    val ir = lower("<p>hello</p>")
    assertEquals(ir.moduleBody, "")
  }

  test("module script State var is included in reactiveVars") {
    val src =
      """<script lang="scala" module>
        |val count = State(0)
        |</script>
        |<p>{count}</p>""".stripMargin
    val ir = lower(src)
    assert(ir.reactiveVars.contains("count"), s"reactiveVars: ${ ir.reactiveVars }")
  }

  test("reactiveVars merges module and instance script vars") {
    val src =
      """<script lang="scala" module>
        |val moduleCount = State(0)
        |</script>
        |<script lang="scala">
        |val instanceCount = State(0)
        |</script>
        |<p></p>""".stripMargin
    val ir = lower(src)
    assert(ir.reactiveVars.contains("moduleCount"), s"reactiveVars: ${ ir.reactiveVars }")
    assert(ir.reactiveVars.contains("instanceCount"), s"reactiveVars: ${ ir.reactiveVars }")
  }

  test("fileImports: module imports come before instance imports") {
    val src =
      """<script lang="scala" module>
        |import "/module.css"
        |val x = 1
        |</script>
        |<script lang="scala">
        |import "/instance.css"
        |val y = 2
        |</script>
        |<p></p>""".stripMargin
    val ir = lower(src)
    assertEquals(ir.fileImports, List("/module.css", "/instance.css"))
  }
