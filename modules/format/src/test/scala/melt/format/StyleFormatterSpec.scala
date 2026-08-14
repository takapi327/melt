/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.format

import melt.css.CssFormatter
import melt.preprocessor.StyleLang

class StyleFormatterSpec extends munit.FunSuite:

  private def fmt(inner: String, lang: Option[StyleLang] = Some(StyleLang.Css)): String =
    StyleFormatter.format(inner, lang, CssFormatter.Options()) match
      case Right(s)  => s
      case Left(err) => fail(s"unexpected Left: $err")

  test("wraps formatted CSS with newline + left margin (like <script>)") {
    assertNoDiff(
      fmt("\n  .c{color:red}\n"),
      "\n  .c {\n    color: red;\n  }\n"
    )
  }

  test("SCSS is passed through byte-for-byte") {
    val scss = "\n  $c: red;\n  .c { color: $c }\n"
    assertEquals(fmt(scss, Some(StyleLang.Scss)), scss)
  }

  test("empty / whitespace-only style is left as-is") {
    assertEquals(fmt("\n   \n"), "\n   \n")
    assertEquals(fmt(""), "")
  }

  test("comment-only style is preserved") {
    assertNoDiff(fmt("\n  /* note */\n"), "\n  /* note */\n")
  }

  test("idempotent") {
    val once  = fmt("\n.a,.b{margin:0 auto;&:hover{color:red}}\n")
    val twice = fmt(once)
    assertEquals(twice, once)
  }

  test("indent option feeds both nesting and left margin") {
    assertNoDiff(
      StyleFormatter.format("\n.c{color:red}\n", Some(StyleLang.Css), CssFormatter.Options(indent = 4))
        .fold(fail(_), identity),
      "\n    .c {\n        color: red;\n    }\n"
    )
  }
