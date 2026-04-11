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
    assert(code.contains("import melt.runtime.ssr.*"), code)
  }

  test("SsrCodeGen emits apply() returning RenderResult") {
    val code = compile("<div></div>")
    assert(code.contains("def apply"), code)
    assert(code.contains(": RenderResult"), code)
    assert(code.contains("SsrRenderer()"), code)
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

  // ── CSS ────────────────────────────────────────────────────────────────

  test("style section is scoped and registered") {
    val code = compile("""<div>hi</div><style>div { color: red; }</style>""")
    assert(code.contains("renderer.css.add(_scopeId, _css)"), code)
    assert(code.contains("private val _css"), code)
  }
