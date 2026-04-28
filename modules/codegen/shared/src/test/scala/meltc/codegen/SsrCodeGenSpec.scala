/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.codegen

import meltc.{ CompileMode, MeltCompiler }

/** Phase A unit tests for [[SsrCodeGen]].
  *
  * The tests compile a `.melt` source string through `MeltCompiler.compile`
  * with `CompileMode.SSR` and assert on the generated Scala source text.
  * Runtime behaviour (that the generated code actually produces the
  * expected HTML when executed) is covered by a separate integration
  * harness in the `runtime.jvm` test tree.
  */
class SsrCodeGenSpec extends munit.FunSuite:

  /** Compile source in SSR mode, assert success, return generated Scala. */
  private def compile(src: String, name: String = "App", pkg: String = ""): String =
    val result = MeltCompiler.compile(src, s"$name.melt", name, pkg, CompileMode.SSR)
    assert(result.errors.isEmpty, s"Compile errors: ${ result.errors.map(_.message) }")
    result.scalaCode.getOrElse(fail("No generated code"))

  // ── Basic structure ────────────────────────────────────────────────────

  test("SsrCodeGen imports the SSR runtime package") {
    val code = compile("<div></div>")
    assert(code.contains("import melt.runtime.*"), code)
    assert(code.contains("import melt.runtime.render.*"), code)
  }

  test("SsrCodeGen emits apply() returning RenderResult") {
    val code = compile("<div></div>")
    assert(code.contains("def apply"), code)
    assert(code.contains(": RenderResult"), code)
    assert(code.contains("ServerRenderer()"), code)
    assert(code.contains("renderer.result()"), code)
  }

  test("SsrCodeGen does not reference DOM APIs") {
    val code = compile("<div>hi</div>")
    assert(!code.contains("org.scalajs.dom"), code)
    assert(!code.contains("createElement"), code)
    assert(!code.contains("appendChild"), code)
  }

  test("SsrCodeGen tracks the component under its kebab-case moduleID") {
    val code = compile("<div></div>", name = "TodoList")
    assert(code.contains("""trackComponent("todo-list")"""), code)
  }

  // ── Element / text / expression ───────────────────────────────────────

  test("static element emits open/close tags with scope class") {
    val code = compile("<div></div>")
    assert(code.contains("""renderer.push("<div")"""), code)
    assert(code.contains("""class=\""""), code)
    assert(code.contains("""renderer.push("</div>")"""), code)
  }

  test("text nodes are HTML-escaped at compile time") {
    val code = compile("<p>3 < 4 & 5 > 2</p>")
    // Literal escapes for the static text
    assert(code.contains("&lt;") || code.contains("&lt"), code)
    assert(code.contains("&amp;") || code.contains("&amp"), code)
    assert(code.contains("&gt;") || code.contains("&gt"), code)
  }

  test("expression interpolation runs through Escape.html") {
    val code = compile("<p>{name}</p>")
    assert(code.contains("Escape.html(name)"), code)
  }

  test("void elements do not emit a closing tag") {
    val code = compile("<br/>")
    assert(code.contains("""renderer.push("<br")"""), code)
    assert(!code.contains("""</br>"""), code)
  }

  // ── Attributes ─────────────────────────────────────────────────────────

  test("static non-class attribute is HTML-escaped into the source") {
    val code = compile("""<a data-id="42">x</a>""")
    assert(code.contains("data-id=\\\"42\\\""), code)
  }

  test("dynamic URL attribute on <a href> uses Escape.url") {
    val code = compile("""<a href={props.url}>x</a>""")
    assert(code.contains("Escape.url(props.url)"), code)
  }

  test("dynamic non-URL attribute uses Escape.attr") {
    val code = compile("""<div title={props.t}>x</div>""")
    assert(code.contains("Escape.attr(props.t)"), code)
  }

  test("event handlers are stripped in SSR") {
    val code = compile("""<button onclick={handler}>+</button>""")
    assert(!code.contains("addEventListener"), code)
    assert(!code.contains("onclick"), code)
    assert(!code.contains("handler"), code)
  }

  // ── melt: special elements ─────────────────────────────────────────────

  test("melt:window contents are dropped") {
    val code = compile("""<melt:window onresize={h}/><div>body</div>""")
    assert(!code.contains("Window"), code)
    assert(!code.contains("onresize"), code)
  }

  test("melt:body contents are dropped") {
    val code = compile("""<melt:body onclick={h}/><div>body</div>""")
    assert(!code.contains("Body.on"), code)
  }

  test("melt:head with dynamic title routes through renderer.head.title") {
    val code = compile("""<melt:head><title>{pageTitle}</title></melt:head><div/>""")
    assert(code.contains("renderer.head.title(pageTitle)"), code)
  }

  // ── Components ─────────────────────────────────────────────────────────

  test("bare component renders via apply + merge") {
    val code = compile("""<div><Child/></div>""")
    assert(code.contains("renderer.merge(Child())"), code)
  }

  test("component with attributes builds Props()") {
    val code = compile("""<div><Child name="Ada" age={props.age}/></div>""")
    assert(code.contains("Child.Props("), code)
    assert(code.contains("name = \"Ada\""), code)
    assert(code.contains("age = props.age"), code)
  }

  test("component inside if/else branch uses mergeMeta to propagate CSS and hydration tracking") {
    val src  = """<div>{if cond then <Child/> else <p>no</p>}</div>"""
    val code = compile(src)
    // The component call result is captured, mergeMeta is called (to propagate
    // CSS + hydration component tracking), and the body is appended separately.
    assert(code.contains("renderer.mergeMeta("), code)
    assert(code.contains("_r.body"), code)
    // The body must NOT be pushed via renderer.merge (which would double-push).
    assert(!code.contains("renderer.merge(Child()"), code)
  }

  test("component inside if/else branch with props uses mergeMeta") {
    val src  = """<div>{if x then <Child name="Ada"/> else <span>no</span>}</div>"""
    val code = compile(src)
    assert(code.contains("renderer.mergeMeta("), code)
    assert(code.contains("Child.Props("), code)
    assert(code.contains("_r.body"), code)
  }

  // ── §12.3.11 Props serialisation ───────────────────────────────────────

  test("components without props do not emit a PropsCodec val") {
    val code = compile("""<div>hi</div>""")
    assert(!code.contains("_propsCodec"), code)
    assert(!code.contains("trackHydrationProps"), code)
    assert(!code.contains("import melt.runtime.json.PropsCodec"), code)
  }

  test("components with props emit a derived PropsCodec val") {
    val src =
      """<script lang="scala" props="Props">
        |case class Props(name: String = "world", count: Int = 0)
        |</script>
        |<div>{props.name}</div>""".stripMargin
    val code = compile(src)
    assert(code.contains("import melt.runtime.json.PropsCodec"), code)
    assert(code.contains("private val _propsCodec: PropsCodec[Props] = PropsCodec.derived"), code)
  }

  test("apply(props) tracks the serialised props on the renderer") {
    val src =
      """<script lang="scala" props="Props">
        |case class Props(name: String = "x")
        |</script>
        |<div>{props.name}</div>""".stripMargin
    val code = compile(src, name = "Greeting")
    // The moduleID is kebab-case of the object name, and the JSON
    // payload comes from the derived codec.
    assert(
      code.contains("""renderer.trackHydrationProps("greeting", _propsCodec.encodeToString(props))"""),
      code
    )
  }

  test("custom Props type name (not literally 'Props') is supported") {
    // `props="HomeProps"` — the user can name their type whatever
    // they want. meltc only knows the name, and the Scala inliner
    // takes care of the rest.
    val src =
      """<script lang="scala" props="HomeProps">
        |case class HomeProps(user: String = "guest")
        |</script>
        |<div>{props.user}</div>""".stripMargin
    val code = compile(src, name = "Home")
    assert(code.contains("private val _propsCodec: PropsCodec[HomeProps] = PropsCodec.derived"), code)
    assert(code.contains("_propsCodec.encodeToString(props)"), code)
  }

  // ── CSS ────────────────────────────────────────────────────────────────

  test("style section is scoped and registered") {
    val code = compile("""<div>hi</div><style>div { color: red; }</style>""")
    assert(code.contains("renderer.css.add(_scopeId, _css)"), code)
    assert(code.contains("private val _css"), code)
  }

  // ── Phase B: list rendering / InlineTemplate ──────────────────────────

  test("list rendering with inline HTML uses foreach(renderer.push)") {
    val src =
      """<ul>{items.map((item: String) =>
        |  <li>{item}</li>
        |)}</ul>""".stripMargin
    val code = compile(src)
    assert(code.contains(".foreach(renderer.push)"), code)
    // The <li> fragment should be serialised via StringBuilder blocks.
    assert(code.contains("_sb"), code)
    assert(code.contains("Escape.html(item)"), code)
    assert(!code.contains("createElement"), code)
  }

  test("list rendering inside .map produces open/close tags for children") {
    val src =
      """<ul>{items.map((item: String) =>
        |  <li>{item}</li>
        |)}</ul>""".stripMargin
    val code = compile(src)
    // The inlined fragment should include string literals for <li and </li>.
    assert(code.contains("\"<li\""), code)
    assert(code.contains("\"</li>\""), code)
  }

  test("keyed list rendering reduces to plain foreach in SSR") {
    val src =
      """<ul>{items.keyed(_.id).map(item =>
        |  <li>{item.name}</li>
        |)}</ul>""".stripMargin
    val code = compile(src)
    // KeyedMap in SSR is indistinguishable from ListMap — both foreach.
    assert(code.contains(".foreach(renderer.push)"), code)
  }

  test("static list child attribute is HTML-escaped into fragment") {
    val src =
      """<ul>{items.map((item: String) =>
        |  <li class="entry">{item}</li>
        |)}</ul>""".stripMargin
    val code = compile(src)
    // The static class "entry" should be combined with the scope id and
    // embedded as a literal attribute value.
    assert(code.contains("entry"), code)
  }

  // ── Phase B: conditional rendering (if / match) ───────────────────────

  test("if / else with inline HTML emits native if + renderer.push") {
    val src  = """<div>{if visible then <p>Yes</p> else <p>No</p>}</div>"""
    val code = compile(src)
    assert(code.contains("renderer.push(if visible then"), code)
    assert(code.contains("else"), code)
    assert(code.contains("\"<p\""), code)
    assert(code.contains("\"</p>\""), code)
    assert(!code.contains("Bind.show"), code)
  }

  test("match with inline HTML emits native match + renderer.push") {
    val src =
      """<div>{n match
        |  case 0 => <span>zero</span>
        |  case _ => <span>other</span>
        |}</div>""".stripMargin
    val code = compile(src)
    assert(code.contains("renderer.push(n match"), code)
    assert(code.contains("case 0 =>"), code)
    assert(code.contains("case _ =>"), code)
    assert(code.contains("\"zero\""), code)
    assert(code.contains("\"other\""), code)
  }

  test("conditional with dynamic expression inside runs Escape.html") {
    val src =
      """<div>{if active then <p>hi {name}</p> else <p>bye</p>}</div>""".stripMargin
    val code = compile(src)
    assert(code.contains("Escape.html(name)"), code)
  }

  // ── Phase B §12.3.6: special element bindings ─────────────────────────

  test("textarea bind:value emits the value as body content (escaped)") {
    val code = compile("""<textarea bind:value={userText}/>""")
    assert(code.contains("""renderer.push("<textarea")"""), code)
    assert(code.contains("""renderer.push("</textarea>")"""), code)
    assert(code.contains("renderer.push(Escape.html(userText))"), code)
    // No `value="..."` attribute should be emitted.
    assert(!code.contains("""Escape.attr(userText)"""), code)
  }

  test("select bind:value marks the matching option as selected (static value)") {
    val src =
      """<select bind:value={choice}>
        |  <option value="a">A</option>
        |  <option value="b">B</option>
        |</select>""".stripMargin
    val code = compile(src)
    assert(code.contains("""if (choice == "a") renderer.push(" selected")"""), code)
    assert(code.contains("""if (choice == "b") renderer.push(" selected")"""), code)
  }

  test("select bind:value strips the directive from the <select> tag") {
    val code = compile("""<select bind:value={c}><option value="x">x</option></select>""")
    assert(!code.contains("bind:value"), code)
  }

  test("input radio bind:group emits checked when value matches") {
    val code = compile("""<input type="radio" bind:group={choice} value="a"/>""")
    assert(code.contains("""if (choice == "a") renderer.push(" checked")"""), code)
  }

  test("input checkbox bind:group emits checked when collection contains value") {
    val code = compile("""<input type="checkbox" bind:group={choices} value="a"/>""")
    assert(code.contains("""if (choices.contains("a")) renderer.push(" checked")"""), code)
  }

  test("bind:innerHTML emits TrustedHtml body without escaping") {
    val code = compile("""<div bind:innerHTML={richContent}/>""")
    assert(code.contains("renderer.push(richContent.value)"), code)
    assert(code.contains("""renderer.push("</div>")"""), code)
  }

  test("bind:textContent HTML-escapes the expression") {
    val code = compile("""<p bind:textContent={raw}/>""")
    assert(code.contains("renderer.push(Escape.html(raw))"), code)
    assert(code.contains("""renderer.push("</p>")"""), code)
  }

  test("innerHTML wins over textContent if both present") {
    val code = compile("""<div bind:innerHTML={html} bind:textContent={txt}/>""")
    assert(code.contains("renderer.push(html.value)"), code)
    assert(!code.contains("Escape.html(txt)"), code)
  }

  // ── Phase C §C3: hydration markers ─────────────────────────────────────

  test("SsrCodeGen wraps the body in hydration markers using kebab moduleID") {
    val code = compile("<div></div>", name = "TodoList")
    assert(code.contains("""HydrationMarkers.open("todo-list")"""), code)
    assert(code.contains("""HydrationMarkers.close("todo-list")"""), code)
  }

  test("hydration open marker precedes the template body") {
    val code    = compile("<div>body</div>", name = "Counter")
    val openIdx = code.indexOf("""HydrationMarkers.open("counter")""")
    val bodyIdx = code.indexOf("""renderer.push("<div")""")
    assert(openIdx >= 0 && bodyIdx > openIdx, s"open at $openIdx, body at $bodyIdx")
  }

  test("hydration close marker follows the template body") {
    val code     = compile("<div>body</div>", name = "Counter")
    val bodyEnd  = code.indexOf("""renderer.push("</div>")""")
    val closeIdx = code.indexOf("""HydrationMarkers.close("counter")""")
    assert(bodyEnd >= 0 && closeIdx > bodyEnd, s"body-end at $bodyEnd, close at $closeIdx")
  }

  // ── Children / slot support ────────────────────────────────────────────

  test("{children} expression adds children parameter to apply()") {
    val code = compile("<div>{children}</div>")
    assert(code.contains("children: () => RenderResult = () => RenderResult.empty"), code)
  }

  test("{children} expression emits renderer.merge(children())") {
    val code = compile("<div>{children}</div>")
    assert(code.contains("renderer.merge(children())"), code)
  }

  test("no {children} in template does not add children parameter") {
    val code = compile("<div><p>hello</p></div>")
    assert(!code.contains("children"), code)
  }

  test("{children} with props adds both params to apply()") {
    val src =
      """<script lang="scala" props="Props">
        |case class Props(title: String = "")
        |</script>
        |<div>{children}</div>""".stripMargin
    val code = compile(src)
    assert(code.contains("props: Props = Props()"), code)
    assert(code.contains("children: () => RenderResult = () => RenderResult.empty"), code)
  }

  test("component call with children nodes generates children lambda") {
    val code = compile("<div><Card><p>Content</p></Card></div>")
    assert(code.contains("Card(children ="), code)
    assert(!code.contains("Card.Props("), code)
    assert(code.contains("() => {"), code)
    assert(code.contains("\"<p\""), code)
  }

  test("component call with props and children generates correct call") {
    val code = compile("""<div><Card title="T"><p>Body</p></Card></div>""")
    assert(code.contains("Card(Card.Props("), code)
    assert(code.contains("children ="), code)
    assert(code.contains("\"<p\""), code)
  }

  test("{children} inside inline template appendNodeToSb path") {
    val src =
      """{items.map(item =>
        |  <div>{children}</div>
        |)}""".stripMargin
    val code = compile(src)
    assert(code.contains("children()"), code)
  }
