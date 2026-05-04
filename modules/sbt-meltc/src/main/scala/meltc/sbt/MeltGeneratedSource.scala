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
    lines:      IndexedSeq[(Int, Int)]  // (generatedLine, sourceLine), ascending
  )

  private val BlockStart = "-- MELT GENERATED --"
  private val SourceKey  = "SOURCE:"
  private val LinesKey   = "LINES:"

  /** Attempts to read and parse the source-map block from `generatedFile`.
    * Returns `None` when the file does not exist, is unreadable, or contains
    * no `-- MELT GENERATED --` block (e.g. was compiled without a `sourcePath`).
    */
  def read(generatedFile: File): Option[Meta] = {
    if (!generatedFile.exists()) return None
    val content =
      try {
        val src = scala.io.Source.fromFile(generatedFile, "UTF-8")
        try src.mkString
        finally src.close()
      } catch {
        case _: Exception => return None
      }
    parse(content)
  }

  /** Parses the source-map block from a generated Scala source string. */
  def parse(content: String): Option[Meta] = {
    val blockStart = content.indexOf(BlockStart)
    if (blockStart < 0) return None
    val blockEnd = content.indexOf(BlockStart, blockStart + BlockStart.length)
    if (blockEnd < 0) return None

    val block = content.substring(blockStart, blockEnd + BlockStart.length)

    val sourcePath: String = {
      val idx = block.indexOf(SourceKey)
      if (idx < 0) return None
      val afterKey = block.substring(idx + SourceKey.length)
      val end      = afterKey.indexOf('\n')
      if (end < 0) afterKey.trim else afterKey.substring(0, end).trim
    }

    val lines: IndexedSeq[(Int, Int)] = {
      val idx = block.indexOf(LinesKey)
      if (idx < 0) IndexedSeq.empty
      else {
        val afterKey = block.substring(idx + LinesKey.length)
        val end      = afterKey.indexOf('\n')
        val raw      = if (end < 0) afterKey.trim else afterKey.substring(0, end).trim
        if (raw.isEmpty) IndexedSeq.empty
        else
          raw.split('|').flatMap { pair =>
            val arrow = pair.indexOf("->")
            if (arrow < 0) None
            else
              try {
                val gen = pair.substring(0, arrow).trim.toInt
                val src = pair.substring(arrow + 2).trim.toInt
                Some((gen, src))
              } catch {
                case _: NumberFormatException => None
              }
          }.toIndexedSeq
      }
    }

    Some(Meta(sourcePath, lines))
  }

  /** Maps `generatedLine` to the corresponding source line using nearest-neighbor
    * lookup on the sorted `lines` array.
    *
    * Finds the largest entry whose generated-line is `<= generatedLine` and
    * returns its source line.  Returns `None` when `lines` is empty or
    * `generatedLine` is before the first entry.
    *
    * Unlike Twirl's delta interpolation, Melt does NOT add a delta because
    * a single template line can expand to many generated lines.
    */
  def mapLine(meta: Meta, generatedLine: Int): Option[Int] = {
    val entries = meta.lines
    if (entries.isEmpty) return None
    // Binary search for the largest entry._1 <= generatedLine
    var lo = 0
    var hi = entries.length - 1
    var result: Option[(Int, Int)] = None
    while (lo <= hi) {
      val mid = (lo + hi) >>> 1
      if (entries(mid)._1 <= generatedLine) {
        result = Some(entries(mid))
        lo = mid + 1
      } else {
        hi = mid - 1
      }
    }
    result.map(_._2)
  }
}
