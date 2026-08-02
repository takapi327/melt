/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.lsp

/** Tests for [[DiagnosticRange.locate]] — narrowing the diagnostic squiggle from
  * whole-line to the offending token when possible.
  */
class DiagnosticRangeSpec extends munit.FunSuite:

  test("underlines a quoted token found on the source line") {
    val line = """  <input name="emial"/>"""
    assertEquals(DiagnosticRange.locate("Form model has no field 'emial'", 0, line), (15, 20))
  }

  test("supports backtick- and double-quoted tokens in the message") {
    val line = "  val count = 1"
    assertEquals(DiagnosticRange.locate("Unused `count`", 0, line), (6, 11))
    assertEquals(DiagnosticRange.locate("""bad "count" here""", 0, line), (6, 11))
  }

  test("falls back to the whole line when the token is not present") {
    val line = "<div>hello</div>"
    assertEquals(DiagnosticRange.locate("something about 'missing'", 0, line), (0, line.length))
  }

  test("falls back to the whole line when the message has no quoted token") {
    val line = "<div>hello</div>"
    assertEquals(DiagnosticRange.locate("generic error", 0, line), (0, line.length))
  }

  test("uses a real 1-based column when the compiler supplies one") {
    val line = "abcdefghij"
    assertEquals(DiagnosticRange.locate("err", 4, line), (3, line.length))
  }

  test("empty source line yields a zero-width range") {
    assertEquals(DiagnosticRange.locate("err", 0, ""), (0, 0))
  }
