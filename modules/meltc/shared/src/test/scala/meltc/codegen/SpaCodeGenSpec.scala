/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.codegen

import meltc.ast.*
import meltc.MeltCompiler

/** Tests for [[SpaCodeGen]].
  *
  * All tests compile a `.melt` source string through the full pipeline
  * (parse → generate) and assert on the generated Scala source, or use
  * the AST directly to test the generator in isolation.
  */
class SpaCodeGenSpec extends munit.FunSuite:

  /** Compile source, assert success, return generated Scala. */
  private def compile(src: String, name: String = "App", pkg: String = ""): String =
    val result = MeltCompiler.compile(src, s"$name.melt", name, pkg)
    assert(result.errors.isEmpty, s"Compile errors: ${ result.errors.map(_.message) }")
    result.scalaCode.getOrElse(fail("No generated code"))

  /** Same as [[compile]] but with the hydration flag enabled so that
    * `@JSExportTopLevel("hydrate", moduleID = ...)` entries are emitted.
    */
  private def compileHydrate(src: String, name: String = "App", pkg: String = ""): String =
    val result =
      MeltCompiler.compile(src, s"$name.melt", name, pkg, hydration = true)
    assert(result.errors.isEmpty, s"Compile errors: ${ result.errors.map(_.message) }")
    result.scalaCode.getOrElse(fail("No generated code"))

  // ── scopeIdFor ────────────────────────────────────────────────────────────

  test("scopeIdFor produces deterministic melt-xxxxxxxx string") {
    val id = SpaCodeGen.scopeIdFor("App")
    assert(id.startsWith("melt-"), id)
    assertEquals(id.length, 13)                    // "melt-" (5) + 8 hex digits
    assertEquals(id, SpaCodeGen.scopeIdFor("App")) // deterministic
  }

  test("scopeIdFor is unique across different names") {
    assertNotEquals(SpaCodeGen.scopeIdFor("App"), SpaCodeGen.scopeIdFor("Counter"))
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

  test("apply() and mount() methods are present") {
    val code = compile("<div></div>")
    assert(code.contains("def apply(): dom.Element"), code)
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
    // Script code should be inside create(), inside Owner.withNew { ... }
    val createIdx  = code.indexOf("def apply()")
    val scriptIdx  = code.indexOf("val count = 42")
    val closingIdx = code.indexOf("Lifecycle.register")
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
    val ids = (0 to 500).map(i => SpaCodeGen.scopeIdFor(s"Component$i"))
    ids.foreach { id =>
      assert(id.matches("melt-[0-9a-f]{8}"), s"Invalid scope ID: $id")
    }
  }

  test("scopeIdFor produces valid hex for empty string") {
    val id = SpaCodeGen.scopeIdFor("")
    assert(id.matches("melt-[0-9a-f]{8}"), s"Bad scopeId: $id")
  }

  // ── S-2: scopeIdFor uses file path to prevent same-name collisions ────

  test("scopeIdFor with same name but different file paths produces different IDs (S-2)") {
    val id1 = SpaCodeGen.scopeIdFor("Button", "src/ui/Button.melt")
    val id2 = SpaCodeGen.scopeIdFor("Button", "src/form/Button.melt")
    assertNotEquals(id1, id2)
  }

  test("scopeIdFor with file path is deterministic (S-2)") {
    val id = SpaCodeGen.scopeIdFor("Counter", "src/Counter.melt")
    assertEquals(id, SpaCodeGen.scopeIdFor("Counter", "src/Counter.melt"))
  }

  test("scopeIdFor produces 8 hex digits with file path (S-2)") {
    val id = SpaCodeGen.scopeIdFor("App", "src/App.melt")
    assert(id.matches("melt-[0-9a-f]{8}"), s"Invalid scope ID: $id")
  }

  // ── CSS scoping in generated code ──────────────────────────────────────

  test("style section CSS is scoped with scope ID in selectors") {
    val src =
      """<div></div>
        |<style>
        |h1 { color: red; }
        |</style>""".stripMargin
    val code    = compile(src, name = "App")
    val scopeId = SpaCodeGen.scopeIdFor("App", "App.melt")
    assert(code.contains(s"h1.$scopeId"), s"CSS should contain scoped selector, got:\n$code")
  }

  test("MeltCompiler.scopedCss field returns scoped CSS") {
    val src =
      """<div></div>
        |<style>
        |p { color: blue; }
        |</style>""".stripMargin
    val result  = MeltCompiler.compile(src, "Test.melt", "Test", "")
    val scopeId = SpaCodeGen.scopeIdFor("Test", "Test.melt")
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

  test("apply() wraps body in Owner.withNew and registers with Lifecycle") {
    val code = compile("<div></div>")
    assert(code.contains("Owner.withNew"), code)
    assert(code.contains("Lifecycle.register"), code)
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
    // Owner-based lifecycle
    assert(code.contains("Owner.withNew"), code)
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
    val scopeId = SpaCodeGen.scopeIdFor("Full", "Full.melt")
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
    val createIdx   = code.indexOf("def apply(")
    assert(propsDefIdx < createIdx, s"Props def should be before create():\n$code")
    // create takes props parameter
    assert(code.contains("def apply(props: Props): dom.Element"), code)
    // mount takes props parameter
    assert(code.contains("def mount(target: dom.Element, props: Props)"), code)
    // body code is inside create
    assert(code.contains("val doubled = count * 2"), code)
  }

  test("component reference without props generates create()") {
    val code = compile("<div><Footer /></div>")
    assert(code.contains("Footer()"), code)
  }

  test("component reference with static props") {
    val code = compile("""<div><Counter label="Hello" /></div>""")
    assert(code.contains("Counter(Counter.Props("), code)
    assert(code.contains("""label = "Hello""""), code)
  }

  test("component reference with dynamic props") {
    val code = compile("<div><Counter count={n} /></div>")
    assert(code.contains("Counter(Counter.Props("), code)
    assert(code.contains("count = n"), code)
  }

  test("component reference with shorthand attribute") {
    val code = compile("<div><Counter {label} /></div>")
    assert(code.contains("label = label"), code)
  }

  test("component reference with spread attribute") {
    val code = compile("<div><Counter {...counterProps} /></div>")
    assert(code.contains("Counter(counterProps)"), code)
  }

  test("component with event handler as prop") {
    val code = compile("<div><TodoInput onadd={handler} /></div>")
    assert(code.contains("onAdd = handler"), code)
  }

  test("styled attribute adds parent scope ID to component root") {
    val code        = compile("<div><Button styled /></div>")
    val applyIdx    = code.indexOf("Button()")
    val classAddIdx = code.indexOf("classList.add(_scopeId)", applyIdx)
    assert(applyIdx >= 0 && classAddIdx > applyIdx, s"styled should add _scopeId after apply:\n$code")
  }

  test("component with children generates children lambda") {
    val code = compile("<div><Card><p>Content</p></Card></div>")
    assert(code.contains("Card(Card.Props(children ="), code)
    assert(code.contains("() => {"), code)
    assert(code.contains("""createElement("p")"""), code)
  }

  test("no-props component omits Props constructor") {
    val code = compile("<div><Divider /></div>")
    assert(code.contains("Divider()"), code)
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

  // ── Phase 8: HtmlProps code generation ─────────────────────────────────

  test("component with HtmlProps spread generates withHtml chain") {
    val src =
      """<script lang="scala" props="Props">
        |case class Props(label: String) extends ButtonHtmlProps
        |</script>
        |<button {...props.html}>{props.label}</button>""".stripMargin
    val code = compile(src, name = "MyButton")
    // Props definition at object level extends ButtonHtmlProps
    assert(code.contains("extends ButtonHtmlProps"), code)
    // Spread on button element applies html attrs
    assert(code.contains("props.html.apply("), code)
  }

  test("component with allHtmlAttrs spread") {
    val src =
      """<script lang="scala" props="Props">
        |case class Props(text: String) extends InputHtmlProps
        |</script>
        |<input {...props.allHtmlAttrs} />""".stripMargin
    val code = compile(src, name = "MyInput")
    assert(code.contains("props.allHtmlAttrs.apply("), code)
  }

  test("component caller passes HtmlProps fields as named args") {
    val code = compile("""<div><MyButton label="Click" disabled={true} /></div>""")
    assert(code.contains("MyButton(MyButton.Props("), code)
    assert(code.contains("""label = "Click""""), code)
    assert(code.contains("disabled = true"), code)
  }

  test("component caller passes withHtml for html attrs") {
    // data-* and aria-* attributes should pass through as regular props
    val code = compile("""<div><MyInput text="hi" id="inp" /></div>""")
    assert(code.contains("MyInput(MyInput.Props("), code)
    assert(code.contains("""text = "hi""""), code)
    assert(code.contains("""id = "inp""""), code)
  }

  test("button component with HtmlProps spread applies to button tag") {
    val src =
      """<script lang="scala" props="Props">
        |case class Props(label: String) extends ButtonHtmlProps
        |</script>
        |<button {...props.allHtmlAttrs}>{props.label}</button>""".stripMargin
    val code = compile(src, name = "MeltButton")
    assert(code.contains("""createElement("button")"""), code)
    assert(code.contains("props.allHtmlAttrs.apply("), code)
  }

  test("input component with HtmlProps spread applies to input tag") {
    val src =
      """<script lang="scala" props="Props">
        |case class Props(label: String) extends InputHtmlProps
        |</script>
        |<div><label>{props.label}</label><input {...props.allHtmlAttrs} /></div>""".stripMargin
    val code = compile(src, name = "MeltInput")
    assert(code.contains("""createElement("input")"""), code)
    assert(code.contains("props.allHtmlAttrs.apply("), code)
  }

  test("anchor component with HtmlProps spread applies to a tag") {
    val src =
      """<script lang="scala" props="Props">
        |case class Props(text: String) extends AnchorHtmlProps
        |</script>
        |<a {...props.allHtmlAttrs}>{props.text}</a>""".stripMargin
    val code = compile(src, name = "MeltLink")
    assert(code.contains("""createElement("a")"""), code)
    assert(code.contains("props.allHtmlAttrs.apply("), code)
  }

  test("img component with HtmlProps spread applies to img tag") {
    val src =
      """<script lang="scala" props="Props">
        |case class Props(caption: String) extends ImgHtmlProps
        |</script>
        |<figure><img {...props.allHtmlAttrs} /><figcaption>{props.caption}</figcaption></figure>""".stripMargin
    val code = compile(src, name = "MeltImage")
    assert(code.contains("""createElement("img")"""), code)
    assert(code.contains("props.allHtmlAttrs.apply("), code)
  }

  test("form component with HtmlProps spread applies to form tag") {
    val src =
      """<script lang="scala" props="Props">
        |case class Props(children: () => dom.Element) extends FormHtmlProps
        |</script>
        |<form {...props.allHtmlAttrs}></form>""".stripMargin
    val code = compile(src, name = "MeltForm")
    assert(code.contains("""createElement("form")"""), code)
    assert(code.contains("props.allHtmlAttrs.apply("), code)
  }

  test("textarea component with HtmlProps spread applies to textarea tag") {
    val src =
      """<script lang="scala" props="Props">
        |case class Props(label: String) extends TextAreaHtmlProps
        |</script>
        |<div><label>{props.label}</label><textarea {...props.allHtmlAttrs}></textarea></div>""".stripMargin
    val code = compile(src, name = "MeltTextArea")
    assert(code.contains("""createElement("textarea")"""), code)
    assert(code.contains("props.allHtmlAttrs.apply("), code)
  }

  test("select component with HtmlProps spread applies to select tag") {
    val src =
      """<script lang="scala" props="Props">
        |case class Props(label: String) extends SelectHtmlProps
        |</script>
        |<div><label>{props.label}</label><select {...props.allHtmlAttrs}></select></div>""".stripMargin
    val code = compile(src, name = "MeltSelect")
    assert(code.contains("""createElement("select")"""), code)
    assert(code.contains("props.allHtmlAttrs.apply("), code)
  }

  // ── Type-safety of generated code ──────────────────────────────────────────
  //
  // The Scala compiler is the final type-checker for generated code.  For each
  // directive the codegen must emit code that allows the Scala compiler to
  // reject wrong-typed arguments at compile time rather than failing at runtime.
  //
  // Convention:
  //   - Tests whose name starts with "TYPE-SAFE:" verify that the generated
  //     code does NOT bypass Scala's type system (e.g. no asInstanceOf wrapping
  //     where the Scala API already enforces types).
  //   - Tests whose name starts with "TYPE-ASCRIPTION:" verify that the generated
  //     code contains an explicit type ascription so that the Scala compiler can
  //     catch wrong types written by the user.

  // ── animate: fn type ascription ──────────────────────────────────────────

  test("TYPE-ASCRIPTION: animate:flip emits (Flip: AnimateFn) so non-AnimateFn values are rejected") {
    // Without `(Flip: AnimateFn)`, any value — a String, Int, or arbitrary object —
    // is accepted by `asInstanceOf[js.Any]` and stored silently.  The error surfaces
    // only at runtime when Bind.each tries to call the fn.
    val src  = """<ul>{items.keyed(_.id).map(_ => <li animate:flip></li>)}</ul>"""
    val code = compile(src)
    assert(
      code.contains("(Flip: AnimateFn)"),
      s"Expected type ascription '(Flip: AnimateFn)' in generated code, but got:\n$code"
    )
  }

  test("TYPE-ASCRIPTION: animate:customFn emits (Bounce: AnimateFn) for user-defined functions") {
    // The capitalized function name must also carry the AnimateFn ascription so
    // that the Scala compiler rejects any identifier that does not implement AnimateFn.
    val src  = """<li animate:bounce></li>"""
    val code = compile(src)
    assert(
      code.contains("(Bounce: AnimateFn)"),
      s"Expected type ascription '(Bounce: AnimateFn)' in generated code, but got:\n$code"
    )
  }

  // ── animate: params type ascription ──────────────────────────────────────

  test("TYPE-ASCRIPTION: animate: default params emits (AnimateParams(): AnimateParams)") {
    // When no expression is provided, the default `AnimateParams()` must still
    // carry a type ascription so the Scala compiler enforces the correct type.
    val src  = """<li animate:flip></li>"""
    val code = compile(src)
    assert(
      code.contains(": AnimateParams)"),
      s"Expected type ascription ': AnimateParams)' in default params, but got:\n$code"
    )
  }

  test("TYPE-ASCRIPTION: animate: explicit params emits type ascription preventing wrong param types") {
    // If a user writes `animate:flip={TransitionParams()}` (wrong type), the Scala
    // compiler must reject it.  The ascription `($params: AnimateParams)` achieves this.
    val src  = """<li animate:flip={AnimateParams(duration = 500)}></li>"""
    val code = compile(src)
    assert(
      code.contains(": AnimateParams)"),
      s"Expected type ascription ': AnimateParams)' in explicit params, but got:\n$code"
    )
  }

  // ── transition: / in: / out: — verify no asInstanceOf bypass ────────────

  test("TYPE-SAFE: transition: params passed directly to TransitionBridge without asInstanceOf") {
    // TransitionBridge.setBoth(el, t: Transition, params: TransitionParams) already
    // enforces types.  The generated code must NOT wrap params in asInstanceOf[js.Any],
    // which would defeat the type check.
    val src  = """<div transition:fly={TransitionParams(y = 100)}></div>"""
    val code = compile(src)
    assert(code.contains("TransitionBridge.setBoth("), code)
    assert(
      !code.contains("TransitionParams(y = 100)).asInstanceOf"),
      s"transition: params must NOT be wrapped in asInstanceOf (bypasses type check):\n$code"
    )
  }

  test("TYPE-SAFE: in: and out: params passed directly to TransitionBridge without asInstanceOf") {
    val src  = """<div in:fly={TransitionParams(x = 50)} out:fade={TransitionParams(duration = 200)}></div>"""
    val code = compile(src)
    assert(code.contains("TransitionBridge.setIn("), code)
    assert(code.contains("TransitionBridge.setOut("), code)
    assert(
      !code.contains("TransitionParams(x = 50)).asInstanceOf"),
      s"in: params must NOT be wrapped in asInstanceOf:\n$code"
    )
    assert(
      !code.contains("TransitionParams(duration = 200)).asInstanceOf"),
      s"out: params must NOT be wrapped in asInstanceOf:\n$code"
    )
  }

  // ── bind: directives — verify no asInstanceOf bypass ────────────────────

  test("TYPE-SAFE: bind:value passes Var directly to Bind.inputValue — Var[String] enforced by Scala") {
    // Bind.inputValue(input: dom.html.Input, v: Var[String]) requires Var[String].
    // Passing Var[Int] causes a Scala compile error.  The generated code must not cast.
    val src  = """<input bind:value={myInput} />"""
    val code = compile(src)
    assert(code.contains("Bind.inputValue("), code)
    assert(
      !code.contains("myInput.asInstanceOf"),
      s"bind:value must not cast the Var argument:\n$code"
    )
  }

  test("TYPE-SAFE: bind:value-int passes Var directly to Bind.inputInt — Var[Int] enforced") {
    val src  = """<input bind:value-int={count} />"""
    val code = compile(src)
    assert(code.contains("Bind.inputInt("), code)
    assert(!code.contains("count.asInstanceOf"), s"bind:value-int must not cast:\n$code")
  }

  test("TYPE-SAFE: bind:checked passes Var directly to Bind.inputChecked — Var[Boolean] enforced") {
    val src  = """<input type="checkbox" bind:checked={isOn} />"""
    val code = compile(src)
    assert(code.contains("Bind.inputChecked("), code)
    assert(!code.contains("isOn.asInstanceOf"), s"bind:checked must not cast:\n$code")
  }

  // ── use: directive — verify no asInstanceOf bypass ───────────────────────

  test("TYPE-SAFE: use: passes action and param directly to Bind.action — generics enforced by Scala") {
    // Bind.action[P](el, act: Action[P], param: P) — the Scala compiler enforces
    // that act is Action[P] and param is P.  The generated code must not cast.
    val src  = """<div use:tooltip={"Help text"}></div>"""
    val code = compile(src)
    assert(code.contains("Bind.action("), code)
    assert(!code.contains("""tooltip.asInstanceOf"""), s"use: must not cast action:\n$code")
  }

  test("TYPE-SAFE: use: without params passes action directly — Action[Unit] enforced") {
    val src  = """<div use:autoFocus></div>"""
    val code = compile(src)
    assert(code.contains("Bind.action("), code)
    assert(!code.contains("autoFocus.asInstanceOf"), s"use: must not cast action:\n$code")
  }

  // ── class: directive — verify no asInstanceOf bypass ─────────────────────

  test("TYPE-SAFE: class: passes expression directly to Bind.classToggle — Boolean/Var[Boolean] enforced") {
    // classToggle overloads accept Var[Boolean], Signal[Boolean], or Boolean only.
    // Passing a String or Int causes a Scala compile error.
    val src  = """<div class:active={isActive}></div>"""
    val code = compile(src)
    assert(code.contains("""Bind.classToggle(_el0, "active", isActive)"""), code)
    assert(!code.contains("isActive.asInstanceOf"), s"class: must not cast expression:\n$code")
  }

  // ── event handlers — verify no asInstanceOf bypass ───────────────────────

  test("TYPE-SAFE: event handler passed directly to addEventListener — type enforced by DOM API") {
    // addEventListener("click", handler) requires handler: js.Function1[dom.Event, _].
    // Passing a non-function causes a Scala compile error.
    val src  = """<button onclick={handler}></button>"""
    val code = compile(src)
    assert(code.contains("""addEventListener("click", handler)"""), code)
    assert(!code.contains("handler.asInstanceOf"), s"onclick must not cast handler:\n$code")
  }

  // ── Phase 14: Special elements (<melt:*>) ────────────────────────────────

  test("<melt:head> with static title emits Head.appendChild") {
    val code = compile("<melt:head><title>My App</title></melt:head>")
    assert(code.contains("Head.appendChild("), code)
    assert(code.contains("""createElement("title")"""), code)
    assert(code.contains("""createTextNode("My App")"""), code)
  }

  test("<melt:head> with reactive title emits Bind.text inside Head.appendChild") {
    val src  = "<melt:head><title>{pageTitle}</title></melt:head>"
    val code = compile(src)
    assert(code.contains("Head.appendChild("), code)
    assert(code.contains("Bind.text(pageTitle,"), code)
  }

  test("<melt:head> does not produce a root element itself") {
    // Head children go to document.head — the component result should not include them
    val src  = "<melt:head><title>T</title></melt:head><div>content</div>"
    val code = compile(src)
    assert(code.contains("Head.appendChild("), code)
    // The <div> should be the _result
    assert(code.contains("""createElement("div")"""), code)
  }

  test("<melt:window> with event handler emits Window.on") {
    val code = compile("<melt:window onresize={handleResize} />")
    assert(code.contains("""Window.on("resize")(handleResize)"""), code)
  }

  test("<melt:window> with keydown handler emits Window.on") {
    val code = compile("<melt:window onkeydown={handleKey} />")
    assert(code.contains("""Window.on("keydown")(handleKey)"""), code)
  }

  test("<melt:window> with bind:scrollY emits Window.bindScrollY") {
    val code = compile("<melt:window bind:scrollY={y} />")
    assert(code.contains("Window.bindScrollY(y)"), code)
  }

  test("<melt:window> with bind:scrollX emits Window.bindScrollX") {
    val code = compile("<melt:window bind:scrollX={x} />")
    assert(code.contains("Window.bindScrollX(x)"), code)
  }

  test("<melt:window> with bind:innerWidth emits Window.bindInnerWidth") {
    val code = compile("<melt:window bind:innerWidth={w} />")
    assert(code.contains("Window.bindInnerWidth(w)"), code)
  }

  test("<melt:window> with bind:innerHeight emits Window.bindInnerHeight") {
    val code = compile("<melt:window bind:innerHeight={h} />")
    assert(code.contains("Window.bindInnerHeight(h)"), code)
  }

  test("<melt:window> with bind:outerWidth emits Window.bindOuterWidth") {
    val code = compile("<melt:window bind:outerWidth={w} />")
    assert(code.contains("Window.bindOuterWidth(w)"), code)
  }

  test("<melt:window> with bind:outerHeight emits Window.bindOuterHeight") {
    val code = compile("<melt:window bind:outerHeight={h} />")
    assert(code.contains("Window.bindOuterHeight(h)"), code)
  }

  test("<melt:window> with bind:devicePixelRatio emits Window.bindDevicePixelRatio") {
    val code = compile("<melt:window bind:devicePixelRatio={dpr} />")
    assert(code.contains("Window.bindDevicePixelRatio(dpr)"), code)
  }

  test("<melt:window> with bind:online emits Window.bindOnline") {
    val code = compile("<melt:window bind:online={isOnline} />")
    assert(code.contains("Window.bindOnline(isOnline)"), code)
  }

  test("<melt:window> with multiple attrs emits all calls") {
    val code = compile("<melt:window onresize={fn} bind:innerWidth={w} bind:scrollY={y} />")
    assert(code.contains("""Window.on("resize")(fn)"""), code)
    assert(code.contains("Window.bindInnerWidth(w)"), code)
    assert(code.contains("Window.bindScrollY(y)"), code)
  }

  test("<melt:window> does not emit createElement") {
    val code = compile("<melt:window onresize={fn} />")
    assert(!code.contains("""createElement("melt:window")"""), code)
  }

  test("<melt:body> with event handler emits Body.on") {
    val code = compile("<melt:body onmouseenter={handleEnter} />")
    assert(code.contains("""Body.on("mouseenter")(handleEnter)"""), code)
  }

  test("<melt:body> with mouseleave emits Body.on") {
    val code = compile("<melt:body onmouseleave={handleLeave} />")
    assert(code.contains("""Body.on("mouseleave")(handleLeave)"""), code)
  }

  test("<melt:body> with use: directive emits Bind.action on document.body") {
    val code = compile("<melt:body use:trapScroll />")
    assert(code.contains("Bind.action(dom.document.body, trapScroll, ())"), code)
  }

  test("<melt:body> with use: directive with param emits Bind.action with param") {
    val code = compile("<melt:body use:someAction={param} />")
    assert(code.contains("Bind.action(dom.document.body, someAction, param)"), code)
  }

  test("<melt:body> does not emit createElement") {
    val code = compile("<melt:body onmouseenter={fn} />")
    assert(!code.contains("""createElement("melt:body")"""), code)
  }

  test("<melt:body> with multiple event handlers emits all Body.on calls") {
    val code = compile("<melt:body onmouseenter={enter} onmouseleave={leave} />")
    assert(code.contains("""Body.on("mouseenter")(enter)"""), code)
    assert(code.contains("""Body.on("mouseleave")(leave)"""), code)
  }

  // ── <melt:document> ───────────────────────────────────────────────────────

  test("<melt:document> with event handler emits Document.on") {
    val code = compile("<melt:document onvisibilitychange={handleVisibility} />")
    assert(code.contains("""Document.on("visibilitychange")(handleVisibility)"""), code)
  }

  test("<melt:document> with multiple event handlers emits all Document.on calls") {
    val code = compile("<melt:document onvisibilitychange={onVis} onselectionchange={onSel} />")
    assert(code.contains("""Document.on("visibilitychange")(onVis)"""), code)
    assert(code.contains("""Document.on("selectionchange")(onSel)"""), code)
  }

  test("<melt:document> does not emit createElement") {
    val code = compile("<melt:document onvisibilitychange={fn} />")
    assert(!code.contains("""createElement("melt:document")"""), code)
  }

  test("<melt:document> bind:visibilityState emits Document.bindVisibilityState") {
    val code = compile("<melt:document bind:visibilityState={state} />")
    assert(code.contains("Document.bindVisibilityState(state)"), code)
  }

  test("<melt:document> bind:fullscreenElement emits Document.bindFullscreenElement") {
    val code = compile("<melt:document bind:fullscreenElement={el} />")
    assert(code.contains("Document.bindFullscreenElement(el)"), code)
  }

  test("<melt:document> bind:pointerLockElement emits Document.bindPointerLockElement") {
    val code = compile("<melt:document bind:pointerLockElement={el} />")
    assert(code.contains("Document.bindPointerLockElement(el)"), code)
  }

  test("<melt:document> bind:activeElement emits Document.bindActiveElement") {
    val code = compile("<melt:document bind:activeElement={focused} />")
    assert(code.contains("Document.bindActiveElement(focused)"), code)
  }

  test("<melt:document> with event handler and bind directive emits both") {
    val code = compile("<melt:document onvisibilitychange={onVis} bind:visibilityState={state} />")
    assert(code.contains("""Document.on("visibilitychange")(onVis)"""), code)
    assert(code.contains("Document.bindVisibilityState(state)"), code)
  }

  // ── <melt:element> ────────────────────────────────────────────────────────

  test("<melt:element this={tag}> emits Bind.dynamicElement with anchor comment") {
    val code = compile("<melt:element this={tag}>content</melt:element>")
    assert(code.contains("createComment(\"\")"), code)
    assert(code.contains("Bind.dynamicElement(tag,"), code)
    assert(code.contains("_dynEl: dom.Element"), code)
    assert(!code.contains("""createElement("melt:element")"""), code)
  }

  test("<melt:element this={\"h2\"}> emits Bind.dynamicElement with literal tag") {
    val code = compile("""<melt:element this={"h2"}>heading</melt:element>""")
    assert(code.contains("""Bind.dynamicElement("h2","""), code)
  }

  test("<melt:element> children are emitted inside setup lambda") {
    val code = compile("<melt:element this={tag}><span>child</span></melt:element>")
    assert(code.contains("Bind.dynamicElement(tag,"), code)
    assert(code.contains("createElement(\"span\")"), code)
  }

  test("<melt:element> with static class attribute emits setAttribute in setup lambda") {
    val code = compile("""<melt:element this={tag} class="wrapper">text</melt:element>""")
    assert(code.contains("Bind.dynamicElement(tag,"), code)
    assert(code.contains("classList.add(\"wrapper\")"), code)
  }

  test("<melt:element> with event handler emits addEventListener in setup lambda") {
    val code = compile("<melt:element this={tag} onclick={handler}>click</melt:element>")
    assert(code.contains("Bind.dynamicElement(tag,"), code)
    assert(code.contains("""addEventListener("click", handler)"""), code)
  }

  test("<melt:element> does not emit createElement for the dynamic element itself") {
    val code = compile("<melt:element this={tag} />")
    assert(!code.contains("""createElement("melt:element")"""), code)
  }

  test("<melt:element> anchor is appended to parent before Bind.dynamicElement call") {
    val code = compile("<div><melt:element this={tag}>x</melt:element></div>")
    // The anchor comment should be appended to the parent div
    assert(code.contains("createComment(\"\")"), code)
    assert(code.contains("Bind.dynamicElement(tag,"), code)
  }

  test("<melt:element> scopeId is passed to Bind.dynamicElement") {
    val code    = compile("<melt:element this={tag}>text</melt:element>", name = "MyApp")
    val scopeId = SpaCodeGen.scopeIdFor("MyApp", "MyApp.melt")
    assert(code.contains(s"Bind.dynamicElement(tag,"), code)
    assert(code.contains(s""""$scopeId""""), code)
  }

  // ── §12.3.11 Props serialisation / hydration ───────────────────────────

  test("hydration entry imports SimpleJson and PropsCodec") {
    val src =
      """<script lang="scala" props="Props">
        |case class Props(name: String = "x")
        |</script>
        |<div>{props.name}</div>""".stripMargin
    val code = compileHydrate(src)
    assert(code.contains("import melt.runtime.json.{ PropsCodec, SimpleJson }"), code)
  }

  test("hydration entry emits derived PropsCodec val for components with Props") {
    val src =
      """<script lang="scala" props="Props">
        |case class Props(user: String = "guest", count: Int = 0)
        |</script>
        |<div>{props.user}</div>""".stripMargin
    val code = compileHydrate(src, name = "Home")
    assert(code.contains("private val _propsCodec: PropsCodec[Props] = PropsCodec.derived"), code)
  }

  test("hydration entry reads Props JSON from data-melt-props script tag") {
    val src =
      """<script lang="scala" props="Props">
        |case class Props(user: String = "guest")
        |</script>
        |<div>{props.user}</div>""".stripMargin
    val code = compileHydrate(src, name = "Home")
    assert(
      code.contains("""dom.document.querySelector("script[data-melt-props=\"home\"]")"""),
      code
    )
    assert(code.contains("_propsCodec.decode(SimpleJson.parse"), code)
    assert(code.contains("Mount(host, apply(_props))"), code)
  }

  test("hydration entry falls back to defaults when Props JSON is missing") {
    val src =
      """<script lang="scala" props="Props">
        |case class Props(user: String = "guest")
        |</script>
        |<div>{props.user}</div>""".stripMargin
    val code = compileHydrate(src, name = "Home")
    // The fallback path constructs Props() for all-defaults components
    // so that pages served outside of Template.render still hydrate.
    assert(code.contains("Props()"), code)
  }

  test("hydration entry supports user-named Props type (arbitrary case class name)") {
    val src =
      """<script lang="scala" props="MyFancyProps">
        |case class MyFancyProps(name: String = "x")
        |</script>
        |<div>{props.name}</div>""".stripMargin
    val code = compileHydrate(src, name = "Fancy")
    assert(
      code.contains("private val _propsCodec: PropsCodec[MyFancyProps] = PropsCodec.derived"),
      code
    )
    assert(code.contains("val _props: MyFancyProps"), code)
  }

  test("components without Props do not import PropsCodec / SimpleJson") {
    val code = compileHydrate("<div>hi</div>", name = "Plain")
    // The import line is still emitted (it's in the top-of-file
    // boilerplate) but no _propsCodec / _props / SimpleJson usage
    // should appear in the body.
    assert(!code.contains("_propsCodec"), code)
    assert(!code.contains("SimpleJson.parse"), code)
    assert(code.contains("Mount(host, apply())"), code)
  }

  test("hydration entry is omitted when hydration flag is disabled") {
    val src =
      """<script lang="scala" props="Props">
        |case class Props(name: String = "x")
        |</script>
        |<div>{props.name}</div>""".stripMargin
    val code = compile(src)
    assert(!code.contains("@JSExportTopLevel(\"hydrate\""), code)
    assert(!code.contains("_meltHydrateEntry"), code)
    assert(!code.contains("_propsCodec"), code)
  }

  // ── bind:value for textarea / select ─────────────────────────────────────

  test("bind:value on textarea emits Bind.textareaValue with TextArea cast") {
    val code = compile("""<textarea bind:value={content}></textarea>""")
    assert(code.contains("Bind.textareaValue("), code)
    assert(code.contains("dom.html.TextArea"), code)
    assert(!code.contains("dom.html.Input"), code)
    assert(!code.contains("Bind.inputValue("), code)
  }

  test("bind:value on select emits Bind.selectValue after option children") {
    val code = compile(
      """<select bind:value={choice}>
        |  <option value="a">A</option>
        |  <option value="b">B</option>
        |</select>""".stripMargin
    )
    assert(code.contains("Bind.selectValue("), code)
    assert(code.contains("dom.html.Select"), code)
    assert(!code.contains("dom.html.Input"), code)
    assert(!code.contains("Bind.inputValue("), code)
    // selectValue must be emitted after the option appendChild calls
    val selectCall = code.indexOf("Bind.selectValue(")
    val lastAppend = code.lastIndexOf(".appendChild(")
    assert(selectCall > lastAppend, s"Bind.selectValue must come after appendChild:\n$code")
  }

  test("bind:value on select multiple emits Bind.selectMultipleValue") {
    val code = compile(
      """<select multiple bind:value={choices}>
        |  <option value="a">A</option>
        |  <option value="b">B</option>
        |</select>""".stripMargin
    )
    assert(code.contains("Bind.selectMultipleValue("), code)
    assert(!code.contains("Bind.selectValue("), code)
    // must be after children
    val selectCall = code.indexOf("Bind.selectMultipleValue(")
    val lastAppend = code.lastIndexOf(".appendChild(")
    assert(selectCall > lastAppend, s"Bind.selectMultipleValue must come after appendChild:\n$code")
  }

  // ── melt:boundary ────────────────────────────────────────────────────────

  test("melt:boundary — minimal (children only) emits Boundary.create with children lambda") {
    val src  = "<melt:boundary><p>Content</p></melt:boundary>"
    val code = compile(src)
    assert(code.contains("Boundary.create(Boundary.Props(children = _bChildren0))"), code)
    assert(code.contains("_bChildren0: (() => dom.Element) = () =>"), code)
    assert(code.contains("""createElement("p")"""), code)
  }

  test("melt:boundary as root element wraps in display:contents div") {
    val src  = "<melt:boundary><p>Content</p></melt:boundary>"
    val code = compile(src)
    assert(code.contains("_bWrap0"), code)
    assert(code.contains("""setAttribute("style", "display: contents")"""), code)
    assert(code.contains("_bWrap0.appendChild(_bFrag0)"), code)
    assert(code.contains("val _result = _bWrap0"), code)
  }

  test("melt:boundary as child emits appendChild and returns empty string") {
    val src  = "<div><melt:boundary><p>Inner</p></melt:boundary></div>"
    val code = compile(src)
    // Fragment is appended to parent div; no wrapper div
    assert(code.contains("_el0.appendChild(_bFrag0)"), code)
    assert(!code.contains("display: contents"), code)
  }

  test("melt:boundary with melt:pending emits pending lambda and Some(...)") {
    val src =
      """<melt:boundary>
        |  <p>Content</p>
        |  <melt:pending><span>Loading…</span></melt:pending>
        |</melt:boundary>""".stripMargin
    val code = compile(src)
    assert(code.contains("_bPending0: (() => dom.Element) = () =>"), code)
    assert(code.contains("""createElement("span")"""), code)
    assert(code.contains("pending = Some(_bPending0)"), code)
  }

  test("melt:boundary with melt:failed emits fallback lambda with (error, reset) params") {
    val src =
      """<melt:boundary>
        |  <p>Content</p>
        |  <melt:failed (error, reset)>
        |    <p>Error</p>
        |    <button onclick={_ => reset()}>Retry</button>
        |  </melt:failed>
        |</melt:boundary>""".stripMargin
    val code = compile(src)
    assert(code.contains("_bFallback0: (Throwable, () => Unit) => dom.Element = (error, reset) =>"), code)
    assert(code.contains("fallback = _bFallback0"), code)
    assert(code.contains("""addEventListener("click", _ => reset())"""), code)
  }

  test("melt:boundary with onerror attr emits onError prop") {
    val src  = "<melt:boundary onerror={handleError}><p>Content</p></melt:boundary>"
    val code = compile(src)
    assert(code.contains("onError = handleError"), code)
  }

  test("melt:boundary full combination emits all props") {
    val src =
      """<melt:boundary onerror={handleError}>
        |  <p>Content</p>
        |  <melt:pending><span>Loading…</span></melt:pending>
        |  <melt:failed (error, reset)><p>Error: {error.getMessage()}</p></melt:failed>
        |</melt:boundary>""".stripMargin
    val code = compile(src)
    assert(code.contains("_bChildren0"), code)
    assert(code.contains("_bPending0"), code)
    assert(code.contains("_bFallback0"), code)
    assert(code.contains("onError = handleError"), code)
    assert(code.contains("pending = Some(_bPending0)"), code)
    assert(code.contains("fallback = _bFallback0"), code)
  }

  test("melt:boundary melt:pending children excluded from main children lambda") {
    val src =
      """<melt:boundary>
        |  <p>Main</p>
        |  <melt:pending><span>Pending</span></melt:pending>
        |</melt:boundary>""".stripMargin
    val code = compile(src)
    // Main children lambda contains "p" element (from <p>Main</p>)
    // Pending lambda contains "span" element (from <span>Pending</span>)
    assert(code.contains("""createElement("p")"""), code)
    assert(code.contains("""createElement("span")"""), code)
  }

  test("bind:value on input still emits Bind.inputValue") {
    val code = compile("""<input bind:value={name} />""")
    assert(code.contains("Bind.inputValue("), code)
    assert(!code.contains("Bind.textareaValue("), code)
    assert(!code.contains("Bind.selectValue("), code)
  }
