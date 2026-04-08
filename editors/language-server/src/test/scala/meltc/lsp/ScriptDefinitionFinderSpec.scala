/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.lsp

class ScriptDefinitionFinderSpec extends munit.FunSuite:

  private val uri = "file:///tmp/Test.melt"

  private def find(source: String, line: Int, char: Int): List[org.eclipse.lsp4j.Location] =
    val vf = VirtualFileGenerator.generate(source)
    ScriptDefinitionFinder.find(source, uri, line, char, vf)

  // ── val definition ────────────────────────────────────────────────────────

  test("finds val definition from template reference") {
    val source =
      """|<script lang="scala">
         |  val count = Var(0)
         |</script>
         |<div>{count}</div>""".stripMargin
    // line 3, "count" starts at char 6
    val locs = find(source, line = 3, char = 8)
    assertEquals(locs.size, 1)
    assertEquals(locs.head.getUri, uri)
    assertEquals(locs.head.getRange.getStart.getLine, 1)
  }

  test("definition location character points to the identifier start") {
    val source =
      """|<script lang="scala">
         |  val count = Var(0)
         |</script>
         |<div>{count}</div>""".stripMargin
    val locs      = find(source, line = 3, char = 8)
    val startChar = locs.head.getRange.getStart.getCharacter
    // "  val count" → "count" starts at char 6
    assertEquals(startChar, 6)
  }

  // ── def definition ────────────────────────────────────────────────────────

  test("finds def definition from template reference") {
    val source =
      """|<script lang="scala">
         |  def increment(): Unit = ()
         |</script>
         |<button onclick={increment()}>+</button>""".stripMargin
    val locs = find(source, line = 3, char = 18)
    assertEquals(locs.size, 1)
    assertEquals(locs.head.getRange.getStart.getLine, 1)
  }

  // ── no match ─────────────────────────────────────────────────────────────

  test("returns empty when identifier is not defined in script") {
    val source =
      """|<script lang="scala">
         |  val x = 1
         |</script>
         |<div>{unknown}</div>""".stripMargin
    val locs = find(source, line = 3, char = 6)
    assertEquals(locs, Nil)
  }

  test("returns empty when cursor is not on an identifier") {
    val source =
      """|<script lang="scala">
         |  val count = Var(0)
         |</script>
         |<div>{count}</div>""".stripMargin
    // cursor on '<' which is not an identifier char
    val locs = find(source, line = 3, char = 0)
    assertEquals(locs, Nil)
  }

  test("returns empty for pure-digit token") {
    val source =
      """|<script lang="scala">
         |  val x = 42
         |</script>
         |<p>{42}</p>""".stripMargin
    val locs = find(source, line = 3, char = 4)
    assertEquals(locs, Nil)
  }

  // ── multiple definitions ──────────────────────────────────────────────────

  test("finds correct definition when multiple vals exist") {
    val source =
      """|<script lang="scala">
         |  val alpha = 1
         |  val beta  = 2
         |</script>
         |<p>{beta}</p>""".stripMargin
    val locs = find(source, line = 4, char = 4)
    assertEquals(locs.size, 1)
    assertEquals(locs.head.getRange.getStart.getLine, 2)
  }

  // ── cursor at different positions within identifier ────────────────────────

  test("finds definition when cursor is at start of identifier") {
    val source =
      """|<script lang="scala">
         |  val count = Var(0)
         |</script>
         |<div>{count}</div>""".stripMargin
    // cursor at first char of "count" (index 6 in line 3)
    val locs = find(source, line = 3, char = 6)
    assertEquals(locs.size, 1)
  }

  test("finds definition when cursor is at end of identifier") {
    val source =
      """|<script lang="scala">
         |  val count = Var(0)
         |</script>
         |<div>{count}</div>""".stripMargin
    // cursor at last char of "count" (index 10 in line 3)
    val locs = find(source, line = 3, char = 10)
    assertEquals(locs.size, 1)
  }
