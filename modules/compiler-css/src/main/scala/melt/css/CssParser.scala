/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.css

/** Recursive-descent parser that parses a CSS string into a [[CssNode]] AST.
  *
  * Selector interpretation and scoping are not the parser's responsibility.
  * The parser only analyses structure: block boundaries and node kinds.
  */
object CssParser:

  /** Parses a CSS string into a `List[CssNode]`. */
  def parse(css: String): List[CssNode] =
    val ctx = ParseContext(css)
    parseNodes(ctx, topLevel = true)

  private def parseNodes(ctx: ParseContext, topLevel: Boolean): List[CssNode] =
    val nodes = List.newBuilder[CssNode]
    while !ctx.isEof && !(ctx.current == '}' && !topLevel) do
      ctx.skipWhitespace()
      if ctx.isEof then ()
      else if ctx.current == '}' && !topLevel then ()
      // ⚠️ Infinite-loop guard: consume a stray `}` at the top level and continue.
      // `parseRuleOrRaw` stops at `}` as a stop char and returns None for an empty prelude.
      // Since None does not advance the position, we must consume the character explicitly.
      else if ctx.current == '}' && topLevel then ctx.advance()
      else if ctx.matchComment() then
        ctx.readComment() match
          case Some(c) => nodes += CssNode.Comment(c)
          case None    => ()
      else if ctx.current == '@' then nodes += parseAtRule(ctx)
      else
        // Selector or declaration: if `{` appears it is a rule, otherwise a declaration (RawText)
        parseRuleOrRaw(ctx) match
          case Some(n) => nodes += n
          case None    => ()
    nodes.result()

  // ── At-rule ───────────────────────────────────────────────────────────────

  private def parseAtRule(ctx: ParseContext): CssNode =
    ctx.advance() // skip '@'
    val name = ctx.readIdent()
    ctx.skipWhitespace()
    // prelude = read text until `{` or `;`
    val prelude = ctx.readUntil(stopChars = Set('{', ';'))
    if ctx.isEof then CssNode.AtRule(name, prelude.trim, body = None)
    else if ctx.current == ';' then
      ctx.advance() // skip ';'
      CssNode.AtRule(name, prelude.trim, body = None)
    else
      ctx.advance() // skip '{'
      val body =
        if CssNode.PassthroughAtRules.contains(name) then
          // @keyframes, @font-face, etc.: keep the block contents as raw text
          List(CssNode.RawText(ctx.readRawBlock()))
        else
          // @media, @supports, @layer, etc.: recursively parse the block body
          parseNodes(ctx, topLevel = false)
      if !ctx.isEof && ctx.current == '}' then ctx.advance()
      CssNode.AtRule(name, prelude.trim, body = Some(body))

  // ── Style rule or raw declaration ─────────────────────────────────────────

  /** Reads a selector and its `{...}` block and returns a StyleRule.
    * Returns None if no block is found (e.g. EOF).
    */
  private def parseRuleOrRaw(ctx: ParseContext): Option[CssNode] =
    // Read text up to `{` as the selector candidate
    // If `{` never appears, return it as RawText
    val prelude = ctx.readUntil(stopChars = Set('{', '}'))
    if ctx.isEof || ctx.current == '}' then
      // No block — keep as declaration text
      val t = prelude.trim
      if t.isEmpty then None else Some(CssNode.RawText(t))
    else
      ctx.advance() // skip '{'
      // Recursively parse the block body (CSS Nesting support)
      val body = parseBlockBody(ctx)
      if !ctx.isEof && ctx.current == '}' then ctx.advance()
      Some(CssNode.StyleRule(prelude.trim, body))

  /** Parses the block body of a style rule.
    *
    * Due to CSS Nesting, a block may contain a mix of declarations ([[CssNode.RawText]])
    * and nested rules ([[CssNode.StyleRule]] / [[CssNode.AtRule]]).
    *
    * Decision rules:
    *   - If `{` appears first → nested style rule
    *   - If `;` or `}` appears first → declaration text (read one declaration only)
    *
    * ⚠️ **Important**: when `;` appears first, use `readDeclaration()` to read only one
    * declaration. Using `readUntil(Set('}'))` would incorrectly absorb all subsequent
    * declarations and nested rules.
    */
  private def parseBlockBody(ctx: ParseContext): List[CssNode] =
    val nodes = List.newBuilder[CssNode]
    while !ctx.isEof && ctx.current != '}' do
      ctx.skipWhitespace()
      if ctx.isEof || ctx.current == '}' then ()
      else if ctx.matchComment() then ctx.readComment().foreach(c => nodes += CssNode.Comment(c))
      else if ctx.current == '@' then nodes += parseAtRule(ctx)
      else
        // Branch on whether `{` or `;` appears first
        val peek = ctx.peekUntil(stopChars = Set('{', ';', '}'))
        peek match
          case '{' =>
            // Nested style rule
            parseRuleOrRaw(ctx).foreach(nodes += _)
          case ';' | '}' =>
            // Declaration text — read one declaration only (consume `;` and stop)
            val raw = ctx.readDeclaration()
            if raw.trim.nonEmpty then nodes += CssNode.RawText(raw)
          case _ =>
            // ⚠️ Infinite-loop guard: reached EOF without finding `{`, `;`, or `}`.
            // Occurs with malformed CSS (declaration missing `;`, unclosed block, etc.).
            // Consume the remaining text as raw text up to `}` (or EOF) to break the loop.
            val remaining = ctx.readUntil(stopChars = Set('}'))
            if remaining.trim.nonEmpty then nodes += CssNode.RawText(remaining)
    nodes.result()

