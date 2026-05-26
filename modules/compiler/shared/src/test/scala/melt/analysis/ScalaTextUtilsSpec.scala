/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.analysis

/** Direct unit tests for [[ScalaTextUtils]].
  *
  * The underlying logic was previously private to [[EffectDepsChecker]] and
  * exercised only through integration tests via `MeltCompiler.compile()`.
  * These tests pin down the exact behaviour after extraction.
  */
class ScalaTextUtilsSpec extends munit.FunSuite:

  // ── stripStringLiterals ───────────────────────────────────────────────────

  test("plain string literal content is replaced with spaces") {
    val result = ScalaTextUtils.stripStringLiterals("""val x = "hello world"""")
    assert(!result.contains("hello"), s"Expected content erased, got: $result")
    assert(result.contains("val x ="), s"Code outside string preserved: $result")
  }

  test("triple-quoted string content is replaced with spaces") {
    val result = ScalaTextUtils.stripStringLiterals("""val x = """" + "\"\"\"" + """hello""" + "\"\"\"")
    assert(!result.contains("hello"), s"Expected content erased, got: $result")
  }

  test("line comment content is replaced with spaces") {
    val result = ScalaTextUtils.stripStringLiterals("val x = 1 // .map( reactive")
    assert(!result.contains("reactive"), s"Comment erased: $result")
    assert(result.contains("val x = 1"), s"Code preserved: $result")
  }

  test("block comment content is replaced with spaces") {
    val result = ScalaTextUtils.stripStringLiterals("val x = /* .map( */ y")
    assert(!result.contains(".map("), s"Block comment erased: $result")
    assert(result.contains("val x ="), s"Code preserved: $result")
    assert(result.contains(" y"), s"Code after comment preserved: $result")
  }

  test("interpolated string ${...} block is preserved") {
    val result = ScalaTextUtils.stripStringLiterals("""s"prefix ${count + 1} suffix"""")
    assert(result.contains("count + 1"), s"Interp block preserved: $result")
    assert(!result.contains("prefix"), s"Non-interp content erased: $result")
    assert(!result.contains("suffix"), s"Non-interp content erased: $result")
  }

  test("interpolated string $identifier is preserved") {
    val result = ScalaTextUtils.stripStringLiterals("""s"Hello $name!"""")
    assert(result.contains("$name"), s"Simple interp preserved: $result")
    assert(!result.contains("Hello"), s"Non-interp content erased: $result")
  }

  test("escape sequence inside string does not confuse the parser") {
    val result = ScalaTextUtils.stripStringLiterals("""val x = "line1\nline2" + y""")
    assert(result.contains("+ y"), s"Code after string preserved: $result")
    assert(!result.contains("line1"), s"String content erased: $result")
  }

  test("code without any strings is returned unchanged") {
    val code   = "val x = count + 1"
    val result = ScalaTextUtils.stripStringLiterals(code)
    assertEquals(result, code)
  }

  // ── extractReactiveVars ───────────────────────────────────────────────────

  test("extracts State-declared variable names") {
    val code   = "val count = State(0)\nval name = State(\"Alice\")"
    val result = ScalaTextUtils.extractReactiveVars(code)
    assertEquals(result, Set("count", "name"))
  }

  test("extracts signal-derived variable names") {
    val code   = "val doubled = count.map(_ * 2)\nval upper = name.flatMap(_.toUpperCase)"
    val result = ScalaTextUtils.extractReactiveVars(code)
    assertEquals(result, Set("doubled", "upper"))
  }

  test("extracts memo variable names") {
    val code   = "val cached = memo(expensiveCompute())"
    val result = ScalaTextUtils.extractReactiveVars(code)
    assertEquals(result, Set("cached"))
  }

  test("does not match pattern inside a string literal") {
    val code   = """val x = "val fake = State(0)" """
    val result = ScalaTextUtils.extractReactiveVars(code)
    assertEquals(result, Set.empty[String])
  }

  test("does not match pattern inside a line comment") {
    val code   = "// val fake = State(0)\nval real = State(1)"
    val result = ScalaTextUtils.extractReactiveVars(code)
    assertEquals(result, Set("real"))
  }

  test("returns empty set for code with no reactive vars") {
    val code   = "val x = 42\ndef foo(): Unit = println(x)"
    val result = ScalaTextUtils.extractReactiveVars(code)
    assertEquals(result, Set.empty[String])
  }
