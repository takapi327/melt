/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.sbt

/** Tests for [[MeltGeneratedSource]] — parsing and line-mapping of the
  * `-- MELT GENERATED --` source-map comment block.
  */
class MeltGeneratedSourceSpec extends munit.FunSuite {

  // ── parse ─────────────────────────────────────────────────────────────────

  private val sampleBlock =
    """|object App { val x = 1 }
       |/*
       |    -- MELT GENERATED --
       |    SOURCE: /home/user/project/App.melt
       |    LINES: 10->5|15->8|20->12
       |    -- MELT GENERATED --
       |*/
       |""".stripMargin

  test("parse extracts sourcePath correctly") {
    val meta = MeltGeneratedSource.parse(sampleBlock)
    assert(meta.isDefined, "expected Some(Meta)")
    assertEquals(meta.get.sourcePath, "/home/user/project/App.melt")
  }

  test("parse extracts LINES entries correctly") {
    val meta = MeltGeneratedSource.parse(sampleBlock).get
    assertEquals(meta.lines.toList, List((10, 5), (15, 8), (20, 12)))
  }

  test("parse returns None when no block is present") {
    val result = MeltGeneratedSource.parse("object App { val x = 1 }")
    assert(result.isEmpty, "expected None for file without block")
  }

  test("parse returns None when only one sentinel is present") {
    val oneMarker = "object App {}\n// -- MELT GENERATED --\n"
    assert(MeltGeneratedSource.parse(oneMarker).isEmpty)
  }

  test("parse uses last occurrence when sentinel appears in user code") {
    // Simulates a user whose source code contains the sentinel as a string literal.
    val withUserLiteral =
      """|val s = "-- MELT GENERATED --"
         |object App {}
         |/*
         |    -- MELT GENERATED --
         |    SOURCE: /tmp/Real.melt
         |    LINES: 5->3
         |    -- MELT GENERATED --
         |*/
         |""".stripMargin
    val meta = MeltGeneratedSource.parse(withUserLiteral)
    assert(meta.isDefined, "should still find the real block")
    assertEquals(meta.get.sourcePath, "/tmp/Real.melt")
  }

  test("parse handles empty LINES gracefully") {
    val noLines =
      """|object App {}
         |/*
         |    -- MELT GENERATED --
         |    SOURCE: /tmp/App.melt
         |    LINES:
         |    -- MELT GENERATED --
         |*/
         |""".stripMargin
    val meta = MeltGeneratedSource.parse(noLines).get
    assert(meta.lines.isEmpty)
  }

  // ── mapLine ───────────────────────────────────────────────────────────────

  private def meta(pairs: (Int, Int)*): MeltGeneratedSource.Meta =
    MeltGeneratedSource.Meta("/tmp/App.melt", pairs.toIndexedSeq)

  test("mapLine returns None for empty lines") {
    assertEquals(MeltGeneratedSource.mapLine(meta(), 5), None)
  }

  test("mapLine returns None when generatedLine is before first entry") {
    assertEquals(MeltGeneratedSource.mapLine(meta(10 -> 5), 9), None)
  }

  test("mapLine returns sourceLine for exact match") {
    assertEquals(MeltGeneratedSource.mapLine(meta(10 -> 5), 10), Some(5))
  }

  test("mapLine returns nearest-predecessor sourceLine") {
    val m = meta(10 -> 5, 20 -> 8, 30 -> 12)
    assertEquals(MeltGeneratedSource.mapLine(m, 15), Some(5))
    assertEquals(MeltGeneratedSource.mapLine(m, 25), Some(8))
    assertEquals(MeltGeneratedSource.mapLine(m, 35), Some(12))
  }

  test("mapLine returns sourceLine for last entry when generatedLine exceeds all") {
    val m = meta(10 -> 5, 20 -> 8)
    assertEquals(MeltGeneratedSource.mapLine(m, 99), Some(8))
  }

  test("mapLine works with single entry") {
    assertEquals(MeltGeneratedSource.mapLine(meta(5 -> 3), 5), Some(3))
    assertEquals(MeltGeneratedSource.mapLine(meta(5 -> 3), 6), Some(3))
    assertEquals(MeltGeneratedSource.mapLine(meta(5 -> 3), 4), None)
  }
}
