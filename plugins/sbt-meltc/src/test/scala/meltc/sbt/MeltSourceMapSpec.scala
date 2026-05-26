/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.sbt

import java.io.File
import java.nio.file.Files
import java.util.Optional

import xsbti.Position

/** Tests for [[MeltSourceMap.positionMapper]].
  *
  * Each test uses temporary files so the cache key `(file, lastModified)` is
  * unique per test, avoiding cross-test interference from the static cache.
  */
class MeltSourceMapSpec extends munit.FunSuite {

  // ── Helpers ───────────────────────────────────────────────────────────────

  /** Creates a mock [[Position]] pointing to `file` at `lineNumber`. */
  private def makePosition(file: File, lineNumber: Int): Position =
    new Position {
      override def line():         Optional[Integer] = Optional.of(lineNumber.asInstanceOf[Integer])
      override def lineContent():  String            = ""
      override def offset():       Optional[Integer] = Optional.empty()
      override def pointer():      Optional[Integer] = Optional.empty()
      override def pointerSpace(): Optional[String]  = Optional.empty()
      override def sourcePath():   Optional[String]  = Optional.of(file.getAbsolutePath)
      override def sourceFile():   Optional[File]    = Optional.of(file)
    }

  /** Creates a mock [[Position]] with no source file (synthetic position). */
  private def makePositionNoFile(): Position =
    new Position {
      override def line():         Optional[Integer] = Optional.of(5.asInstanceOf[Integer])
      override def lineContent():  String            = ""
      override def offset():       Optional[Integer] = Optional.empty()
      override def pointer():      Optional[Integer] = Optional.empty()
      override def pointerSpace(): Optional[String]  = Optional.empty()
      override def sourcePath():   Optional[String]  = Optional.empty()
      override def sourceFile():   Optional[File]    = Optional.empty()
    }

  /** Writes `content` to a temp file with the given suffix and returns the file. */
  private def tempFile(suffix: String, content: String): File = {
    val f = File.createTempFile("MeltSourceMapSpec", suffix)
    f.deleteOnExit()
    Files.write(f.toPath, content.getBytes("UTF-8"))
    f
  }

  // ── Test-local V3 helpers ─────────────────────────────────────────────────

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

  /** Builds a generated `.scala` file content with a V3 source-map block. */
  private def scalaContent(meltPath: String, entries: (Int, Int, Int)*): String = {
    val mappings = encodeMappings(entries)
    val escaped  = meltPath.replace("\\", "\\\\").replace("\"", "\\\"")
    val json     = s"""{"version":3,"sources":["$escaped"],"names":[],"mappings":"$mappings"}"""
    val b64      = java.util.Base64.getEncoder.encodeToString(json.getBytes("UTF-8"))
    s"""|object App {}
        |/*
        |    -- MELT GENERATED --
        |    SOURCE: $meltPath
        |    V3: $b64
        |    -- MELT GENERATED --
        |*/
        |""".stripMargin
  }

  // ── positionMapper ────────────────────────────────────────────────────────

  test("positionMapper returns None when sourceFile is absent") {
    val result = MeltSourceMap.positionMapper(makePositionNoFile())
    assert(result.isEmpty, "expected None when no sourceFile present")
  }

  test("positionMapper returns None for non-.scala source file") {
    val meltFile = tempFile(".melt", "<div></div>")
    val pos      = makePosition(meltFile, 5)
    val result   = MeltSourceMap.positionMapper(pos)
    assert(result.isEmpty, "expected None for a .melt file (not a generated .scala)")
  }

  test("positionMapper returns None when no MELT GENERATED block in file") {
    val scalaFile = tempFile(".scala", "object Plain { val x = 1 }")
    val pos       = makePosition(scalaFile, 1)
    val result    = MeltSourceMap.positionMapper(pos)
    assert(result.isEmpty, "expected None when generated file has no source-map block")
  }

  test("positionMapper remaps generated line to .melt source line and column") {
    val meltFile  = tempFile(".melt", "<script lang=\"scala\">val x = 1\n</script>\n<div></div>")
    val scalaFile = tempFile(".scala", scalaContent(meltFile.getAbsolutePath, (5, 3, 1), (10, 7, 4)))

    val pos    = makePosition(scalaFile, 10)
    val result = MeltSourceMap.positionMapper(pos)
    assert(result.isDefined, "expected Some(remapped position)")
    assert(result.get.line().isPresent, "remapped position should have a line")
    assertEquals(result.get.line().get().intValue(), 7)
    // pointer() is 0-based: column 4 → pointer 3
    assertEquals(result.get.pointer().get().intValue(), 3)
    assertEquals(result.get.sourceFile().get().getAbsolutePath, meltFile.getAbsolutePath)
    assertEquals(result.get.sourcePath().get(), meltFile.getAbsolutePath)
  }

  test("positionMapper uses nearest-predecessor entry for between-mapped lines") {
    val meltFile  = tempFile(".melt", "")
    val scalaFile = tempFile(".scala", scalaContent(meltFile.getAbsolutePath, (5, 3, 1), (15, 8, 2), (25, 12, 5)))

    // Line 18 is between entries 15 and 25 → should map to entry 15's source (8, col 2)
    val pos    = makePosition(scalaFile, 18)
    val result = MeltSourceMap.positionMapper(pos)
    assert(result.isDefined, "expected Some(remapped position)")
    assertEquals(result.get.line().get().intValue(), 8)
    assertEquals(result.get.pointer().get().intValue(), 1) // col 2 → pointer 1
  }

  test("positionMapper returns None when generated line is before first entry") {
    val meltFile  = tempFile(".melt", "")
    val scalaFile = tempFile(".scala", scalaContent(meltFile.getAbsolutePath, (10, 5, 1)))

    val pos    = makePosition(scalaFile, 3) // line 3 < first entry line 10
    val result = MeltSourceMap.positionMapper(pos)
    assert(result.isEmpty, "expected None when line is before the first entry")
  }

  test("positionMapper returns None when .melt source file does not exist") {
    val scalaFile =
      tempFile(".scala", scalaContent("/nonexistent/path/Missing.melt", (5, 3, 1)))

    val pos    = makePosition(scalaFile, 5)
    val result = MeltSourceMap.positionMapper(pos)
    assert(result.isEmpty, "expected None when the .melt source file does not exist")
  }

  test("positionMapper returns None when generated position has no line number") {
    val meltFile  = tempFile(".melt", "")
    val scalaFile = tempFile(".scala", scalaContent(meltFile.getAbsolutePath, (5, 3, 2)))

    val pos = new Position {
      override def line():         Optional[Integer] = Optional.empty() // no line
      override def lineContent():  String            = ""
      override def offset():       Optional[Integer] = Optional.empty()
      override def pointer():      Optional[Integer] = Optional.empty()
      override def pointerSpace(): Optional[String]  = Optional.empty()
      override def sourcePath():   Optional[String]  = Optional.of(scalaFile.getAbsolutePath)
      override def sourceFile():   Optional[File]    = Optional.of(scalaFile)
    }
    val result = MeltSourceMap.positionMapper(pos)
    assert(result.isEmpty, "expected None when position has no line number")
  }
}
