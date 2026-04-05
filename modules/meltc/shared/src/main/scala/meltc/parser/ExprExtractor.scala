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

        case '{' =>
          depth += 1; buf += '{'; i += 1

        case '}' =>
          depth -= 1
          if depth > 0 then buf += '}'
          i += 1

        case c =>
          buf += c; i += 1

    (buf.toString.trim, i)
