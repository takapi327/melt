/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.parser

import scala.util.matching.Regex

import meltc.css.StyleLang

/** Splits a `.melt` source file into its three raw sections.
  *
  * Only `<script lang="scala">` is treated as the Scala section.
  * Plain `<script>` tags (without `lang="scala"`) are left in the template
  * source as regular HTML, per §3 of the design document.
  */
private[parser] object SectionSplitter:

  case class RawScript(code: String, propsType: Option[String])

  case class Sections(
    rawScript:      Option[RawScript],
    templateSource: String,
    style:          Option[(String, StyleLang)]
  )

  /** Matches `<script` tags that contain `lang="scala"` or `lang='scala'`.
    * Group 1 captures the full attribute string inside the tag.
    */
  private val ScriptOpenTag: Regex =
    """(?s)<script(\s[^>]*\blang\s*=\s*["']scala["'][^>]*)>""".r

  /** Matches `props="TypeName"` or `props='TypeName'` within an attribute string.
    * Group 1 captures the type name.
    */
  private val PropsAttr: Regex = """props\s*=\s*["']([^"']+)["']""".r

  private val CloseScript = "</script>"

  /** Matches `<style>` or `<style lang="...">`.
    * Group 1 captures the attribute string inside the tag (null when no attributes).
    */
  private val StyleOpenTag: Regex =
    """(?s)<style(\s[^>]*)?>""".r

  private val StyleLangAttr: Regex =
    """lang\s*=\s*["']([^"']+)["']""".r

  private val StyleClose = "</style>"

  def split(source: String): Either[String, Sections] =
    // ── 1. Extract <script lang="scala"> section ──────────────────────────
    val (rawScript, afterScript) = ScriptOpenTag.findFirstMatchIn(source) match
      case None    => (None, source)
      case Some(m) =>
        val bodyStart = m.end
        val bodyEnd   = source.indexOf(CloseScript, bodyStart)
        if bodyEnd < 0 then return Left("""Unclosed <script lang="scala"> tag""")
        val code      = source.substring(bodyStart, bodyEnd).trim
        val propsType = PropsAttr.findFirstMatchIn(m.group(1)).map(_.group(1))
        val remaining = source.substring(0, m.start) + source.substring(bodyEnd + CloseScript.length)
        (Some(RawScript(code, propsType)), remaining)

    // ── 2. Extract <style> section ────────────────────────────────────────
    val styleStart              = afterScript.indexOf("<style")
    val (styleOpt, templateRaw) =
      if styleStart < 0 then (None, afterScript)
      else
        val tagMatch = StyleOpenTag.findFirstMatchIn(afterScript.substring(styleStart)) match
          case None    => return Left("Malformed <style> tag")
          case Some(m) => m
        val tagEnd = styleStart + tagMatch.end
        val cssEnd = afterScript.indexOf(StyleClose, tagEnd)
        if cssEnd < 0 then return Left("Unclosed <style> tag")
        val rawContent = afterScript.substring(tagEnd, cssEnd).trim
        val langStr    = Option(tagMatch.group(1))
          .flatMap(StyleLangAttr.findFirstMatchIn(_))
          .map(_.group(1).toLowerCase)
          .getOrElse("css")
        val lang = langStr match
          case "scss" => StyleLang.Scss
          case _      => StyleLang.Css
        val remaining = afterScript.substring(0, styleStart) +
          afterScript.substring(cssEnd + StyleClose.length)
        (Some((rawContent, lang)), remaining)

    Right(Sections(rawScript, templateRaw.trim, styleOpt))
