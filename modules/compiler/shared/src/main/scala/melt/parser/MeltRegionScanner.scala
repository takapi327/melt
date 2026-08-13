/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.parser

import scala.collection.mutable.ListBuffer

import melt.preprocessor.StyleLang

/** Locates the byte spans of a `.melt` file's Scala/CSS sections *without*
  * removing or transforming them — the position-preserving counterpart to
  * [[SectionSplitter]] (which discards offsets while rebuilding the template).
  *
  * It reuses SectionSplitter's tag regexes so the recognition grammar stays
  * single-sourced: exactly the same tags SectionSplitter extracts are reported
  * here, only with their offsets in the *original* source. This is the input
  * to the `.melt` formatter, which reformats each section's inner text and
  * splices it back while leaving everything outside the spans byte-identical.
  */
object MeltRegionScanner:

  enum RegionKind:
    case InstanceScript, ModuleScript, Style

  /** A recognized, reformattable section.
    *
    * @param innerStart index just after the opening tag's `>`
    * @param innerEnd   index of the closing tag's `<`
    * @param styleLang  the `<style>` language (only for [[RegionKind.Style]])
    *
    * The raw (untrimmed) inner text is `source.substring(innerStart, innerEnd)`.
    */
  final case class Region(
    kind:       RegionKind,
    innerStart: Int,
    innerEnd:   Int,
    styleLang:  Option[StyleLang] = None
  )

  /** Scans `source` and returns the recognized regions in source order.
    * Returns `Left` on the same malformed-input conditions as
    * [[SectionSplitter.split]] (duplicate/unclosed tags).
    */
  def scan(source: String): Either[String, List[Region]] =
    import SectionSplitter.*
    val regions = ListBuffer.empty[Region]

    // ── 1. module script (at most one) ──────────────────────────────────────
    val moduleMatches = ModuleScriptOpenTag.findAllMatchIn(source).toList
    if moduleMatches.size > 1 then return Left("At most one <script module> is allowed per component")
    moduleMatches.headOption match
      case None    => ()
      case Some(m) =>
        val close = findScriptClose(source, m.end)
        if close < 0 then return Left("""Unclosed <script lang="scala" module> tag""")
        regions += Region(RegionKind.ModuleScript, m.end, close)

    // ── 2. instance script (the regex excludes `module`, so no overlap) ──────
    InstanceScriptOpenTag.findFirstMatchIn(source) match
      case None    => ()
      case Some(m) =>
        val close = findScriptClose(source, m.end)
        if close < 0 then return Left("""Unclosed <script lang="scala"> tag""")
        regions += Region(RegionKind.InstanceScript, m.end, close)

    // A `<style` occurring inside a script body is text, not a section — mirror
    // SectionSplitter, which searches for `<style` only after removing scripts.
    def insideScriptBody(idx: Int): Boolean =
      regions.exists(r => idx >= r.innerStart && idx < r.innerEnd)

    // ── 3. style (first `<style` outside any script body) ───────────────────
    var searchFrom = 0
    var styleIdx   = -1
    var scanning   = true
    while scanning do
      val idx = source.indexOf("<style", searchFrom)
      if idx < 0 then scanning = false
      else if insideScriptBody(idx) then searchFrom = idx + "<style".length
      else
        styleIdx = idx
        scanning = false

    if styleIdx >= 0 then
      StyleOpenTag.findFirstMatchIn(source.substring(styleIdx)) match
        case None     => return Left("Malformed <style> tag")
        case Some(tm) =>
          val attrsOpt = Option(tm.group(1))
          val langOpt  = attrsOpt.flatMap(StyleLangAttr.findFirstMatchIn(_))
          // `<style scoped>` and other non-`lang` attribute tags are left in the
          // template by SectionSplitter — not a formattable region here either.
          if attrsOpt.isDefined && langOpt.isEmpty then ()
          else
            val tagEnd = styleIdx + tm.end
            val cssEnd = source.indexOf(StyleClose, tagEnd)
            if cssEnd < 0 then return Left("Unclosed <style> tag")
            val lang = langOpt.map(_.group(1).toLowerCase) match
              case Some("scss") => StyleLang.Scss
              case _            => StyleLang.Css
            regions += Region(RegionKind.Style, tagEnd, cssEnd, Some(lang))

    Right(regions.sortBy(_.innerStart).toList)
