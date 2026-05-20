/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.sbt

import java.io.File

/** Reads and parses the `-- MELT GENERATED --` source-map comment block
  * that meltc appends to every generated `.scala` file.
  *
  * Block format (appended after the Scala code):
  * {{{
  * /*
  *     -- MELT GENERATED --
  *     SOURCE: /abs/path/to/Counter.melt
  *     LINES: 18->8|19->8|25->9|42->9
  *     -- MELT GENERATED --
  * */
  * }}}
  *
  * `LINES` encodes `(generatedLine -> sourceLine)` pairs in ascending
  * generated-line order, pipe-delimited.  The format is intentionally
  * compatible with Twirl's `@LINES` section for tooling interoperability.
  */
object MeltGeneratedSource {

  /** Parsed metadata from the `-- MELT GENERATED --` block. */
  final case class Meta(
    sourcePath: String,
    lines:      IndexedSeq[(Int, Int, Int)] // (generatedLine, sourceLine, sourceColumn), ascending
  )

  private val BlockStart = "-- MELT GENERATED --"
  private val SourceKey  = "SOURCE:"
  private val LinesKey   = "LINES:"

  /** Attempts to read and parse the source-map block from `generatedFile`.
    * Returns `None` when the file does not exist, is unreadable, or contains
    * no `-- MELT GENERATED --` block (e.g. was compiled without a `sourcePath`).
    */
  def read(generatedFile: File): Option[Meta] = {
    if (!generatedFile.exists()) None
    else {
      val contentOpt =
        try {
          val src = scala.io.Source.fromFile(generatedFile, "UTF-8")
          try Some(src.mkString)
          finally src.close()
        } catch {
          case _: Exception => None
        }
      contentOpt.flatMap(parse)
    }
  }

  /** Parses the source-map block from a generated Scala source string.
    *
    * The block is always appended at the end of the file, so we search from
    * the tail using [[String#lastIndexOf]] to avoid false-positive matches on
    * user code that happens to contain the sentinel string as a string literal
    * (e.g. `val s = "-- MELT GENERATED --"`).
    */
  def parse(content: String): Option[Meta] = {
    val blockEnd   = content.lastIndexOf(BlockStart)
    val blockStart = if (blockEnd >= 0) content.lastIndexOf(BlockStart, blockEnd - 1) else -1

    if (blockEnd < 0 || blockStart < 0) None
    else {
      val block = content.substring(blockStart, blockEnd + BlockStart.length)

      val sourcePathOpt: Option[String] = {
        val idx = block.indexOf(SourceKey)
        if (idx < 0) None
        else {
          val afterKey = block.substring(idx + SourceKey.length)
          val end      = afterKey.indexOf('\n')
          Some(if (end < 0) afterKey.trim else afterKey.substring(0, end).trim)
        }
      }

      val lines: IndexedSeq[(Int, Int, Int)] = {
        val idx = block.indexOf(LinesKey)
        if (idx < 0) IndexedSeq.empty
        else {
          val afterKey = block.substring(idx + LinesKey.length)
          val end      = afterKey.indexOf('\n')
          val raw      = if (end < 0) afterKey.trim else afterKey.substring(0, end).trim
          if (raw.isEmpty) IndexedSeq.empty
          else
            raw
              .split('|')
              .flatMap { pair =>
                val arrow = pair.indexOf("->")
                if (arrow < 0) None
                else
                  try {
                    val gen        = pair.substring(0, arrow).trim.toInt
                    val afterArrow = pair.substring(arrow + 2).trim
                    val colon      = afterArrow.indexOf(':')
                    val (src, col) =
                      if (colon < 0) (afterArrow.toInt, 1)
                      else (afterArrow.substring(0, colon).toInt, afterArrow.substring(colon + 1).toInt)
                    Some((gen, src, col))
                  } catch {
                    case _: NumberFormatException => None
                  }
              }
              .toIndexedSeq
        }
      }

      sourcePathOpt.map(sourcePath => Meta(sourcePath, lines))
    }
  }

  /** Maps `generatedLine` to the corresponding `(sourceLine, sourceColumn)` using
    * nearest-neighbor lookup on the sorted `lines` array.
    *
    * Finds the largest entry whose generated-line is `<= generatedLine` and
    * returns its `(sourceLine, sourceColumn)`.  Returns `None` when `lines` is empty
    * or `generatedLine` is before the first entry.
    *
    * Unlike Twirl's delta interpolation, Melt does NOT add a delta because
    * a single template line can expand to many generated lines.
    */
  def mapPosition(meta: Meta, generatedLine: Int): Option[(Int, Int)] = {
    val entries = meta.lines
    if (entries.isEmpty) None
    else {
      // Binary search for the largest entry._1 <= generatedLine
      var lo = 0
      var hi = entries.length - 1
      var result: Option[(Int, Int, Int)] = None
      while (lo <= hi) {
        val mid = (lo + hi) >>> 1
        if (entries(mid)._1 <= generatedLine) {
          result = Some(entries(mid))
          lo     = mid + 1
        } else {
          hi = mid - 1
        }
      }
      result.map(e => (e._2, e._3))
    }
  }
}
