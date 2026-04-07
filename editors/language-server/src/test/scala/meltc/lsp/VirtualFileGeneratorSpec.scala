/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.lsp

class VirtualFileGeneratorSpec extends munit.FunSuite:

  // ── generate ─────────────────────────────────────────────────────────────

  test("script lines are preserved verbatim in the virtual file") {
    val source = """|<script lang="scala">
                    |  val count = Var(0)
                    |  def inc() = count.update(_ + 1)
                    |</script>""".stripMargin
    val vf     = VirtualFileGenerator.generate(source)
    val lines  = vf.content.split("\n", -1).toVector
    assertEquals(lines(1), "  val count = Var(0)")
    assertEquals(lines(2), "  def inc() = count.update(_ + 1)")
  }

  test("script tag lines become blank in the virtual file") {
    val source = """|<script lang="scala">
                    |  val x = 1
                    |</script>""".stripMargin
    val vf    = VirtualFileGenerator.generate(source)
    val lines = vf.content.split("\n", -1).toVector
    assertEquals(lines(0), "", "opening <script> tag line should be blank")
    assertEquals(lines(2), "", "closing </script> tag line should be blank")
  }

  test("template lines become blank in the virtual file") {
    val source = """|<script lang="scala">
                    |  val n = 42
                    |</script>
                    |<div>{n}</div>""".stripMargin
    val vf    = VirtualFileGenerator.generate(source)
    val lines = vf.content.split("\n", -1).toVector
    assertEquals(lines(3), "", "template line should be blank")
  }

  test("style lines become blank in the virtual file") {
    val source = """|<script lang="scala">
                    |  val x = 1
                    |</script>
                    |<style>
                    |  p { color: red; }
                    |</style>""".stripMargin
    val vf    = VirtualFileGenerator.generate(source)
    val lines = vf.content.split("\n", -1).toVector
    assertEquals(lines(3), "")
    assertEquals(lines(4), "")
    assertEquals(lines(5), "")
  }

  test("virtual file has the same number of lines as the source") {
    val source = """|<script lang="scala">
                    |  val a = 1
                    |  val b = 2
                    |</script>
                    |<div>{a}</div>""".stripMargin
    val vf         = VirtualFileGenerator.generate(source)
    val srcLines   = source.split("\n", -1).length
    val virtLines  = vf.content.split("\n", -1).length
    assertEquals(virtLines, srcLines)
  }

  test("file with no script section produces all-blank virtual file") {
    val source = "<div>Hello</div>"
    val vf     = VirtualFileGenerator.generate(source)
    assertEquals(vf.content, "")
  }

  test("plain <script> without lang=scala is not treated as script section") {
    val source = """|<script>
                    |  alert("hi")
                    |</script>""".stripMargin
    val vf    = VirtualFileGenerator.generate(source)
    val lines = vf.content.split("\n", -1).toVector
    assertEquals(lines(1), "", "plain <script> body should be blank")
  }

  // ── PositionMapper integration ────────────────────────────────────────────

  test("mapper.scriptRange covers the script body lines") {
    val source = """|<script lang="scala">
                    |  val x = 1
                    |  val y = 2
                    |</script>""".stripMargin
    val vf = VirtualFileGenerator.generate(source)
    // line 0 = <script>, line 1-2 = body, line 3 = </script>
    assertEquals(vf.mapper.scriptRange, Some(LineRange(1, 2)))
  }

  test("mapper.styleRange covers the style body lines") {
    val source = """|<div></div>
                    |<style>
                    |  p { color: red; }
                    |</style>""".stripMargin
    val vf = VirtualFileGenerator.generate(source)
    assertEquals(vf.mapper.styleRange, Some(LineRange(2, 2)))
  }

  test("mapper reports Script section for script body lines") {
    val source = """|<script lang="scala">
                    |  val x = 1
                    |</script>""".stripMargin
    val vf = VirtualFileGenerator.generate(source)
    assertEquals(vf.mapper.sectionAt(1), MeltSection.Script)
  }

  test("mapper reports Template section for template lines") {
    val source = """|<script lang="scala">
                    |  val x = 1
                    |</script>
                    |<div>{x}</div>""".stripMargin
    val vf = VirtualFileGenerator.generate(source)
    assertEquals(vf.mapper.sectionAt(3), MeltSection.Template)
  }

  test("mapper reports Style section for style body lines") {
    val source = """|<div></div>
                    |<style>
                    |  p { color: red; }
                    |</style>""".stripMargin
    val vf = VirtualFileGenerator.generate(source)
    assertEquals(vf.mapper.sectionAt(2), MeltSection.Style)
  }
