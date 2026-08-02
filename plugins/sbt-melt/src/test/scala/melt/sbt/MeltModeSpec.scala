/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.sbt

/** Tests for [[MeltPlugin.resolveCodegenMode]] — codegen-mode validation so a
  * mistyped `meltCodegenMode` fails loudly instead of silently defaulting.
  */
class MeltModeSpec extends munit.FunSuite:

  test("\"spa\" and \"ssr\" resolve to themselves regardless of platform") {
    assertEquals(MeltPlugin.resolveCodegenMode("spa", hasScalaJSPlugin = false), Right("spa"))
    assertEquals(MeltPlugin.resolveCodegenMode("ssr", hasScalaJSPlugin = true), Right("ssr"))
  }

  test("\"auto\" picks spa with Scala.js and ssr without") {
    assertEquals(MeltPlugin.resolveCodegenMode("auto", hasScalaJSPlugin = true), Right("spa"))
    assertEquals(MeltPlugin.resolveCodegenMode("auto", hasScalaJSPlugin = false), Right("ssr"))
  }

  test("case and surrounding whitespace are tolerated") {
    assertEquals(MeltPlugin.resolveCodegenMode("  SPA ", hasScalaJSPlugin = false), Right("spa"))
    assertEquals(MeltPlugin.resolveCodegenMode("Auto", hasScalaJSPlugin = true), Right("spa"))
  }

  test("an unknown mode is rejected with a message naming the value and the valid options") {
    val result = MeltPlugin.resolveCodegenMode("sppa", hasScalaJSPlugin = false)
    assert(result.isLeft, s"expected Left, got $result")
    val msg = result.left.getOrElse("")
    assert(msg.contains("sppa"), s"message should name the bad value: $msg")
    assert(msg.contains("spa") && msg.contains("ssr") && msg.contains("auto"), s"message should list valid modes: $msg")
  }
