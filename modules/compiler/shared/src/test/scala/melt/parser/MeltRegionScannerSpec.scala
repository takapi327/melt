/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.parser

import melt.parser.MeltRegionScanner.{ Region, RegionKind }
import melt.preprocessor.StyleLang

class MeltRegionScannerSpec extends munit.FunSuite:

  private def scan(src: String): List[Region] =
    MeltRegionScanner.scan(src) match
      case Right(rs) => rs
      case Left(err) => fail(s"unexpected Left: $err")

  private def inner(src: String, r: Region): String = src.substring(r.innerStart, r.innerEnd)

  test("finds instance script + style with exact inner spans") {
    val src =
      """<script lang="scala">
        |val x = 1
        |</script>
        |<div>{x}</div>
        |<style>
        |.a { color: red; }
        |</style>
        |""".stripMargin
    val rs = scan(src)
    assertEquals(rs.map(_.kind), List(RegionKind.InstanceScript, RegionKind.Template, RegionKind.Style))
    assertEquals(inner(src, rs(0)), "\nval x = 1\n")
    assertEquals(inner(src, rs(1)), "<div>{x}</div>") // template, trimmed to content
    assertEquals(inner(src, rs(2)), "\n.a { color: red; }\n")
    assertEquals(rs(2).styleLang, Some(StyleLang.Css))
  }

  test("finds module + instance script and reports source order") {
    val src =
      """<script lang="scala" module>
        |val shared = 1
        |</script>
        |<script lang="scala">
        |val local = 2
        |</script>
        |<p/>
        |""".stripMargin
    val rs = scan(src)
    assertEquals(
      rs.map(_.kind),
      List(RegionKind.ModuleScript, RegionKind.InstanceScript, RegionKind.Template)
    )
    assertEquals(inner(src, rs(0)).trim, "val shared = 1")
    assertEquals(inner(src, rs(1)).trim, "val local = 2")
    assertEquals(inner(src, rs(2)), "<p/>") // template
  }

  test("detects scss lang") {
    val src = """<style lang="scss">.a{&:hover{color:red}}</style>"""
    val rs  = scan(src)
    assertEquals(rs.map(_.styleLang), List(Some(StyleLang.Scss)))
  }

  test("ignores <style scoped> (non-lang attribute) — it stays part of the template") {
    val src = """<div></div><style scoped>.a{}</style>"""
    val rs  = scan(src)
    // <style scoped> is not a Style region; the whole thing is one template.
    assertEquals(rs.map(_.kind), List(RegionKind.Template))
    assertEquals(inner(src, rs.head), src)
  }

  test("template-only file → one Template region spanning the content") {
    val src = "  <div>{x}</div>\n"
    val rs  = scan(src)
    assertEquals(rs.map(_.kind), List(RegionKind.Template))
    assertEquals(inner(src, rs.head), "<div>{x}</div>") // trimmed
  }

  test("no Template region when the leftover is whitespace only") {
    val src =
      """<script lang="scala">
        |val x = 1
        |</script>
        |<style>.a{}</style>
        |""".stripMargin
    val rs = scan(src)
    assertEquals(rs.map(_.kind), List(RegionKind.InstanceScript, RegionKind.Style))
  }

  test("does not treat a <style in a script string as a region") {
    val src =
      """<script lang="scala">
        |val s = "<style>x</style>"
        |</script>
        |""".stripMargin
    val rs = scan(src)
    assertEquals(rs.map(_.kind), List(RegionKind.InstanceScript))
  }

  test("round-trip: identity splice reproduces the original byte-for-byte") {
    val src =
      """<script lang="scala" module>
        |  val shared = 1
        |</script>
        |
        |<script lang="scala">
        |  val x    = State(0)
        |  doThing(x)
        |</script>
        |
        |<div class="c">{x}</div>
        |
        |<style>
        |  .c { color: red;
        |       font-size: 12px; }
        |</style>
        |""".stripMargin
    val rs      = scan(src)
    val spliced = rs
      .sortBy(-_.innerStart)
      .foldLeft(src) { (acc, r) =>
        acc.substring(0, r.innerStart) + acc.substring(r.innerStart, r.innerEnd) + acc.substring(r.innerEnd)
      }
    assertEquals(spliced, src)
  }

  test("Left on duplicate module scripts") {
    val src =
      """<script lang="scala" module>val a=1</script>
        |<script lang="scala" module>val b=2</script>""".stripMargin
    assert(MeltRegionScanner.scan(src).isLeft)
  }

  test("Left on unclosed script tag") {
    assert(MeltRegionScanner.scan("""<script lang="scala">val x = 1""").isLeft)
  }
