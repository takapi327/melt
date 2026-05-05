/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc

/** Source position span for a single AST node.
  *
  * Offsets are 0-based character positions within the `templateSource` string
  * extracted by [[meltc.parser.SectionSplitter]].  Use [[absoluteLine]] and
  * [[column]] to convert to human-readable coordinates relative to the original
  * `.melt` file.
  *
  * @param startOffset 0-based character offset of the first character of the node
  *                    in `templateSource`. `-1` means the position is unknown.
  * @param endOffset   0-based character offset one past the last character of the
  *                    node. `-1` means the end offset is unknown.
  */
case class SourceSpan(startOffset: Int, endOffset: Int = -1):

  /** Returns `true` if this span carries a valid position. */
  def isKnown: Boolean = startOffset >= 0

  /** 1-based line number in the original `.melt` file.
    *
    * @param templateSource    the raw template section text (used to count newlines)
    * @param templateStartLine 1-based line in the `.melt` file where the template section begins
    */
  def absoluteLine(templateSource: String, templateStartLine: Int): Int =
    if startOffset < 0 || templateSource.isEmpty then templateStartLine
    else templateStartLine + templateSource.take(startOffset).count(_ == '\n')

  /** 1-based column number within the line.
    *
    * Computed from `startOffset` alone — independent of `templateStartLine`.
    *
    * @param templateSource the raw template section text
    */
  def column(templateSource: String): Int =
    if startOffset < 0 || templateSource.isEmpty then 1
    else
      val lastNl = templateSource.lastIndexOf('\n', startOffset - 1)
      if lastNl < 0 then startOffset + 1 else startOffset - lastNl

object SourceSpan:
  /** Sentinel value representing an unknown / unavailable source position. */
  val unknown: SourceSpan = SourceSpan(-1, -1)