// PassthroughAtRules references CssNode.PassthroughAtRules (single source of truth)

/** Character-level scanning context. */
private class ParseContext(src: String):
  private var pos: Int = 0

  def isEof:     Boolean = pos >= src.length
  def current:   Char    = src(pos)
  def advance(): Unit    = pos += 1

  /** Skips whitespace characters. */
  def skipWhitespace(): Unit =
    while !isEof && src(pos).isWhitespace do pos += 1

  /** Reads an identifier (letters, digits, `-`). */
  def readIdent(): String =
    val start = pos
    while !isEof && (src(pos).isLetterOrDigit || src(pos) == '-') do pos += 1
    src.substring(start, pos)

  /** Reads text until any character in `stopChars` is encountered.
    * Inside string literals (`" "`, `' '`), `url()`, CSS block comments,
    * and parentheses `()` / `[]`, `stopChars` are ignored.
    * Inside `{}`, `stopChars` are not ignored (so that `{` is detected for CSS Nesting).
    */
  def readUntil(stopChars: Set[Char]): String =
    val sb    = new StringBuilder
    var depth = 0 // nesting depth of `()` and `[]`
    while !isEof && !(depth == 0 && stopChars.contains(src(pos))) do
      src(pos) match
        case '/' if pos + 1 < src.length && src(pos + 1) == '*' =>
          val end        = src.indexOf("*/", pos + 2)
          val commentEnd = if end < 0 then src.length else end + 2
          sb ++= src.substring(pos, commentEnd)
          pos = commentEnd
        case '"' | '\'' =>
          val q = src(pos)
          sb += src(pos)
          pos += 1
          while !isEof && src(pos) != q do
            if src(pos) == '\\' then
              sb += src(pos)
              pos += 1
            if !isEof then
              sb += src(pos)
              pos += 1
          if !isEof then
            sb += src(pos)
            pos += 1
        case 'u' if src.startsWith("url(", pos) =>
          // Read url(...) as a single unit regardless of parenthesis depth.
          // ⚠️ Known limitation: if the quoted string inside url() contains `)`
          // (e.g. url("path).css")), scanning may terminate early.
          // This does not occur in practice and is therefore acceptable.
          sb += src(pos)
          pos += 1
          while !isEof && src(pos) != ')' do
            sb += src(pos)
            pos += 1
          if !isEof then
            sb += src(pos)
            pos += 1
        case '(' | '[' => depth += 1; sb += src(pos); pos += 1
        case ')' | ']' => depth -= 1; sb += src(pos); pos += 1
        case c         => sb += c; pos += 1
    sb.toString

  /** Peeks ahead and returns the first character in `stopChars` found (without advancing).
    * Returns `'\u0000'` if none is found.
    * Skips over string literals and the contents of `()` / `[]`.
    */
  def peekUntil(stopChars: Set[Char]): Char =
    var i     = pos
    var depth = 0
    while i < src.length do
      src(i) match
        case c if depth == 0 && stopChars.contains(c) => return c
        case '"' | '\''                               =>
          val q = src(i); i += 1
          while i < src.length && src(i) != q do
            if src(i) == '\\' then i += 1
            i += 1
          i += 1
        case '(' | '['                                      => depth += 1; i += 1
        case ')' | ']'                                      => depth -= 1; i += 1
        case '/' if i + 1 < src.length && src(i + 1) == '*' =>
          val end = src.indexOf("*/", i + 2)
          i = if end < 0 then src.length else end + 2
        case _ => i += 1
    '\u0000'

  /** Returns true if the current position is the start of a CSS block comment (`/` + `*`), without advancing. */
  def matchComment(): Boolean =
    pos + 1 < src.length && src(pos) == '/' && src(pos + 1) == '*'

  /** Reads a CSS block comment and returns its content. */
  def readComment(): Option[String] =
    if !matchComment() then return None
    val start = pos
    val end   = src.indexOf("*/", pos + 2)
    pos = if end < 0 then src.length else end + 2
    Some(src.substring(start, pos))

  /** Reads one CSS declaration (`property: value;`).
    *
    * Reads until `{`, `;`, or `}` is reached.
    * If `;` is reached, it is consumed and included in the returned string.
    * If `{` or `}` is reached, that character is left unconsumed.
    *
    * This prevents `parseBlockBody` from incorrectly absorbing subsequent
    * declarations and nested rules that follow the `;`.
    */
  def readDeclaration(): String =
    val text = readUntil(stopChars = Set(';', '{', '}'))
    if !isEof && src(pos) == ';' then
      pos += 1
      text + ";"
    else text

  /** Reads raw text until `}` is reached (used for passthrough blocks).
    *
    * ⚠️ **Known limitation**: `{` and `}` inside string literals or comments are still
    * counted toward the depth counter.
    * For example, in `@keyframes test { from { content: "}"; } }`, the `}` inside the
    * string literal may unexpectedly bring `depth` to 0, causing the block to end early.
    * However, string values containing `{` / `}` inside passthrough blocks such as
    * `@keyframes` / `@font-face` are extremely rare in practice, so this is acceptable.
    */
  def readRawBlock(): String =
    val sb    = new StringBuilder
    var depth = 1
    while !isEof && depth > 0 do
      if src(pos) == '{' then depth += 1
      else if src(pos) == '}' then depth -= 1
      if depth > 0 then sb += src(pos)
      pos += 1
    sb.toString
