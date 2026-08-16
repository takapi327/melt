/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.format

import melt.css.{ CssFormatter, CssParser }

class CssFormatterSpec extends munit.FunSuite:

  private def fmt(css: String, opts: CssFormatter.Options = CssFormatter.Options()): String =
    CssFormatter.format(CssParser.parse(css), opts)

  private def check(name: String)(input: String, expected: String): Unit =
    test(name) {
      assertNoDiff(fmt(input), expected)
      // Idempotency: formatting the output again is a no-op.
      assertNoDiff(fmt(expected), expected)
    }

  // ── Golden fixtures ─────────────────────────────────────────────────────────

  check("simple rule")(
    ".a{color:red}",
    """.a {
      |  color: red;
      |}""".stripMargin
  )

  check("multiple declarations, significant whitespace preserved")(
    ".b{margin:0 auto;padding:  1px   2px;}",
    """.b {
      |  margin: 0 auto;
      |  padding: 1px 2px;
      |}""".stripMargin
  )

  check("nested rule (CSS Nesting)")(
    ".c{color:red;&:hover{color:blue}}",
    """.c {
      |  color: red;
      |  &:hover {
      |    color: blue;
      |  }
      |}""".stripMargin
  )

  check("@media block")(
    "@media (min-width: 700px){.a{color:red}}",
    """@media (min-width: 700px) {
      |  .a {
      |    color: red;
      |  }
      |}""".stripMargin
  )

  check("bodyless @import")(
    """@import "x.css";""",
    """@import "x.css";"""
  )

  check("@keyframes body is re-indented verbatim (passthrough)")(
    """@keyframes spin {
      |      from { transform: rotate(0); }
      |      to { transform: rotate(360deg); }
      |}""".stripMargin,
    """@keyframes spin {
      |  from { transform: rotate(0); }
      |  to { transform: rotate(360deg); }
      |}""".stripMargin
  )

  check("top-level comment kept, blank line before rule")(
    "/* hi */\n.a{color:red}",
    """/* hi */
      |
      |.a {
      |  color: red;
      |}""".stripMargin
  )

  check("custom property value kept verbatim")(
    ".a{--Grid:  1fr   2fr ;color:red}",
    """.a {
      |  --Grid: 1fr   2fr;
      |  color: red;
      |}""".stripMargin
  )

  check("!important normalised to single space")(
    ".a{color:red   !important}",
    """.a {
      |  color: red !important;
      |}""".stripMargin
  )

  check("selector list → one per line (default)")(
    ".a,.b ,  .c{color:red}",
    """.a,
      |.b,
      |.c {
      |  color: red;
      |}""".stripMargin
  )

  check("combinators get single space on each side")(
    ".a>.b+.c~.d{color:red}",
    """.a > .b + .c ~ .d {
      |  color: red;
      |}""".stripMargin
  )

  // ── Selector-destruction regressions (round 4) ──────────────────────────────

  check("attribute operator ~= is not spaced")(
    "[a~=b]{x:1}",
    """[a~=b] {
      |  x: 1;
      |}""".stripMargin
  )

  check("quoted attribute value whitespace untouched")(
    """[title="two  words"]{x:1}""",
    """[title="two  words"] {
      |  x: 1;
      |}""".stripMargin
  )

  check(":is() inner comma is not split")(
    ":is(.a, .b){x:1}",
    """:is(.a, .b) {
      |  x: 1;
      |}""".stripMargin
  )

  check(":not() inner comma is not split")(
    ":not(.a, .b){x:1}",
    """:not(.a, .b) {
      |  x: 1;
      |}""".stripMargin
  )

  check(":nth-child(2n+1) + is not spaced")(
    ":nth-child(2n+1){x:1}",
    """:nth-child(2n+1) {
      |  x: 1;
      |}""".stripMargin
  )

  // ── Options ─────────────────────────────────────────────────────────────────

  test("selectorList = single-line keeps the list on one line") {
    assertNoDiff(
      fmt(".a,.b,.c{color:red}", CssFormatter.Options(selectorList = CssFormatter.SelectorList.SingleLine)),
      """.a, .b, .c {
        |  color: red;
        |}""".stripMargin
    )
  }

  test("indent option changes nesting width") {
    assertNoDiff(
      fmt(".a{color:red}", CssFormatter.Options(indent = 4)),
      """.a {
        |    color: red;
        |}""".stripMargin
    )
  }

  // ── Idempotency over a combined fixture ─────────────────────────────────────

  test("idempotent over a combined stylesheet") {
    val input =
      """/* header */
        |.card , .panel{
        |  color:  red ;
        |  margin:0 auto;
        |  &:hover{ color:blue }
        |}
        |@media (min-width: 600px){ .card{ padding:1rem } }""".stripMargin
    val once  = fmt(input)
    val twice = fmt(once)
    assertNoDiff(twice, once)
  }
