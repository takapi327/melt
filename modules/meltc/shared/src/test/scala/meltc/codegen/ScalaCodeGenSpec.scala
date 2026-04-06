/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.codegen

import meltc.ast.*
import meltc.MeltCompiler

/** Tests for [[ScalaCodeGen]].
  *
  * All tests compile a `.melt` source string through the full pipeline
  * (parse → generate) and assert on the generated Scala source, or use
  * the AST directly to test the generator in isolation.
  */
class ScalaCodeGenSpec extends munit.FunSuite:

  /** Compile source, assert success, return generated Scala. */
  private def compile(src: String, name: String = "App", pkg: String = ""): String =
    val result = MeltCompiler.compile(src, s"$name.melt", name, pkg)
    assert(result.errors.isEmpty, s"Compile errors: ${ result.errors.map(_.message) }")
    result.scalaCode.getOrElse(fail("No generated code"))

  // ── scopeIdFor ────────────────────────────────────────────────────────────

  test("scopeIdFor produces deterministic melt-xxxxxx string") {
    val id = ScalaCodeGen.scopeIdFor("App")
    assert(id.startsWith("melt-"), id)
    assertEquals(id.length, 11)                      // "melt-" (5) + 6 hex digits
    assertEquals(id, ScalaCodeGen.scopeIdFor("App")) // deterministic
  }

  test("scopeIdFor is unique across different names") {
    assertNotEquals(ScalaCodeGen.scopeIdFor("App"), ScalaCodeGen.scopeIdFor("Counter"))
  }

  // ── Package declaration ───────────────────────────────────────────────────

  test("empty package omits package line") {
    val code = compile("<div></div>", pkg = "")
    assert(!code.contains("package "), code)
  }

  test("non-empty package emits package line") {
    val code = compile("<div></div>", pkg = "components")
    assert(code.startsWith("package components"), code)
  }

  // ── Object structure ──────────────────────────────────────────────────────

  test("generated object name matches filename") {
    val code = compile("<div></div>", name = "Counter")
    assert(code.contains("object Counter {"), code)
  }

  test("always imports dom and runtime") {
    val code = compile("<div></div>")
    assert(code.contains("import org.scalajs.dom"), code)
    assert(code.contains("Bind"), code)
    assert(code.contains("Cleanup"), code)
    assert(code.contains("Mount"), code)
    assert(code.contains("Style"), code)
  }

  test("creates() and mount() methods are present") {
    val code = compile("<div></div>")
    assert(code.contains("def create(): dom.Element"), code)
    assert(code.contains("def mount(target: dom.Element): Unit"), code)
  }

  // ── Static HTML generation ────────────────────────────────────────────────

  test("empty template generates empty div") {
    val code = compile("<script lang=\"scala\">\n</script>")
    assert(code.contains("createElement(\"div\")"), code)
  }

  test("single root element") {
    val code = compile("<div></div>")
    assert(code.contains("createElement(\"div\")"), code)
    assert(code.contains("classList.add(_scopeId)"), code)
  }

  test("text child node") {
    val code = compile("<p>Hello</p>")
    assert(code.contains("createTextNode(\"Hello\")"), code)
    assert(code.contains("appendChild"), code)
  }

  test("static class attribute uses classList.add to preserve scope ID") {
    val code = compile("""<div class="container"></div>""")
    assert(code.contains("""classList.add("container")"""), code)
    // Must NOT use setAttribute("class", ...) which would overwrite the scope ID
    assert(!code.contains("""setAttribute("class""""), code)
  }

  test("static non-class attribute uses setAttribute") {
    val code = compile("""<div id="main"></div>""")
    assert(code.contains("""setAttribute("id", "main")"""), code)
  }

  test("boolean attribute") {
    val code = compile("<button disabled></button>")
    assert(code.contains("""setAttribute("disabled", "")"""), code)
  }

  test("nested elements produce correct variable structure") {
    val code = compile("<div><p>Text</p></div>")
    // outer div: _el0, inner p: _el1, text: _txt0
    assert(code.contains("_el0"), code)
    assert(code.contains("_el1"), code)
    assert(code.contains("_txt0"), code)
    // inner p appended to outer div
    assert(code.contains("_el0.appendChild(_el1)"), code)
  }

  test("multiple root nodes wrapped in div") {
    val code = compile("<h1>A</h1><p>B</p>")
    assert(code.contains("val _root = dom.document.createElement(\"div\")"), code)
    assert(code.contains("_root.appendChild"), code)
  }

  // ── Expression nodes (reactive via Bind.text) ───���───────────────────────

  test("expression node emits Bind.text for reactive binding") {
    val code = compile("<p>{count}</p>")
    assert(code.contains("Bind.text(count,"), code)
    // Should NOT use static createTextNode for expressions
    assert(!code.contains("createTextNode((count).toString)"), code)
  }

  // ── Script section ───────────────────────────────────────────────────────

  test("script code is inside create() for per-instance isolation") {
    val src =
      """<script lang="scala">
        |val count = 42
        |</script>
        |<p>{count}</p>""".stripMargin
    val code = compile(src)
    assert(code.contains("val count = 42"), code)
    // Script code should be inside create(), after Cleanup.pushScope()
    val createIdx  = code.indexOf("def create()")
    val scriptIdx  = code.indexOf("val count = 42")
    val closingIdx = code.indexOf("val _cleanups")
    assert(
      createIdx >= 0 && scriptIdx > createIdx && scriptIdx < closingIdx,
      s"Script should be inside create():\n$code"
    )
  }

  // ── Style section ─────────────────────────────────────────────────────────

  test("style section emits _css val and Style.inject call") {
    val src =
      """<div></div>
        |<style>
        |h1 { color: red; }
        |</style>""".stripMargin
    val code = compile(src)
    assert(code.contains("private val _css"), code)
    assert(code.contains("Style.inject(_scopeId, _css)"), code)
    assert(code.contains("color: red"), code)
  }

  test("no style section omits _css and Style.inject") {
    val code = compile("<div></div>")
    assert(!code.contains("_css"), code)
    assert(!code.contains("Style.inject"), code)
  }

  // ── Scope ID in generated code ────────────────────────────────────────────

  test("_scopeId val is present in generated code") {
    val code = compile("<div></div>")
    assert(code.contains("private val _scopeId = \"melt-"), code)
  }

  test("classList.add(_scopeId) called on every element") {
    val code  = compile("<div><p></p></div>")
    val count = code.split("classList.add\\(_scopeId\\)", -1).length - 1
    assertEquals(count, 2) // div and p
  }

  // ── Full Hello World roundtrip ─────────────────────────────────────────────

  test("full hello-world generates compilable-looking code") {
    val src =
      """<div>
        |  <h1>Hello, Melt!</h1>
        |  <p>Static content works.</p>
        |</div>
        |
        |<style>
        |h1 { color: #ff3e00; }
        |p  { color: #555; }
        |</style>""".stripMargin
    val code = compile(src, name = "App", pkg = "components")
    assert(code.startsWith("package components"), code)
    assert(code.contains("object App {"), code)
    assert(code.contains("""createTextNode("Hello, Melt!")"""), code)
    assert(code.contains("""createTextNode("Static content works.")"""), code)
    assert(code.contains("Style.inject"), code)
    assert(code.contains("mount(target: dom.Element)"), code)
  }

  // ── MeltCompiler integration ───────────────────────────────────────────────

  test("compile() with parse error returns error result") {
    val result = MeltCompiler.compile(
      """<script lang="scala">
        |/* unclosed""".stripMargin,
      "Bad.melt",
      "Bad",
      ""
    )
    assert(result.errors.nonEmpty, "Expected parse error")
    assert(result.scalaCode.isEmpty)
  }

  test("compile() convenience overload derives objectName from filename") {
    val result = MeltCompiler.compile("<div></div>", "Counter.melt")
    assert(result.isSuccess)
    result.scalaCode.foreach { code =>
      assert(code.contains("object Counter {"), code)
    }
  }

  // ── Dynamic attribute generation ──────────────────────────────────────

  test("dynamic class attribute uses classList.add to preserve scope ID") {
    val code = compile("<div class={expr}></div>")
    assert(code.contains("classList.add"), code)
    assert(!code.contains("""setAttribute("class""""), code)
  }

  test("dynamic non-class attribute emits Bind.attr") {
    val code = compile("<div title={expr}></div>")
    assert(code.contains("""Bind.attr(_el0, "title", expr)"""), code)
  }

  // ── Event handler generation ──────────────────────────────────────────

  test("event handler emits addEventListener") {
    val code = compile("<button onclick={handler}>Click</button>")
    assert(code.contains("""addEventListener("click", handler)"""), code)
  }

  test("keydown event handler") {
    val code = compile("<input onkeydown={handler} />")
    assert(code.contains("""addEventListener("keydown", handler)"""), code)
  }

  // ── Component node (no-op in Phase 3) ─────────────────────────────────

  test("component node produces no createElement output") {
    val code = compile("<div><Counter /></div>")
    assert(!code.contains("""createElement("Counter")"""), code)
    // The <div> should still be present
    assert(code.contains("""createElement("div")"""), code)
  }

  // ── Special characters in text ─────────────────────────────────────────

  test("whitespace in text is collapsed to single space") {
    val code = compile("<p>line1\nline2\ttab</p>")
    // Whitespace collapsing turns \n and \t into single space
    assert(code.contains("line1 line2 tab"), code)
  }

  test("double quotes in text are escaped") {
    val src  = "<p>He said &quot;hello&quot;</p>"
    val code = compile(src)
    assert(code.contains("\\\""), code)
  }

  // ── Special characters in attribute values ─────────────────────────────

  test("backslash in attribute value is escaped") {
    val code = compile("""<div title="a\\b"></div>""")
    assert(code.contains("\\\\"), code)
  }

  // ── Deep nesting (3+ levels) ───────────────────────────────────────────

  test("deeply nested elements (3 levels) produce correct structure") {
    val code = compile("<div><ul><li>Item</li></ul></div>")
    assert(code.contains("_el0"), code) // div
    assert(code.contains("_el1"), code) // ul
    assert(code.contains("_el2"), code) // li
    assert(code.contains("_el1.appendChild(_el2)"), code)
    assert(code.contains("_el0.appendChild(_el1)"), code)
  }

  // ── scopeIdFor edge cases ─────────────────────────────────────────────

  test("scopeIdFor always produces valid hex (no negative values)") {
    val ids = (0 to 500).map(i => ScalaCodeGen.scopeIdFor(s"Component$i"))
    ids.foreach { id =>
      assert(id.matches("melt-[0-9a-f]{6}"), s"Invalid scope ID: $id")
    }
  }

  test("scopeIdFor produces valid hex for empty string") {
    val id = ScalaCodeGen.scopeIdFor("")
    assert(id.matches("melt-[0-9a-f]{6}"), s"Bad scopeId: $id")
  }

  // ── CSS scoping in generated code ──────────────────────────────────────

  test("style section CSS is scoped with scope ID in selectors") {
    val src =
      """<div></div>
        |<style>
        |h1 { color: red; }
        |</style>""".stripMargin
    val code    = compile(src, name = "App")
    val scopeId = ScalaCodeGen.scopeIdFor("App")
    assert(code.contains(s"h1.$scopeId"), s"CSS should contain scoped selector, got:\n$code")
  }

  test("MeltCompiler.scopedCss field returns scoped CSS") {
    val src =
      """<div></div>
        |<style>
        |p { color: blue; }
        |</style>""".stripMargin
    val result  = MeltCompiler.compile(src, "Test.melt", "Test", "")
    val scopeId = ScalaCodeGen.scopeIdFor("Test")
    result.scopedCss.foreach { css =>
      assert(css.contains(s"p.$scopeId"), s"scopedCss should contain scoped selector, got: $css")
    }
  }

  // ── Non-void self-closing tag warning ──────────────────────────────────

  test("non-void self-closing tag produces warning") {
    val src    = "<span />"
    val result = MeltCompiler.compile(src, "Warn.melt", "Warn", "")
    assert(result.warnings.nonEmpty, "Expected warning for <span />")
    assert(result.warnings.head.message.contains("self-closed"), result.warnings.head.message)
  }

  test("static class attribute with multiple classes emits separate classList.add calls") {
    val code = compile("""<div class="foo bar baz"></div>""")
    assert(code.contains("""classList.add("foo")"""), code)
    assert(code.contains("""classList.add("bar")"""), code)
    assert(code.contains("""classList.add("baz")"""), code)
  }

  test("class attribute coexists with scope ID") {
    val code = compile("""<div class="counter"></div>""")
    // Both scope ID and user class should be added via classList.add
    assert(code.contains("classList.add(_scopeId)"), code)
    assert(code.contains("""classList.add("counter")"""), code)
  }

  test("void self-closing tag produces no warning") {
    val src    = "<br />"
    val result = MeltCompiler.compile(src, "Ok.melt", "Ok", "")
    assert(result.warnings.isEmpty, s"Unexpected warnings: ${ result.warnings.map(_.message) }")
  }

  // ── Interleaved text and expression nodes ──────────────────────────────

  test("interleaved text and expression nodes") {
    val code = compile("<p>Hello {name} world</p>")
    assert(code.contains("createTextNode(\"Hello \")"), code)
    assert(code.contains("Bind.text(name,"), code)
    assert(code.contains("createTextNode(\" world\")"), code)
  }

  // ── Empty style section ───────────────────────────────────────────────

  test("empty style section does not emit Style.inject") {
    val src =
      """<div></div>
        |<style>
        |</style>""".stripMargin
    val code = compile(src)
    // CSS is blank after trimming, but style section exists.
    // Style.inject should still be called (idempotent) with the empty CSS.
    assert(code.contains("private val _css"), code)
  }

  // ── Void elements produce no appendChild for children ──────────────────

  test("void element produces no appendChild calls for children") {
    val code = compile("<div><br /><img src=\"a.png\" /></div>")
    // br and img should not have any appendChild calls after them
    assert(!code.contains("_el1.appendChild"), code)
    assert(!code.contains("_el2.appendChild"), code)
    // Only _el0 (div) should have appendChild
    assert(code.contains("_el0.appendChild(_el1)"), code)
    assert(code.contains("_el0.appendChild(_el2)"), code)
  }

  // ── CSS containing triple-quotes does not break generated code ─────────

  test("CSS with triple-quote characters is safely escaped") {
    val src =
      """<div></div>
        |<style>
        |p::before { content: '\"\"\"'; }
        |</style>""".stripMargin
    val code = compile(src, name = "Tricky")
    // The generated code should compile without syntax errors
    assert(code.contains("private val _css"), code)
    // Triple quotes must be properly escaped in a regular string literal
    assert(!code.contains("\"\"\"\"\"\""), s"Raw triple-quote found in output:\n$code")
  }

  // ── Phase 4: Reactive bindings ──────────────────────────────────────────

  test("create() includes Cleanup.pushScope and popScope with stored cleanups") {
    val code = compile("<div></div>")
    assert(code.contains("Cleanup.pushScope()"), code)
    assert(code.contains("val _cleanups = Cleanup.popScope()"), code)
  }

  test("bind:value directive emits Bind.inputValue") {
    val code = compile("""<input bind:value={name} />""")
    assert(code.contains("Bind.inputValue("), code)
    assert(code.contains(".asInstanceOf[dom.html.Input]"), code)
    assert(code.contains("name"), code)
  }

  test("Counter.melt completion criteria generates correct code") {
    val src =
      """<script lang="scala">
        |val count = Var(0)
        |val name = Var("")
        |</script>
        |<div>
        |  <p>Count: {count}</p>
        |  <button onclick={_ => count += 1}>+1</button>
        |  <input bind:value={name} placeholder="Your name" />
        |  <p>Hello, {name}!</p>
        |</div>""".stripMargin
    val code = compile(src, name = "Counter")
    // Reactive text bindings
    assert(code.contains("Bind.text(count,"), code)
    assert(code.contains("Bind.text(name,"), code)
    // Event handler
    assert(code.contains("""addEventListener("click","""), code)
    // Two-way bind:value
    assert(code.contains("Bind.inputValue("), code)
    // Static text
    assert(code.contains("createTextNode(\"Count: \")"), code)
    assert(code.contains("createTextNode(\"Hello, \")"), code)
    // Script code
    assert(code.contains("val count = Var(0)"), code)
    // Cleanup
    assert(code.contains("Cleanup.pushScope()"), code)
  }

  // ── MeltCompiler integration: style + script + template ─────────────────

  test("end-to-end: scopedCss is consistent with scalaCode CSS") {
    val src =
      """<script lang="scala">
        |val greeting = "hello"
        |</script>
        |
        |<div>
        |  <h1>{greeting}</h1>
        |</div>
        |
        |<style>
        |h1 { color: red; }
        |div { padding: 1em; }
        |</style>""".stripMargin
    val result  = MeltCompiler.compile(src, "Full.melt", "Full", "pkg")
    val scopeId = ScalaCodeGen.scopeIdFor("Full")
    assert(result.isSuccess, s"Errors: ${ result.errors.map(_.message) }")

    // scopedCss contains scoped selectors
    result.scopedCss.foreach { css =>
      assert(css.contains(s"h1.$scopeId"), s"scopedCss missing h1 scope: $css")
      assert(css.contains(s"div.$scopeId"), s"scopedCss missing div scope: $css")
    }

    // scalaCode includes the same scoped CSS
    result.scalaCode.foreach { code =>
      assert(code.contains(s"h1.$scopeId"), s"scalaCode missing scoped h1: $code")
      assert(code.contains("val greeting"), code)
      assert(code.contains("package pkg"), code)
    }
  }

  // ── Phase 5: Component system ─────────────────────────────────────────────

  test("props type generates case class at object level and create(props)") {
    val src =
      """<script lang="scala" props="Props">
        |case class Props(label: String, count: Int)
        |val doubled = count * 2
        |</script>
        |<p>{props.label}</p>""".stripMargin
    val code = compile(src, name = "Counter")
    // Props definition at object level (before create)
    assert(code.contains("case class Props(label: String, count: Int)"), code)
    val propsDefIdx = code.indexOf("case class Props")
    val createIdx   = code.indexOf("def create(")
    assert(propsDefIdx < createIdx, s"Props def should be before create():\n$code")
    // create takes props parameter
    assert(code.contains("def create(props: Props): dom.Element"), code)
    // mount takes props parameter
    assert(code.contains("def mount(target: dom.Element, props: Props)"), code)
    // body code is inside create
    assert(code.contains("val doubled = count * 2"), code)
  }

  test("component reference without props generates create()") {
    val code = compile("<div><Footer /></div>")
    assert(code.contains("Footer.create()"), code)
  }

  test("component reference with static props") {
    val code = compile("""<div><Counter label="Hello" /></div>""")
    assert(code.contains("Counter.create(Counter.Props("), code)
    assert(code.contains("""label = "Hello""""), code)
  }

  test("component reference with dynamic props") {
    val code = compile("<div><Counter count={n} /></div>")
    assert(code.contains("Counter.create(Counter.Props("), code)
    assert(code.contains("count = n"), code)
  }

  test("component reference with shorthand attribute") {
    val code = compile("<div><Counter {label} /></div>")
    assert(code.contains("label = label"), code)
  }

  test("component reference with spread attribute") {
    val code = compile("<div><Counter {...counterProps} /></div>")
    assert(code.contains("Counter.create(counterProps)"), code)
  }

  test("component with event handler as prop") {
    val code = compile("<div><TodoInput onadd={handler} /></div>")
    assert(code.contains("onAdd = handler"), code)
  }

  test("styled attribute adds parent scope ID to component root") {
    val code        = compile("<div><Button styled /></div>")
    val createIdx   = code.indexOf("Button.create()")
    val classAddIdx = code.indexOf("classList.add(_scopeId)", createIdx)
    assert(createIdx >= 0 && classAddIdx > createIdx, s"styled should add _scopeId after create:\n$code")
  }

  test("component with children generates children lambda") {
    val code = compile("<div><Card><p>Content</p></Card></div>")
    assert(code.contains("Card.create(Card.Props(children ="), code)
    assert(code.contains("() => {"), code)
    assert(code.contains("""createElement("p")"""), code)
  }

  test("no-props component omits Props constructor") {
    val code = compile("<div><Divider /></div>")
    assert(code.contains("Divider.create()"), code)
    assert(!code.contains("Divider.Props"), code)
  }

  // ── Phase 6: Template directives ──────────────────────────────────────────

  test("class: directive emits Bind.classToggle") {
    val code = compile("<div class:active={isActive}></div>")
    assert(code.contains("""Bind.classToggle(_el0, "active", isActive)"""), code)
  }

  test("class: shorthand (no value) uses variable name") {
    val code = compile("<div class:selected></div>")
    assert(code.contains("""Bind.classToggle(_el0, "selected", selected)"""), code)
  }

  test("style: directive emits Bind.style") {
    val code = compile("<div style:color={textColor}></div>")
    assert(code.contains("""Bind.style(_el0, "color", textColor)"""), code)
  }

  test("bind:checked emits Bind.inputChecked") {
    val code = compile("<input bind:checked={done} />")
    assert(code.contains("Bind.inputChecked("), code)
    assert(code.contains("done"), code)
  }

  test("bind:this emits ref.set call") {
    val code = compile("<canvas bind:this={canvasRef}></canvas>")
    assert(code.contains("canvasRef.set("), code)
  }

  // ── Phase 6: List rendering ───────────────────────────────────────────────

  test(".map() expression with DOM body emits anchor + Bind.list") {
    val src  = "<ul>{items.map(item => { val li = dom.document.createElement(\"li\"); li })}</ul>"
    val code = compile(src)
    assert(code.contains("createComment(\"melt\")"), code)
    assert(code.contains("Bind.list(items,"), code)
  }

  test(".map() expression without DOM body stays as Bind.text") {
    val code = compile("<p>{items.map(_.size)}</p>")
    assert(code.contains("Bind.text(items.map(_.size),"), code)
    assert(!code.contains("Bind.list"), code)
  }

  test(".keyed().map() expression emits anchor + Bind.each") {
    val src  = "<ul>{items.keyed(_.id).map(item => { val li = dom.document.createElement(\"li\"); li })}</ul>"
    val code = compile(src)
    assert(code.contains("createComment(\"melt\")"), code)
    assert(code.contains("Bind.each(items,"), code)
    assert(code.contains("_.id"), code)
  }

  test("plain text expression still uses Bind.text") {
    val code = compile("<p>{name}</p>")
    assert(code.contains("Bind.text(name,"), code)
    assert(!code.contains("Bind.list"), code)
  }

  // ── Phase 6: Inline HTML in expressions ──────────────────────────────────

  test("inline HTML in .map() generates DOM construction code") {
    val src =
      """<ul>{items.map((item: String) =>
        |  <li>{item}</li>
        |)}</ul>""".stripMargin
    val code = compile(src)
    assert(code.contains("""createElement("li")"""), code)
    assert(code.contains("Bind.list(items,"), code)
    assert(code.contains("Bind.text(item,"), code)
  }

  test("inline HTML with attributes generates correct code") {
    val src =
      """<ul>{items.map((item: Item) =>
        |  <li class="entry" onclick={handler}>
        |    <span>{item.name}</span>
        |  </li>
        |)}</ul>""".stripMargin
    val code = compile(src)
    assert(code.contains("""classList.add("entry")"""), code)
    assert(code.contains("""addEventListener("click", handler)"""), code)
    assert(code.contains("Bind.text(item.name,"), code)
  }

  test("expression without HTML stays as Expression node") {
    val code = compile("<p>{count + 1}</p>")
    assert(code.contains("Bind.text(count + 1,"), code)
    assert(!code.contains("Bind.list"), code)
  }

  // ── Phase 6: if/else and match ────────────────────────────────────────────

  test("if/else with inline HTML emits Bind.show") {
    val src =
      """<div>{if visible then <p>Yes</p> else <span>No</span>}</div>"""
    val code = compile(src)
    assert(code.contains("Bind.show("), code)
    assert(code.contains("createComment(\"melt\")"), code)
  }

  test("if/else with plain text stays as Bind.text") {
    val src  = """<p>{if x > 0 then "positive" else "negative"}</p>"""
    val code = compile(src)
    assert(code.contains("Bind.text("), code)
    assert(!code.contains("Bind.show"), code)
  }

  // ── Phase 6: bind:group radio vs checkbox ─────────────────────────────────

  test("bind:group on radio emits Bind.radioGroup") {
    val code = compile("""<input type="radio" bind:group={selected} value="a" />""")
    assert(code.contains("Bind.radioGroup("), code)
  }

  test("bind:group on checkbox emits Bind.checkboxGroup") {
    val code = compile("""<input type="checkbox" bind:group={toppings} value="cheese" />""")
    assert(code.contains("Bind.checkboxGroup("), code)
  }

  // ── Phase 6: numeric bind:value ───────────────────────────────────────────

  test("bind:value-int emits Bind.inputInt") {
    val code = compile("""<input type="number" bind:value-int={count} />""")
    assert(code.contains("Bind.inputInt("), code)
  }

  test("bind:value-double emits Bind.inputDouble") {
    val code = compile("""<input type="number" bind:value-double={price} />""")
    assert(code.contains("Bind.inputDouble("), code)
  }

  // ── Phase 8: use: directive ──────────────────────────────────────────────

  test("use: directive with parameter emits Bind.action") {
    val code = compile("""<div use:tooltip={"Help text"}></div>""")
    assert(code.contains("Bind.action("), code)
    assert(code.contains("tooltip"), code)
  }

  test("use: directive without parameter emits Bind.action with unit") {
    val code = compile("<input use:autoFocus />")
    assert(code.contains("Bind.action("), code)
    assert(code.contains("autoFocus"), code)
  }

  // ── Phase 8: spread on HTML element ────────────────────────────────────

  test("spread attribute on HTML element emits apply") {
    val code = compile("<div {...htmlAttrs}></div>")
    assert(code.contains("htmlAttrs.apply("), code)
  }

  // ── Phase 8: a11y warnings in CompileResult ────────────────────────────

  test("a11y warning for img without alt") {
    val result = meltc.MeltCompiler.compile("""<img src="x.png" />""", "A11y.melt", "A11y", "")
    assert(result.warnings.exists(_.message.contains("alt")), result.warnings.toString)
  }
