/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.parser

import scala.util.matching.Regex

import melt.preprocessor.StyleLang

/** Splits a `.melt` source file into its four raw sections.
  *
  * Only `<script lang="scala">` is treated as the Scala instance section.
  * `<script lang="scala" module>` is treated as the module section.
  * Plain `<script>` tags (without `lang="scala"`) are left in the template
  * source as regular HTML, per §3 of the design document.
  */
private[parser] object SectionSplitter:

  case class RawScript(
    code:           String,
    imports:        List[String]           = Nil,
    importWarnings: List[(String, String)] = Nil // (message, path) for unsupported imports
  )

  case class Sections(
    rawScript:      Option[RawScript],
    moduleScript:   Option[RawScript], // <script lang="scala" module>
    templateSource: String,
    style:          Option[(String, StyleLang)]
  )

  /** Matches `<script lang="scala" module>` — the module script tag.
    * Uses lookaheads so attribute order (`lang` before/after `module`) does not matter.
    * The `module` attribute must be preceded by whitespace and followed by whitespace, `>`, or `/`
    * to avoid matching `data-module="true"` or similar attribute values.
    */
  private[parser] val ModuleScriptOpenTag: Regex =
    """(?s)<script(?=\s)(?=[^>]*\blang\s*=\s*["']scala["'])(?=[^>]*\smodule(?=\s|>|/))[^>]*>""".r

  /** Matches instance `<script lang="scala">` — must NOT have the `module` attribute. */
  private[parser] val InstanceScriptOpenTag: Regex =
    """(?s)<script(?=\s)(?=[^>]*\blang\s*=\s*["']scala["'])(?![^>]*\smodule(?=\s|>|/))[^>]*>""".r

  /** Matches `import "..."` — string literal import (always a file import, never valid Scala). */
  private val StringImportPattern: Regex =
    """^\s*import\s+"([^"]+)"\s*$""".r

  /** Matches `import "..."` followed by a trailing `//` line comment.
    * Such lines are not valid Scala and would cause a compile error if passed
    * through to scalac. We detect them separately to emit a helpful warning
    * and still process the import path.
    */
  private val StringImportWithCommentPattern: Regex =
    """^\s*import\s+"([^"]+)"\s*//.*$""".r

  private[parser] val CloseScript = "</script>"

  /** Matches `<style>` or `<style lang="...">`.
    * Group 1 captures the attribute string inside the tag (null when no attributes).
    */
  private[parser] val StyleOpenTag: Regex =
    """(?s)<style(\s[^>]*)?>""".r

  private[parser] val StyleLangAttr: Regex =
    """lang\s*=\s*["']([^"']+)["']""".r

  private[parser] val StyleClose = "</style>"

  /** Detects and removes string literal imports from script code.
    *
    * @return `(filteredCode, supportedImports, warnings)` where warnings is a
    *         list of `(message, path)` pairs for unsupported import paths.
    *         The caller uses `path` to locate the import statement in the
    *         original source for accurate line-number reporting.
    */
  /** Strips the common leading indentation of a `<script>` body (and drops
    * leading/trailing blank lines) so a source file may indent its script — e.g.
    * by 2 spaces — without the codegen receiving *inconsistent* indentation.
    *
    * `trim` alone only dedents the first line, turning a uniformly indented
    * script into "line 1 at column 0, the rest indented", which breaks Scala 3
    * significant-indentation parsing of the generated code. For a column-0 body
    * this is equivalent to `trim`.
    */
  private[parser] def dedentBlock(s: String): String =
    val lines = s.split("\n", -1).toList
    val body  = lines.dropWhile(_.trim.isEmpty).reverse.dropWhile(_.trim.isEmpty).reverse
    val real  = body.filter(_.trim.nonEmpty)
    if real.isEmpty then ""
    else
      val minIndent = real.map(_.takeWhile(_ == ' ').length).min
      body.map(l => if l.trim.isEmpty then "" else l.substring(minIndent)).mkString("\n")

  /** Finds the `</script>` that closes a script section, starting at `from`.
    *
    * Unlike a plain `indexOf`, it skips `</script>` occurrences inside Scala
    * string literals or comments in the script body, so a script may legitimately
    * contain e.g. `"...</script>..."` in a regex. Returns -1 if none is found.
    *
    * Pragmatic lexer covering the cases that occur in `.melt` scripts: `"..."`
    * (with `\` escapes), `"""..."""`, `//` line comments, and nestable `/* */`
    * block comments. String interpolation with a *nested* string literal that
    * itself contains a literal `</script>` is not tracked (a vanishingly rare
    * combination); such a file should escape the tag.
    */
  private[parser] def findScriptClose(source: String, from: Int): Int =
    val n        = source.length
    var i        = from
    var inLine   = false // // ... to end of line
    var block    = 0     // /* */ nesting depth
    var inStr    = false // "..."
    var inTriple = false // """..."""
    def triAt(j: Int): Boolean =
      j + 2 < n && source.charAt(j) == '"' && source.charAt(j + 1) == '"' && source.charAt(j + 2) == '"'
    while i < n do
      val c = source.charAt(i)
      if inLine then
        if c == '\n' then inLine = false
        i += 1
      else if block > 0 then
        if c == '/' && i + 1 < n && source.charAt(i + 1) == '*' then { block += 1; i += 2 }
        else if c == '*' && i + 1 < n && source.charAt(i + 1) == '/' then { block -= 1; i += 2 }
        else i += 1
      else if inTriple then
        // In a run of >3 quotes (e.g. `s""""x""""`), the *last* 3 close the
        // string; earlier quotes are content. So `"""` only closes when it is
        // not followed by another `"`.
        if triAt(i) && !(i + 3 < n && source.charAt(i + 3) == '"') then { inTriple = false; i += 3 }
        else i += 1
      else if inStr then
        if c == '\\' then i += 2 // skip escaped char
        else if c == '"' then { inStr = false; i += 1 }
        else i += 1
      else if triAt(i) then { inTriple = true; i += 3 }
      else if c == '"' then { inStr = true; i += 1 }
      else if c == '/' && i + 1 < n && source.charAt(i + 1) == '/' then { inLine = true; i += 2 }
      else if c == '/' && i + 1 < n && source.charAt(i + 1) == '*' then { block = 1; i += 2 }
      else if source.startsWith(CloseScript, i) then return i
      else i += 1
    -1

  private[parser] def extractImports(
    code: String
  ): (String, List[String], List[(String, String)]) =
    val lines    = code.linesIterator.toList
    val allPaths = lines.collect { case StringImportPattern(path) => path }

    // Detect string imports with trailing line comments (not valid Scala).
    // The import path is still processed, but the user is warned to remove the comment.
    val commentedPaths = lines.collect { case StringImportWithCommentPattern(path) => path }
    val commentedWarnings: List[(String, String)] = commentedPaths.map { p =>
      (
        s"""trailing line comments on string imports are not supported; remove the comment: import "$p" // ...""",
        p
      )
    }

    def isUnsupported(p: String) =
      p.startsWith("./") || p.startsWith("../") ||
        p.startsWith("http://") || p.startsWith("https://") ||
        p.startsWith("//")

    val warnings: List[(String, String)] = allPaths.collect {
      case p if p.startsWith("./") || p.startsWith("../") =>
        (s"""relative path imports are not yet supported, use an absolute path: "$p"""", p)
      case p if p.startsWith("http://") || p.startsWith("https://") || p.startsWith("//") =>
        (s"""external URL imports are not yet supported: "$p"""", p)
    } ++ commentedWarnings
    val imports  = (allPaths ++ commentedPaths).filterNot(isUnsupported)
    val filtered =
      lines
        .filterNot(l => StringImportPattern.matches(l) || StringImportWithCommentPattern.matches(l))
        .mkString("\n")
    (filtered, imports, warnings)

  def split(source: String): Either[String, Sections] =
    // ── 1. Extract <script lang="scala" module> section ───────────────────
    // Check for duplicate module scripts first; at most one is allowed.
    val moduleMatches = ModuleScriptOpenTag.findAllMatchIn(source).toList
    if moduleMatches.size > 1 then return Left("At most one <script module> is allowed per component")

    val (moduleRaw, afterModule) = moduleMatches.headOption match
      case None    => (None, source)
      case Some(m) =>
        val bodyEnd = findScriptClose(source, m.end)
        if bodyEnd < 0 then return Left("""Unclosed <script lang="scala" module> tag""")
        val rawCode   = dedentBlock(source.substring(m.end, bodyEnd))
        val remaining = source.substring(0, m.start) + source.substring(bodyEnd + CloseScript.length)
        val (filteredCode, imports, warnings) = extractImports(rawCode)
        (Some(RawScript(filteredCode, imports, warnings)), remaining)

    // ── 2. Extract <script lang="scala"> (instance) section ───────────────
    val (rawScript, afterScript) = InstanceScriptOpenTag.findFirstMatchIn(afterModule) match
      case None    => (None, afterModule)
      case Some(m) =>
        val bodyEnd = findScriptClose(afterModule, m.end)
        if bodyEnd < 0 then return Left("""Unclosed <script lang="scala"> tag""")
        val rawCode   = dedentBlock(afterModule.substring(m.end, bodyEnd))
        val remaining = afterModule.substring(0, m.start) + afterModule.substring(bodyEnd + CloseScript.length)
        val (filteredCode, imports, warnings) = extractImports(rawCode)
        (Some(RawScript(filteredCode, imports, warnings)), remaining)

    // ── 3. Extract <style> section ────────────────────────────────────────
    // Only plain `<style>` (no attributes) and `<style lang="...">` are
    // extracted. Tags with other attributes (e.g. `<style scoped>`) are
    // left in the template source unchanged, matching the original behaviour.
    val styleStart              = afterScript.indexOf("<style")
    val (styleOpt, templateRaw) =
      if styleStart < 0 then (None, afterScript)
      else
        val tagMatch = StyleOpenTag.findFirstMatchIn(afterScript.substring(styleStart)) match
          case None    => return Left("Malformed <style> tag")
          case Some(m) => m
        val attrsOpt = Option(tagMatch.group(1))
        val langOpt  = attrsOpt.flatMap(StyleLangAttr.findFirstMatchIn(_))
        // Skip tags that have attributes other than `lang` (e.g. `scoped`)
        if attrsOpt.isDefined && langOpt.isEmpty then (None, afterScript)
        else
          val tagEnd = styleStart + tagMatch.end
          val cssEnd = afterScript.indexOf(StyleClose, tagEnd)
          if cssEnd < 0 then return Left("Unclosed <style> tag")
          val rawContent = afterScript.substring(tagEnd, cssEnd).trim
          val langStr    = langOpt.map(_.group(1).toLowerCase).getOrElse("css")
          val lang       = langStr match
            case "scss" => StyleLang.Scss
            case _      => StyleLang.Css
          val remaining = afterScript.substring(0, styleStart) +
            afterScript.substring(cssEnd + StyleClose.length)
          (Some((rawContent, lang)), remaining)

    Right(Sections(rawScript, moduleRaw, templateRaw.trim, styleOpt))
