/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.parser

/** Direct unit tests for [[ExprExtractor]].
  *
  * The extractor is also exercised indirectly through [[TemplateParser]], but
  * these tests pin down its exact behaviour for edge cases.
  */
class ExprExtractorSpec extends munit.FunSuite:

  /** Convenience wrapper: wraps `src` in `{ }` and extracts. */
  private def extract(src: String): String =
    val full      = "{" + src + "}"
    val (expr, _) = ExprExtractor.extract(full, 1)
    expr

  /** Returns the end-position after extracting from `{ ... }`. */
  private def endPos(src: String): Int =
    val full     = "{" + src + "}"
    val (_, end) = ExprExtractor.extract(full, 1)
    end

  // ── Basic expressions ─────────────────────────────────────────────────────

  test("simple identifier") {
    assertEquals(extract("count"), "count")
  }

  test("arithmetic expression") {
    assertEquals(extract("x + 1"), "x + 1")
  }

  test("method call chain") {
    assertEquals(extract("list.map(_.toString).mkString(\", \")"), "list.map(_.toString).mkString(\", \")")
  }

  test("end position is just after the closing brace") {
    val src      = "{hello}"
    val (_, end) = ExprExtractor.extract(src, 1)
    assertEquals(end, src.length)
  }

  // ── Nested braces ─────────────────────────────────────────────────────────

  test("one level of nested braces") {
    assertEquals(extract("val x = {1 + 2}; x"), "val x = {1 + 2}; x")
  }

  test("two levels of nested braces") {
    assertEquals(extract("f({g({h()})})"), "f({g({h()})})")
  }

  test("Map literal with braces") {
    assertEquals(extract("""Map("a" -> {1 + 2})"""), """Map("a" -> {1 + 2})""")
  }

  // ── String literals ───────────────────────────────────────────────────────

  test("double-quoted string with curly braces inside") {
    assertEquals(extract(""""hello {world}""""), """"hello {world}"""")
  }

  test("double-quoted string is not confused with closing brace") {
    assertEquals(extract(""""}"  """.trim), """"}"  """.trim)
  }

  test("string with backslash escape") {
    assertEquals(extract(""""say \\"hi\\"" """.trim), """"say \\"hi\\"" """.trim)
  }

  test("interpolated string with ${...}") {
    assertEquals(extract("""s"Count: ${count.now()}""""), """s"Count: ${count.now()}"""")
  }

  test("interpolated string with multiple holes") {
    assertEquals(extract("""s"$firstName $lastName""""), """s"$firstName $lastName"""")
  }

  // ── Triple-quoted strings ─────────────────────────────────────────────────

  test("triple-quoted string") {
    assertEquals(extract("\"\"\"hello world\"\"\""), "\"\"\"hello world\"\"\"")
  }

  test("triple-quoted string with braces inside") {
    assertEquals(extract("\"\"\"{ not an expression }\"\"\""), "\"\"\"{ not an expression }\"\"\"")
  }

  // ── Character literals ────────────────────────────────────────────────────

  test("char literal") {
    assertEquals(extract("'a'"), "'a'")
  }

  test("escaped char literal") {
    assertEquals(extract("'\\n'"), "'\\n'")
  }

  // ── Block expressions ─────────────────────────────────────────────────────

  test("block expression with val definition") {
    assertEquals(extract("val x = 1; x + 2"), "val x = 1; x + 2")
  }

  test("if-then-else expression") {
    assertEquals(extract("if x > 0 then \"pos\" else \"non-pos\""), "if x > 0 then \"pos\" else \"non-pos\"")
  }

  test("if-then-else with braced branches") {
    assertEquals(extract("if flag then { a } else { b }"), "if flag then { a } else { b }")
  }

  // ── Empty expression ──────────────────────────────────────────────────────

  test("empty expression yields empty string") {
    val (expr, _) = ExprExtractor.extract("{}", 1)
    assertEquals(expr, "")
  }

  // ── Deeply nested structures ─────────────────────────────────────────────

  test("deeply nested braces (5 levels)") {
    assertEquals(extract("f({g({h({i({j()})})})})"), "f({g({h({i({j()})})})})")
  }

  test("nested block with multiple statements") {
    val expr = "val xs = List(1, 2, 3); xs.map(x => {x * 2}).sum"
    assertEquals(extract(expr), expr)
  }

  // ── Mixed content ─────────────────────────────────────────────────────────

  test("expression followed by text outside braces is not consumed") {
    val src         = "{count} items"
    val (expr, end) = ExprExtractor.extract(src, 1)
    assertEquals(expr, "count")
    // ' ' after '}' remains in src
    assertEquals(src.substring(end), " items")
  }

  test("multiline expression") {
    val multiline = "x +\n  y +\n  z"
    assertEquals(extract(multiline), multiline)
  }

  // ── Robustness (unclosed brace) ───────────────────────────────────────────

  test("unclosed brace consumes to end of input without throwing") {
    val src         = "{unclosed"
    val (expr, end) = ExprExtractor.extract(src, 1)
    // Should not throw; returns whatever was accumulated
    assertEquals(expr, "unclosed")
    assertEquals(end, src.length)
  }

  // ── Position tracking ─────────────────────────────────────────────────────

  test("end position advances past the closing brace") {
    val src         = "{count} remaining"
    val (expr, end) = ExprExtractor.extract(src, 1)
    assertEquals(expr, "count")
    assertEquals(end, 7) // points at ' ' after '}'
  }

  test("consecutive expressions can be extracted in sequence") {
    val src      = "{a}{b}"
    val (e1, p1) = ExprExtractor.extract(src, 1)
    assertEquals(e1, "a")
    assertEquals(p1, 3)
    val (e2, p2) = ExprExtractor.extract(src, p1 + 1)
    assertEquals(e2, "b")
    assertEquals(p2, 6)
  }
