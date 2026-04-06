/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.codegen

/** Rewrites CSS selectors to include a scope class for component isolation.
  *
  * Given a scope ID like `melt-08bc50`, each CSS selector is rewritten so that
  * it only matches elements carrying that class. This mirrors the approach used
  * by Svelte's scoped styles.
  *
  * {{{
  * CssScoper.scope("h1 { color: red; }", "melt-abc123")
  * // → "h1.melt-abc123 { color: red; }"
  * }}}
  *
  * Supported features:
  *   - Simple selectors: `h1`, `.cls`, `#id`, `*`
  *   - Compound selectors with combinators: scope applied to the last segment
  *   - Pseudo-elements (`::before`, `::after`): scope inserted before the pseudo
  *   - `:global(...)`: inner selector emitted without scoping
  *   - Group selectors (`,`-separated): each part scoped independently
  *   - `@media` / `@supports`: selectors inside are scoped, condition is preserved
  *   - `@keyframes` / `@font-face`: passed through without scoping
  */
object CssScoper:

  /** Pseudo-elements that require the scope class to be inserted before them. */
  private val PseudoElements: Set[String] = Set(
    "::before",
    "::after",
    "::first-line",
    "::first-letter",
    "::placeholder",
    "::selection",
    "::marker",
    "::backdrop",
    "::file-selector-button"
  )

  /** At-rules whose body content should NOT be scoped (no selectors inside). */
  private val PassthroughAtRules: Set[String] = Set("keyframes", "font-face")

  def scope(css: String, scopeId: String): String =
    val buf = new StringBuilder
    var i   = 0

    while i < css.length do
      skipWhitespace(css, i) match
        case j if j >= css.length => i = j
        case j =>
          i = j
          if css(i) == '/' && i + 1 < css.length && css(i + 1) == '*' then
            val end = css.indexOf("*/", i + 2)
            val commentEnd = if end < 0 then css.length else end + 2
            buf ++= css.substring(i, commentEnd)
            i = commentEnd
          else if css(i) == '@' then
            i = processAtRule(css, i, scopeId, buf)
          else if css(i) == '}' then
            buf += '}'
            i += 1
          else
            i = processRule(css, i, scopeId, buf)

    buf.toString

  // ── At-rule processing ────────────────────────────────────────────────────

  private def processAtRule(css: String, start: Int, scopeId: String, buf: StringBuilder): Int =
    // Read the at-rule name (e.g. "media", "keyframes", "supports")
    var i    = start + 1 // skip '@'
    val name = new StringBuilder
    while i < css.length && css(i).isLetterOrDigit || (i < css.length && css(i) == '-') do
      name += css(i)
      i += 1

    val ruleName     = name.toString
    val isPassthrough = PassthroughAtRules.exists(n => ruleName.endsWith(n))

    // Read until '{' to capture the at-rule condition
    val condStart = i
    while i < css.length && css(i) != '{' do i += 1
    val condition = css.substring(condStart, i).trim

    if i >= css.length then
      buf ++= css.substring(start, i)
      return i

    i += 1 // skip '{'

    buf ++= s"@$ruleName"
    if condition.nonEmpty then buf ++= s" $condition"
    buf ++= " {\n"

    if isPassthrough then
      // Pass through the block content without scoping
      var depth = 1
      val blockStart = i
      while i < css.length && depth > 0 do
        if css(i) == '{' then depth += 1
        else if css(i) == '}' then depth -= 1
        if depth > 0 then i += 1
      buf ++= css.substring(blockStart, i)
      buf += '}'
      if i < css.length then i += 1
    else
      // Recursively scope the inner rules
      var depth = 1
      while i < css.length && depth > 0 do
        val j = skipWhitespace(css, i)
        i = j
        if i >= css.length then ()
        else if css(i) == '}' then
          depth -= 1
          if depth > 0 then
            buf += '}'
            i += 1
          else
            buf ++= "}\n"
            i += 1
        else if css(i) == '/' && i + 1 < css.length && css(i + 1) == '*' then
          val end = css.indexOf("*/", i + 2)
          val commentEnd = if end < 0 then css.length else end + 2
          buf ++= css.substring(i, commentEnd)
          i = commentEnd
        else if css(i) == '@' then
          i = processAtRule(css, i, scopeId, buf)
        else
          i = processRule(css, i, scopeId, buf)

    i

  // ── Rule processing ──────────────────────────────────────────────────────

  private def processRule(css: String, start: Int, scopeId: String, buf: StringBuilder): Int =
    // Read selector(s) until '{'
    var i       = start
    val selBuf  = new StringBuilder
    while i < css.length && css(i) != '{' do
      selBuf += css(i)
      i += 1

    if i >= css.length then
      buf ++= css.substring(start, i)
      return i

    val rawSelector = selBuf.toString.trim
    val scopedSelector = scopeGroupSelector(rawSelector, scopeId)
    buf ++= scopedSelector
    buf ++= " {"

    i += 1 // skip '{'

    // Read block content until matching '}'
    var depth = 1
    while i < css.length && depth > 0 do
      if css(i) == '{' then depth += 1
      else if css(i) == '}' then depth -= 1
      if depth > 0 then
        buf += css(i)
        i += 1

    buf += '}'
    if i < css.length then i += 1
    buf += '\n'
    i

  // ── Selector scoping ────────────────────────────────────────────────────

  /** Scopes a group selector (comma-separated). */
  private def scopeGroupSelector(selector: String, scopeId: String): String =
    splitGroupSelector(selector).map(s => scopeSingleSelector(s.trim, scopeId)).mkString(", ")

  /** Splits a selector on top-level commas (not inside parentheses). */
  private def splitGroupSelector(selector: String): List[String] =
    val parts   = List.newBuilder[String]
    val current = new StringBuilder
    var depth   = 0
    var i       = 0
    while i < selector.length do
      selector(i) match
        case '(' => depth += 1; current += '('; i += 1
        case ')' => depth -= 1; current += ')'; i += 1
        case ',' if depth == 0 =>
          parts += current.toString
          current.clear()
          i += 1
        case c => current += c; i += 1
    parts += current.toString
    parts.result()

  /** Scopes a single selector (no commas). */
  private def scopeSingleSelector(selector: String, scopeId: String): String =
    if selector.isEmpty then return selector

    // Handle :global(...) — strip wrapper and emit unscoped
    if selector.startsWith(":global(") && selector.endsWith(")") then
      return selector.substring(8, selector.length - 1)

    // Handle selectors that contain :global() as part of a compound selector
    val globalIdx = selector.indexOf(":global(")
    if globalIdx >= 0 then
      return scopeSelectorWithGlobal(selector, scopeId)

    // Split into combinator-separated segments and scope the last one
    val segments = splitByCombinators(selector)
    if segments.isEmpty then return selector

    val init = segments.init
    val last = scopeSimpleSelector(segments.last._1.trim, scopeId)

    val result = new StringBuilder
    init.foreach { case (seg, comb) =>
      result ++= seg
      result += ' '
      if comb.nonEmpty then result ++= comb; result += ' '
    }
    result ++= last
    result.toString

  /** Handles selectors containing `:global()` within a compound selector. */
  private def scopeSelectorWithGlobal(selector: String, scopeId: String): String =
    val globalIdx = selector.indexOf(":global(")
    // Find matching closing paren
    var depth     = 0
    var end       = globalIdx + 8
    while end < selector.length && (depth > 0 || selector(end) != ')') do
      if selector(end) == '(' then depth += 1
      else if selector(end) == ')' then depth -= 1
      end += 1
    if end < selector.length then end += 1 // skip ')'

    val before  = selector.substring(0, globalIdx)
    val inner   = selector.substring(globalIdx + 8, end - 1)
    val after   = selector.substring(end)

    // Scope the 'before' part if non-empty, leave global inner unscoped
    val scopedBefore =
      if before.trim.isEmpty then ""
      else
        val trimmed = before.trim
        if trimmed.endsWith(" ") || trimmed.endsWith(">") || trimmed.endsWith("+") || trimmed.endsWith("~") then
          scopeSingleSelector(trimmed.init.trim, scopeId) + trimmed.last
        else scopeSingleSelector(trimmed, scopeId)

    val result = scopedBefore + inner + after
    result.trim

  /** Splits a selector string into segments separated by combinators. Returns list of (segment, combinator). */
  private def splitByCombinators(selector: String): List[(String, String)] =
    val segments = List.newBuilder[(String, String)]
    val current  = new StringBuilder
    var i        = 0
    val len      = selector.length

    while i < len do
      selector(i) match
        case '>' | '+' | '~' =>
          if current.nonEmpty then
            segments += ((current.toString.trim, selector(i).toString))
            current.clear()
          i += 1
          // skip spaces after combinator
          while i < len && selector(i) == ' ' do i += 1
        case ' ' =>
          // Could be a descendant combinator or just spaces around another combinator
          val saved = current.toString
          var j     = i + 1
          while j < len && selector(j) == ' ' do j += 1
          if j < len && (selector(j) == '>' || selector(j) == '+' || selector(j) == '~') then
            // Spaces before an explicit combinator — don't treat as descendant
            i = j
          else
            // Descendant combinator (space)
            if saved.trim.nonEmpty then
              segments += ((saved.trim, ""))
              current.clear()
            i = j
        case '[' =>
          // Attribute selector — include everything until ']'
          current += '['
          i += 1
          while i < len && selector(i) != ']' do
            current += selector(i)
            i += 1
          if i < len then current += ']'; i += 1
        case '(' =>
          // Parenthesized content (pseudo-class args) — include balanced parens
          current += '('
          i += 1
          var depth = 1
          while i < len && depth > 0 do
            if selector(i) == '(' then depth += 1
            else if selector(i) == ')' then depth -= 1
            if depth > 0 then current += selector(i)
            i += 1
          current += ')'
        case c =>
          current += c
          i += 1

    if current.nonEmpty then segments += ((current.toString.trim, ""))
    segments.result()

  /** Appends the scope class to a simple selector, handling pseudo-elements. */
  private def scopeSimpleSelector(selector: String, scopeId: String): String =
    if selector.isEmpty then selector
    else
      val scopeClass = s".$scopeId"

      // Check for pseudo-element at the end
      val pseudoMatch = PseudoElements.collectFirst {
        case pseudo if selector.indexOf(pseudo) > 0 =>
          val idx = selector.indexOf(pseudo)
          selector.substring(0, idx) + scopeClass + selector.substring(idx)
      }

      pseudoMatch.getOrElse {
        // Check for pseudo-class at the end (e.g., :hover, :focus)
        val lastColon = selector.lastIndexOf(':')
        if lastColon > 0 && !selector.substring(lastColon).startsWith("::") then
          selector.substring(0, lastColon) + scopeClass + selector.substring(lastColon)
        else
          selector + scopeClass
      }

  // ── Helpers ─────────────────────────────────────────────────────────────

  private def skipWhitespace(css: String, start: Int): Int =
    var i = start
    while i < css.length && css(i).isWhitespace do i += 1
    i
