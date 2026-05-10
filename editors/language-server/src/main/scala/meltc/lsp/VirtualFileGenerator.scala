/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.lsp

/** A virtual .scala file generated from a .melt source file, together with the
  * [[PositionMapper]] that translates positions between the two.
  *
  * @param content the virtual .scala source text
  * @param mapper  position mapper for this file pair
  */
case class VirtualFile(content: String, mapper: PositionMapper)

/** Generates a virtual .scala file from a .melt source file.
  *
  * The virtual file is designed so that Metals can type-check the Scala script
  * section with accurate error positions:
  *
  *   - Lines that belong to the `<script lang="scala">` **body** are copied verbatim.
  *   - All other lines (the `<script>` / `</script>` tag lines, HTML template, and
  *     `<style>` section) are replaced with blank lines.
  *
  * Because every line in the virtual file corresponds to the same-numbered line in
  * the original .melt file, LSP position mappings are identity transformations and
  * diagnostics from Metals point to the correct locations in the .melt file.
  *
  * {{{
  * // Counter.melt (0-indexed lines)
  * 0: <script lang="scala">
  * 1:   val count = Var(0)
  * 2:   def increment() = count.update(_ + 1)
  * 3: </script>
  * 4:
  * 5: <div><p>{count}</p></div>
  *
  * // Virtual Counter.scala
  * 0:                                   ← blank (tag line)
  * 1:   val count = Var(0)              ← verbatim
  * 2:   def increment() = count.update(_ + 1) ← verbatim
  * 3:                                   ← blank (tag line)
  * 4:                                   ← blank
  * 5:                                   ← blank (template)
  * }}}
  */
object VirtualFileGenerator:

  private val ScalaLangRe    = """lang\s*=\s*["']scala["']""".r
  private val StringImportRe = """^\s*import\s+"[^"]+"\s*$""".r

  /** Generates a [[VirtualFile]] from a raw .melt source string. */
  def generate(meltSource: String): VirtualFile =
    val lines = meltSource.split("\n", -1).toVector

    val scriptBounds = findSection(lines, isScriptOpen, "</script>")
    val styleBounds  = findSection(lines, isStyleOpen, "</style>")

    val virtualLines = lines.zipWithIndex.map { (line, idx) =>
      val inScriptBody = scriptBounds.exists {
        case (open, close) =>
          idx > open && idx < close
      }
      if inScriptBody && !StringImportRe.matches(line) then line else ""
    }

    val scriptRange = scriptBounds
      .map { case (open, close) => LineRange(open + 1, close - 1) }
      .filter(r => r.startLine <= r.endLine)

    val styleRange = styleBounds
      .map { case (open, close) => LineRange(open + 1, close - 1) }
      .filter(r => r.startLine <= r.endLine)

    VirtualFile(
      content = virtualLines.mkString("\n"),
      mapper  = PositionMapper(scriptRange, styleRange)
    )

  private def isScriptOpen(line: String): Boolean =
    line.trim.startsWith("<script") && ScalaLangRe.findFirstIn(line).isDefined

  private def isStyleOpen(line: String): Boolean =
    line.trim.startsWith("<style")

  /** Returns the 0-based (openLine, closeLine) indices for a section delimited by
    * a matching open predicate and a close tag string.
    * Returns None if the section is absent or malformed.
    */
  private def findSection(
    lines:    Vector[String],
    isOpen:   String => Boolean,
    closeTag: String
  ): Option[(Int, Int)] =
    lines.zipWithIndex
      .collectFirst { case (line, idx) if isOpen(line) => idx }
      .flatMap { openIdx =>
        lines.zipWithIndex
          .collectFirst { case (line, idx) if idx > openIdx && line.contains(closeTag) => idx }
          .map((openIdx, _))
      }
