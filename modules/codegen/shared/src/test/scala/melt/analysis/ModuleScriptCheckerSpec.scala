/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.analysis

import melt.ast.ScriptSection

class ModuleScriptCheckerSpec extends munit.FunSuite:

  private def check(code: String) =
    ModuleScriptChecker.check(ScriptSection(code))

  private def checkSsr(code: String) =
    ModuleScriptChecker.checkSsrState(ScriptSection(code))

  // ── check (props reference) ────────────────────────────────────────────────

  test("no issues when module script has no props reference") {
    val result = check("val total = State(0)\ndef format(n: Int): String = s\"#$n\"")
    assertEquals(result, Nil)
  }

  test("error when module script references props") {
    val result = check("val x = props.label")
    assertEquals(result.length, 1)
    assert(result.head._1.contains("`props` is not available"), result.head._1)
    assertEquals(result.head._2, 1) // line 1
  }

  test("error reports correct local line number for props reference") {
    val code =
      """val x = 1
        |val y = props.label
        |val z = 2""".stripMargin
    val result = check(code)
    assertEquals(result.length, 1)
    assertEquals(result.head._2, 2) // props is on line 2
  }

  test("multiple lines with props each produce an error") {
    val code =
      """val a = props.x
        |val b = 1
        |val c = props.y""".stripMargin
    val result = check(code)
    assertEquals(result.length, 2)
    assertEquals(result(0)._2, 1)
    assertEquals(result(1)._2, 3)
  }

  test("props as part of a longer identifier does not trigger error") {
    val result = check("val myprops = 1\nval propsCount = 2")
    assertEquals(result, Nil)
  }

  // ── checkSsrState ─────────────────────────────────────────────────────────

  test("no warning when module script has no State or Var") {
    val result = checkSsr("val MaxItems = 100\ndef format(n: Int): String = n.toString")
    assertEquals(result, Nil)
  }

  test("warning when module script contains State(...)") {
    val result = checkSsr("val total = State(0)")
    assertEquals(result.length, 1)
    assert(result.head._1.contains("State/Var"), result.head._1)
    assert(result.head._1.contains("SSR"), result.head._1)
  }

  test("warning when module script contains Var(...)") {
    val result = checkSsr("val counter = Var(0)")
    assertEquals(result.length, 1)
    assert(result.head._1.contains("State/Var"), result.head._1)
  }

  test("checkSsrState returns at most one warning (file-level)") {
    val result = checkSsr("val a = State(0)\nval b = Var(1)")
    assertEquals(result.length, 1) // single file-level warning
  }
