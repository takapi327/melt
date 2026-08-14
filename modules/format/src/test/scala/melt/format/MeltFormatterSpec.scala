/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.format

import melt.parser.MeltRegionScanner.RegionKind

class MeltFormatterSpec extends munit.FunSuite:

  private val sample =
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
      |  .c { color: red; }
      |</style>
      |""".stripMargin

  test("identity formatSection reproduces the source byte-for-byte") {
    assertEquals(MeltFormatter.format(sample), Right(sample))
  }

  test("splices formatted inner text while leaving everything else untouched") {
    // Replace each section's inner text with a marker; assert only the inner
    // spans changed and tags/template survive verbatim.
    val out = MeltFormatter
      .format(
        sample,
        {
          case (RegionKind.ModuleScript, _)   => "M"
          case (RegionKind.InstanceScript, _) => "I"
          case (RegionKind.Style, _)          => "S"
        }
      )
      .fold(err => fail(err), identity)

    val expected =
      """<script lang="scala" module>M</script>
        |
        |<script lang="scala">I</script>
        |
        |<div class="c">{x}</div>
        |
        |<style>S</style>
        |""".stripMargin
    assertEquals(out, expected)
  }

  test("only Style section is transformed when a transform targets Style") {
    val out = MeltFormatter
      .format(sample, (kind, inner) => if kind == RegionKind.Style then inner.toUpperCase else inner)
      .fold(err => fail(err), identity)
    assert(out.contains("COLOR: RED"), out)          // style uppercased
    assert(out.contains("val x    = State(0)"), out) // script untouched
  }

  test("propagates Left from the scanner on malformed input") {
    assert(MeltFormatter.format("""<script lang="scala">oops""").isLeft)
  }

  // Definitive Phase-0 round-trip: identity-format every real `.melt` in the
  // repo and assert byte-for-byte identity. sbt runs tests from the build root,
  // so `examples`/`docs` resolve relative to it; if not found, the check is a
  // no-op rather than a false failure.
  test("round-trip: identity format leaves every real .melt file unchanged") {
    def meltFiles(dir: java.io.File): List[java.io.File] =
      if !dir.isDirectory then Nil
      else
        val here = Option(dir.listFiles).getOrElse(Array.empty[java.io.File]).toList
        here.flatMap {
          case d if d.isDirectory               => meltFiles(d)
          case f if f.getName.endsWith(".melt") => List(f)
          case _                                => Nil
        }

    val files = List("examples", "docs").flatMap(d => meltFiles(new java.io.File(d)))
    assume(files.nonEmpty, "no .melt files found relative to cwd; skipping real-file round-trip")

    val broken = files.flatMap { f =>
      val src  = scala.io.Source.fromFile(f, "UTF-8")
      val text =
        try src.mkString
        finally src.close()
      MeltFormatter.format(text) match
        case Right(out) if out == text => None
        case Right(_)                  => Some(s"${ f.getPath }: identity format changed bytes")
        case Left(err)                 => Some(s"${ f.getPath }: scan Left: $err")
    }
    assert(broken.isEmpty, s"${ broken.size } file(s) failed round-trip:\n${ broken.mkString("\n") }")
  }
