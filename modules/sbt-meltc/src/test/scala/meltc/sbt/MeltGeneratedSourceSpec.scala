/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.sbt

/** Tests for [[MeltGeneratedSource]] — parsing and position-mapping of the
  * `-- MELT GENERATED --` source-map comment block.
  */
class MeltGeneratedSourceSpec extends munit.FunSuite {

  // ── parse ─────────────────────────────────────────────────────────────────

  private val sampleBlock =
    """|object App { val x = 1 }
       |/*
       |    -- MELT GENERATED --
       |    SOURCE: /home/user/project/App.melt
       |    LINES: 10->5:3|15->8:1|20->12:7
       |    -- MELT GENERATED --
       |*/
       |""".stripMargin

  test("parse extracts sourcePath correctly") {
    val meta = MeltGeneratedSource.parse(sampleBlock)
    assert(meta.isDefined, "expected Some(Meta)")
    assertEquals(meta.get.sourcePath, "/home/user/project/App.melt")
  }

  test("parse extracts LINES entries with column correctly") {
    val meta = MeltGeneratedSource.parse(sampleBlock).get
    assertEquals(meta.lines.toList, List((10, 5, 3), (15, 8, 1), (20, 12, 7)))
  }

  test("parse falls back to column 1 when no colon in entry") {
    val block =
      """|object App {}
         |/*
         |    -- MELT GENERATED --
         |    SOURCE: /tmp/App.melt
         |    LINES: 10->5|15->8
         |    -- MELT GENERATED --
         |*/
         |""".stripMargin
    val meta = MeltGeneratedSource.parse(block).get
    assertEquals(meta.lines.toList, List((10, 5, 1), (15, 8, 1)))
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
    val withUserLiteral =
      """|val s = "-- MELT GENERATED --"
         |object App {}
         |/*
         |    -- MELT GENERATED --
         |    SOURCE: /tmp/Real.melt
         |    LINES: 5->3:2
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

  // ── mapPosition ───────────────────────────────────────────────────────────

  private def meta(triples: (Int, Int, Int)*): MeltGeneratedSource.Meta =
    MeltGeneratedSource.Meta("/tmp/App.melt", triples.toIndexedSeq)

  test("mapPosition returns None for empty lines") {
    assertEquals(MeltGeneratedSource.mapPosition(meta(), 5), None)
  }

  test("mapPosition returns None when generatedLine is before first entry") {
    assertEquals(MeltGeneratedSource.mapPosition(meta((10, 5, 1)), 9), None)
  }

  test("mapPosition returns (sourceLine, sourceColumn) for exact match") {
    assertEquals(MeltGeneratedSource.mapPosition(meta((10, 5, 3)), 10), Some((5, 3)))
  }

  test("mapPosition returns nearest-predecessor entry") {
    val m = meta((10, 5, 1), (20, 8, 4), (30, 12, 2))
    assertEquals(MeltGeneratedSource.mapPosition(m, 15), Some((5, 1)))
    assertEquals(MeltGeneratedSource.mapPosition(m, 25), Some((8, 4)))
    assertEquals(MeltGeneratedSource.mapPosition(m, 35), Some((12, 2)))
  }

  test("mapPosition returns last entry when generatedLine exceeds all") {
    val m = meta((10, 5, 1), (20, 8, 3))
    assertEquals(MeltGeneratedSource.mapPosition(m, 99), Some((8, 3)))
  }

  test("mapPosition works with single entry") {
    assertEquals(MeltGeneratedSource.mapPosition(meta((5, 3, 2)), 5), Some((3, 2)))
    assertEquals(MeltGeneratedSource.mapPosition(meta((5, 3, 2)), 6), Some((3, 2)))
    assertEquals(MeltGeneratedSource.mapPosition(meta((5, 3, 2)), 4), None)
  }
}
