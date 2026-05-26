/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.sbt

/** Tests for [[MeltGeneratedSource]] — parsing and position-mapping of the
  * `-- MELT GENERATED --` source-map comment block.
  */
class MeltGeneratedSourceSpec extends munit.FunSuite {

  // ── Test-local V3 helpers ──────────────────────────────────────────────────

  private val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

  private def encodeVlq(n: Int): String = {
    val sb      = new StringBuilder
    var encoded = if (n < 0) ((-n) << 1) | 1 else n << 1
    while ({
      val digit = encoded & 0x1f
      encoded >>>= 5
      sb += B64(if (encoded > 0) digit | 0x20 else digit)
      encoded > 0
    }) ()
    sb.toString
  }

  private def encodeMappings(entries: Seq[(Int, Int, Int)]): String = {
    if (entries.isEmpty) return ""
    val sb          = new StringBuilder
    val lastGenLine = entries.last._1
    val byLine      = entries.map(e => e._1 -> e).toMap
    var prevSrcLine = 0
    var prevSrcCol  = 0
    (1 to lastGenLine).foreach { genLine =>
      if (genLine > 1) sb += ';'
      byLine.get(genLine).foreach {
        case (_, srcLine, srcCol) =>
          val sl = srcLine - 1
          val sc = srcCol - 1
          sb ++= encodeVlq(0)
          sb ++= encodeVlq(0)
          sb ++= encodeVlq(sl - prevSrcLine)
          sb ++= encodeVlq(sc - prevSrcCol)
          prevSrcLine = sl
          prevSrcCol  = sc
      }
    }
    sb.toString
  }

  /** Builds a `-- MELT GENERATED --` block with a V3 source map. */
  private def makeBlock(sourcePath: String, entries: (Int, Int, Int)*): String = {
    val mappings = encodeMappings(entries)
    val escaped  = sourcePath.replace("\\", "\\\\").replace("\"", "\\\"")
    val json     = s"""{"version":3,"sources":["$escaped"],"names":[],"mappings":"$mappings"}"""
    val b64      = java.util.Base64.getEncoder.encodeToString(json.getBytes("UTF-8"))
    s"""|object App { val x = 1 }
        |/*
        |    -- MELT GENERATED --
        |    SOURCE: $sourcePath
        |    V3: $b64
        |    -- MELT GENERATED --
        |*/
        |""".stripMargin
  }

  // ── parse ─────────────────────────────────────────────────────────────────

  private val sampleBlock = makeBlock("/home/user/project/App.melt", (10, 5, 3), (15, 8, 1), (20, 12, 7))

  test("parse extracts sourcePath correctly") {
    val meta = MeltGeneratedSource.parse(sampleBlock)
    assert(meta.isDefined, "expected Some(Meta)")
    assertEquals(meta.get.sourcePath, "/home/user/project/App.melt")
  }

  test("parse extracts V3 entries correctly") {
    val meta = MeltGeneratedSource.parse(sampleBlock).get
    assertEquals(meta.lines.toList, List((10, 5, 3), (15, 8, 1), (20, 12, 7)))
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
    val block           = makeBlock("/tmp/Real.melt", (5, 3, 2))
    val withUserLiteral =
      s"""|val s = "-- MELT GENERATED --"
          |$block""".stripMargin
    val meta = MeltGeneratedSource.parse(withUserLiteral)
    assert(meta.isDefined, "should still find the real block")
    assertEquals(meta.get.sourcePath, "/tmp/Real.melt")
  }

  test("parse returns empty lines when V3 key is absent") {
    val noV3 =
      """|object App {}
         |/*
         |    -- MELT GENERATED --
         |    SOURCE: /tmp/App.melt
         |    -- MELT GENERATED --
         |*/
         |""".stripMargin
    val meta = MeltGeneratedSource.parse(noV3).get
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
