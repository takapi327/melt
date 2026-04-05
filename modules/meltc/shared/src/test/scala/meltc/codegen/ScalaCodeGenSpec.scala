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
    assert(code.contains("import melt.runtime.{ Mount, Style }"), code)
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

  test("static attribute") {
    val code = compile("""<div class="container"></div>""")
    assert(code.contains("""setAttribute("class", "container")"""), code)
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

  // ── Expression nodes ─────────────────────────────────────────────────────

  test("expression node becomes text node with toString") {
    val code = compile("<p>{count}</p>")
    assert(code.contains("(count).toString"), code)
    assert(code.contains("createTextNode"), code)
  }

  // ── Script section ───────────────────────────────────────────────────────

  test("script code is included in object body") {
    val src =
      """<script lang="scala">
        |val count = 42
        |</script>
        |<p>{count}</p>""".stripMargin
    val code = compile(src)
    assert(code.contains("val count = 42"), code)
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
