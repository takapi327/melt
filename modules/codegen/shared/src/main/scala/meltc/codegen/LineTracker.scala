/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.codegen

/** A [[StringBuilder]] wrapper that tracks the current generated line number and
  * records `(generatedLine → sourceLine)` mappings for the `-- MELT GENERATED --`
  * source-map block.
  *
  * Usage:
  * {{{
  * val tracker = new LineTracker
  * tracker ++= "package foo\n"
  * tracker.markSourceLine(3)  // generated line 2 → source line 3
  * tracker ++= "val x = 1\n"
  * val code     = tracker.result()
  * val linesStr = tracker.linesMetadata()  // "2->3"
  * }}}
  */
final class LineTracker:

  private val buf         = new StringBuilder
  private var currentLine = 1
  // (generatedLine, sourceLine, sourceColumn)
  private val entries = scala.collection.mutable.ArrayBuffer.empty[(Int, Int, Int)]

  /** Appends a string, counting embedded newlines. */
  def ++=(s: String): this.type =
    buf ++= s
    currentLine += s.count(_ == '\n')
    this

  /** Appends a single character. */
  def +=(c: Char): this.type =
    buf += c
    if c == '\n' then currentLine += 1
    this

  /** Records that the current generated line corresponds to `sourceLine` and
    * `sourceColumn` in the original `.melt` file.
    *
    * Consecutive calls on the same generated line update the entry rather than
    * appending a duplicate.
    *
    * @param sourceLine   1-based line in the original `.melt` file
    * @param sourceColumn 1-based column in the original `.melt` file (default: 1)
    */
  def markSourceLine(sourceLine: Int, sourceColumn: Int = 1): Unit =
    if sourceLine > 0 then
      val entry = (currentLine, sourceLine, sourceColumn)
      if entries.nonEmpty && entries.last._1 == currentLine then entries(entries.length - 1) = entry
      else entries += entry

  /** Returns the accumulated source string. */
  def result(): String = buf.result()

  /** Returns the recorded `(generatedLine, sourceLine, sourceColumn)` triples in order. */
  def mappings(): Seq[(Int, Int, Int)] = entries.toSeq

  /** Serialises the LINES metadata as `"g1->s1:c1|g2->s2:c2|..."`.
    * Returns an empty string when no source lines have been marked.
    */
  def linesMetadata(): String =
    entries.map { case (gen, src, col) => s"$gen->$src:$col" }.mkString("|")
