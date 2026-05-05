/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.parser

/** Extracts brace-balanced Scala expressions from a source string.
  *
  * Per §B.1 of the design document, a full Scala parser is not required.
  * Braces are balanced by depth-counting while skipping over string literals.
  */
private[parser] object ExprExtractor:

  /** Extracts a Scala expression starting immediately after an opening `{`.
    *
    * Handles:
    *   - Nested `{...}` (depth counting)
    *   - Triple-quoted strings `"""..."""`
    *   - Regular string literals `"..."` with backslash escapes
    *   - Character literals `'...'`
    *
    * @param src   the full source string
    * @param start index immediately after the opening `{`
    * @return `(trimmedExpression, posAfterClosingBrace)`
    */
  def extract(src: String, start: Int): (String, Int) =
    val buf   = new StringBuilder
    var depth = 1
    var i     = start

    while i < src.length && depth > 0 do
      src(i) match
        case '"' if src.startsWith("\"\"\"", i) =>
          // Triple-quoted string — scan forward for the closing `"""`
          val close = src.indexOf("\"\"\"", i + 3)
          val end   = if close < 0 then src.length else close + 3
          buf ++= src.substring(i, end)
          i = end

        case '"' =>
          // Regular double-quoted string with escape processing
          buf += '"'
          i += 1
          var open = true
          while i < src.length && open do
            src(i) match
              case '\\' if i + 1 < src.length =>
                buf += '\\'; buf += src(i + 1); i += 2
              case '"' =>
                buf += '"'; i += 1; open = false
              case c =>
                buf += c; i += 1

        case '\'' =>
          // Simplified character literal: `'c'` or `'\x'`
          buf += '\''
          i += 1
          if i < src.length then
            if src(i) == '\\' && i + 1 < src.length then
              buf += '\\'; buf += src(i + 1); i += 2
            else
              buf += src(i); i += 1
          if i < src.length && src(i) == '\'' then
            buf += '\''; i += 1

        case '/' if i + 1 < src.length && src(i + 1) == '*' =>
          // Block comment — scan to `*/`; braces inside are NOT depth-counted
          buf += '/'; buf += '*'; i += 2
          while i + 1 < src.length && !(src(i) == '*' && src(i + 1) == '/') do
            buf += src(i); i += 1
          if i + 1 < src.length then
            buf += '*'; buf += '/'; i += 2

        case '/' if i + 1 < src.length && src(i + 1) == '/' =>
          // Line comment — scan to end of line; braces inside are NOT depth-counted
          buf += '/'; buf += '/'; i += 2
          while i < src.length && src(i) != '\n' do
            buf += src(i); i += 1

        case '{' =>
          depth += 1; buf += '{'; i += 1

        case '}' =>
          depth -= 1
          if depth > 0 then buf += '}'
          i += 1

        case c =>
          buf += c; i += 1

    (buf.toString.trim, i)

  /** Extracts a Scala expression that may contain inline HTML template fragments.
    *
    * When `<tag` is detected in a non-string, non-comment context (preceded by
    * a non-identifier character), the HTML fragment is parsed by [[TemplateParser]]
    * and represented as [[meltc.ast.InlineTemplatePart.Html]].
    *
    * @param posBuilder shared [[meltc.NodePositions.Builder]] that receives source-span
    *                   entries for every [[meltc.ast.TemplateNode]] parsed inside
    *                   inline HTML fragments.  The builder is passed through to each
    *                   sub-[[TemplateParser]] so that all nodes end up in the same map.
    * @return `(parts, posAfterClosingBrace)` — if no HTML is found, `parts` contains
    *         a single `Code` element equivalent to `extract()`.
    */
  def extractRich(
    src:        String,
    start:      Int,
    posBuilder: meltc.NodePositions.Builder
  ): (List[meltc.ast.InlineTemplatePart], Int) =
    import meltc.ast.InlineTemplatePart

    val parts   = List.newBuilder[InlineTemplatePart]
    val codeBuf = new StringBuilder
    var depth   = 1
    var i       = start
    var hasHtml = false

    while i < src.length && depth > 0 do
      src(i) match
        case '"' if src.startsWith("\"\"\"", i) =>
          val close = src.indexOf("\"\"\"", i + 3)
          val end   = if close < 0 then src.length else close + 3
          codeBuf ++= src.substring(i, end)
          i = end

        case '"' =>
          codeBuf += '"'
          i += 1
          var open = true
          while i < src.length && open do
            src(i) match
              case '\\' if i + 1 < src.length =>
                codeBuf += '\\'; codeBuf += src(i + 1); i += 2
              case '"' =>
                codeBuf += '"'; i += 1; open = false
              case c =>
                codeBuf += c; i += 1

        case '\'' =>
          codeBuf += '\''
          i += 1
          if i < src.length then
            if src(i) == '\\' && i + 1 < src.length then
              codeBuf += '\\'; codeBuf += src(i + 1); i += 2
            else
              codeBuf += src(i); i += 1
          if i < src.length && src(i) == '\'' then
            codeBuf += '\''; i += 1

        case '/' if i + 1 < src.length && src(i + 1) == '*' =>
          codeBuf += '/'; codeBuf += '*'; i += 2
          while i + 1 < src.length && !(src(i) == '*' && src(i + 1) == '/') do
            codeBuf += src(i); i += 1
          if i + 1 < src.length then
            codeBuf += '*'; codeBuf += '/'; i += 2

        case '/' if i + 1 < src.length && src(i + 1) == '/' =>
          codeBuf += '/'; codeBuf += '/'; i += 2
          while i < src.length && src(i) != '\n' do
            codeBuf += src(i); i += 1

        case '<' if isHtmlTagStart(src, i, codeBuf) =>
          // Flush accumulated Scala code
          val code = codeBuf.toString
          if code.nonEmpty then parts += InlineTemplatePart.Code(code)
          codeBuf.clear()
          hasHtml = true

          // Parse HTML fragment using TemplateParser.
          // baseOffset = i so that positions recorded inside the fragment are
          // expressed as absolute offsets within the original templateSource.
          val fragment   = src.substring(i)
          val fragParser = new TemplateParser(fragment, baseOffset = i, posBuilder = posBuilder)
          val nodes      = fragParser.parseFragment()
          val consumed   = fragParser.position
          parts += InlineTemplatePart.Html(nodes)
          i += consumed

        case '{' =>
          depth += 1; codeBuf += '{'; i += 1

        case '}' =>
          depth -= 1
          if depth > 0 then codeBuf += '}'
          i += 1

        case c =>
          codeBuf += c; i += 1

    // Flush remaining code
    val remaining = codeBuf.toString.trim
    if remaining.nonEmpty then parts += InlineTemplatePart.Code(remaining)

    (parts.result(), i)

  /** Checks if `<` at position `i` is the start of an inline HTML tag,
    * not a Scala comparison operator.
    *
    * Requirements:
    *   1. `<` is followed by a letter (tag name start)
    *   2. The character before `<` is NOT an identifier character
    *      (to exclude `x < y` comparisons)
    */
  private def isHtmlTagStart(src: String, i: Int, codeBuf: StringBuilder): Boolean =
    if i + 1 >= src.length then false
    else
      val next = src(i + 1)
      if !next.isLetter then false
      else
        // Check preceding character: must not be an identifier char
        val prev =
          if codeBuf.nonEmpty then codeBuf.last
          else if i > 0 then src(i - 1)
          else ' '
        !prev.isLetterOrDigit && prev != '_' && prev != '.'
