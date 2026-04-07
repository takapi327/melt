/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.lsp

/** A range of 0-based line numbers, inclusive on both ends. */
case class LineRange(startLine: Int, endLine: Int):
  def contains(line: Int): Boolean = line >= startLine && line <= endLine

/** Identifies which section of a .melt file a given line belongs to. */
enum MeltSection:
  case Script, Template, Style, Unknown
  def name: String = this.toString.toLowerCase

/** Maps positions between a .melt source file and its virtual .scala representation.
  *
  * [[VirtualFileGenerator]] preserves line numbers exactly — script section lines
  * are copied verbatim and all other lines become blank. Therefore the mapping
  * between .melt and virtual .scala positions is the **identity**: line N in the
  * virtual file corresponds to line N in the .melt file.
  *
  * The primary responsibility of this class is therefore to answer
  * "which section does this line belong to?" so the language server can decide
  * how to route LSP requests.
  *
  * @param scriptRange 0-based inclusive line range of the `<script lang="scala">` body
  *                    (excludes the opening and closing tag lines)
  * @param styleRange  0-based inclusive line range of the `<style>` body
  *                    (excludes the opening and closing tag lines)
  */
class PositionMapper(
  val scriptRange: Option[LineRange],
  val styleRange:  Option[LineRange]
):

  /** Returns the section that contains the given 0-based line number. */
  def sectionAt(line: Int): MeltSection =
    if scriptRange.exists(_.contains(line)) then MeltSection.Script
    else if styleRange.exists(_.contains(line)) then MeltSection.Style
    else MeltSection.Template

  /** Returns true if the given 0-based line is inside the `<script lang="scala">` body. */
  def isScriptLine(line: Int): Boolean = sectionAt(line) == MeltSection.Script

  /** Maps a 0-based (line, character) position from the virtual .scala file back to a
    * .melt position. Because line numbers are preserved identically this is the identity.
    */
  def virtualToMelt(line: Int, character: Int): (Int, Int) = (line, character)

  /** Maps a 0-based (line, character) position from the .melt file to a virtual .scala
    * position. Because line numbers are preserved identically this is the identity.
    */
  def meltToVirtual(line: Int, character: Int): (Int, Int) = (line, character)
