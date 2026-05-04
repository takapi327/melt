/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.analysis

import meltc.{ CompileMode, MeltCompiler }

/** Tests for [[MalformedExpressionChecker]] — detects HTML closing tags leaked
  * into Scala expression code due to a missing closing `}`.
  */
class MalformedExpressionCheckerSpec extends munit.FunSuite:

  private def compile(template: String) =
    val src = s"""<script lang="scala">
                 |val x = 1
                 |</script>
                 |$template""".stripMargin
    MeltCompiler.compile(src, "App.melt", "App", "", CompileMode.SPA)

  // ── Positive cases (should emit an error) ─────────────────────────────────

  test("unclosed brace leaking closing tag produces an error") {
    val result = compile("<div>{count</div>")
    assert(result.errors.nonEmpty, "expected a MalformedExpression error")
    assert(
      result.errors.exists(_.message.contains("</")),
      s"error should mention '</', got: ${ result.errors.map(_.message) }"
    )
  }

  test("unclosed brace leaking span closing tag produces an error") {
    val result = compile("<p>{msg</p>")
    assert(result.errors.nonEmpty, "expected a MalformedExpression error")
  }

  test("malformed expression nested inside element produces an error") {
    val result = compile("<div><span>{value</span></div>")
    assert(result.errors.nonEmpty, "expected a MalformedExpression error")
  }

  // ── Negative cases (should NOT produce a false positive) ──────────────────

  test("properly closed expression does not produce an error") {
    val result = compile("<div>{x}</div>")
    assert(result.errors.isEmpty, s"unexpected errors: ${ result.errors.map(_.message) }")
  }

  test("closing tag in a double-quoted string literal does not trigger error") {
    val result = compile("""<div>{"</div>"}</div>""")
    assert(result.errors.isEmpty, s"unexpected errors: ${ result.errors.map(_.message) }")
  }

  test("closing tag in a triple-quoted string literal does not trigger error") {
    val result = compile("""<div>{s"</span>"}</div>""")
    assert(result.errors.isEmpty, s"unexpected errors: ${ result.errors.map(_.message) }")
  }

  test("closing tag in a line comment does not trigger error") {
    val result = compile("<div>{x // </div>\n}</div>")
    assert(result.errors.isEmpty, s"unexpected errors: ${ result.errors.map(_.message) }")
  }

  test("closing tag in a block comment does not trigger error") {
    val result = compile("<div>{x /* </div> */}</div>")
    assert(result.errors.isEmpty, s"unexpected errors: ${ result.errors.map(_.message) }")
  }

  test("empty template has no errors") {
    val result = compile("<div></div>")
    assert(result.errors.isEmpty, s"unexpected errors: ${ result.errors.map(_.message) }")
  }
