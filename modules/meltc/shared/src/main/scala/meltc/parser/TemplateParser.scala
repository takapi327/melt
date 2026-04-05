/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.parser

import meltc.ast.{Attr, TemplateNode}

/** Parses an HTML template string into a list of [[TemplateNode]] values.
  *
  * The parser handles:
  *   - HTML elements (lowercase tag → [[TemplateNode.Element]])
  *   - Component references (uppercase tag → [[TemplateNode.Component]])
  *   - Scala expressions `{...}` with nested-brace support (§B.1)
  *   - Static, dynamic, directive, event-handler, and boolean attributes
  *   - Self-closing tags `<br />` and void elements
  *   - HTML comments `<!-- ... -->` (discarded)
  */
private[parser] final class TemplateParser(src: String):

  private var pos: Int = 0

  /** HTML void elements that never have a closing tag. */
  private val VoidElements: Set[String] = Set(
    "area", "base", "br", "col", "embed", "hr", "img", "input",
    "link", "meta", "param", "source", "track", "wbr"
  )

  def parse(): List[TemplateNode] = parseNodes(insideTag = false)

  // ── Node-sequence parsing ─────────────────────────────────────────────────

  private def parseNodes(insideTag: Boolean): List[TemplateNode] =
    val nodes = List.newBuilder[TemplateNode]
    var stop  = false

    while !stop && pos < src.length do
      if src.startsWith("</", pos) then
        if insideTag then
          stop = true
        else
          // Stray closing tag at the top level — skip it to avoid an infinite loop.
          // A well-formed .melt template should not have unmatched closing tags,
          // but we parse leniently rather than crashing.
          val gtPos = src.indexOf('>', pos)
          pos = if gtPos < 0 then src.length else gtPos + 1
      else
        src(pos) match
          case '{' =>
            pos += 1
            val (expr, end) = ExprExtractor.extract(src, pos)
            pos = end
            if expr.nonEmpty then nodes += TemplateNode.Expression(expr)

          case '<' if isOpenTagAt(pos) =>
            nodes += parseElement()

          case '<' if src.startsWith("<!--", pos) =>
            val end = src.indexOf("-->", pos + 4)
            pos = if end < 0 then src.length else end + 3

          case _ =>
            val text = collectText()
            if !text.isBlank then nodes += TemplateNode.Text(text)

    nodes.result()

  // ── Element / component parsing ───────────────────────────────────────────

  private def parseElement(): TemplateNode =
    pos += 1 // consume '<'

    val tag = collectName()
    skipSpaces()
    val attrs = collectAttrs()

    val selfClose = pos < src.length && src(pos) == '/'
    if selfClose then pos += 1
    if pos < src.length && src(pos) == '>' then pos += 1

    val children =
      if selfClose || VoidElements.contains(tag.toLowerCase) then Nil
      else
        val ch = parseNodes(insideTag = true)
        consumeClosingTag()
        ch

    makeNode(tag, attrs, children)

  private def consumeClosingTag(): Unit =
    if pos < src.length && src.startsWith("</", pos) then
      pos += 2
      skipSpaces()
      while pos < src.length && src(pos) != '>' do pos += 1
      if pos < src.length then pos += 1

  // ── Attribute parsing ─────────────────────────────────────────────────────

  private def collectAttrs(): List[Attr] =
    val attrs = List.newBuilder[Attr]
    skipSpaces()
    while pos < src.length && src(pos) != '>' && src(pos) != '/' do
      collectOneAttr().foreach(attrs += _)
      skipSpaces()
    attrs.result()

  private def collectOneAttr(): Option[Attr] =
    if pos >= src.length || src(pos) == '>' || src(pos) == '/' then return None

    val name = collectAttrName()
    if name.isEmpty then { pos += 1; return None } // skip unknown char

    skipSpaces()
    if pos >= src.length || src(pos) != '=' then
      // Directive with no value (e.g. `transition:fade`) or plain boolean attribute
      val colon = name.indexOf(':')
      return Some(
        if colon >= 0 then Attr.Directive(name.substring(0, colon), name.substring(colon + 1), None)
        else Attr.BooleanAttr(name)
      )

    pos += 1 // skip '='
    skipSpaces()
    if pos >= src.length then return Some(Attr.BooleanAttr(name))

    src(pos) match
      case '"' =>
        pos += 1
        val start = pos
        while pos < src.length && src(pos) != '"' do pos += 1
        val value = src.substring(start, pos)
        if pos < src.length then pos += 1
        Some(makeAttrStatic(name, value))

      case '\'' =>
        pos += 1
        val start = pos
        while pos < src.length && src(pos) != '\'' do pos += 1
        val value = src.substring(start, pos)
        if pos < src.length then pos += 1
        Some(makeAttrStatic(name, value))

      case '{' =>
        pos += 1
        val (expr, end) = ExprExtractor.extract(src, pos)
        pos = end
        Some(makeAttrExpr(name, expr))

      case _ =>
        val start = pos
        while pos < src.length && !src(pos).isWhitespace && src(pos) != '>' && src(pos) != '/' do
          pos += 1
        Some(makeAttrStatic(name, src.substring(start, pos)))

  private def makeAttrStatic(name: String, value: String): Attr =
    val colon = name.indexOf(':')
    if colon >= 0 then Attr.Directive(name.substring(0, colon), name.substring(colon + 1), Some(value))
    else Attr.Static(name, value)

  private def makeAttrExpr(name: String, expr: String): Attr =
    val colon = name.indexOf(':')
    if colon >= 0 then
      Attr.Directive(name.substring(0, colon), name.substring(colon + 1), Some(expr))
    else if name.startsWith("on") && name.length > 2 then
      Attr.EventHandler(name.substring(2), expr)
    else
      Attr.Dynamic(name, expr)

  // ── Node factory ──────────────────────────────────────────────────────────

  private def makeNode(tag: String, attrs: List[Attr], children: List[TemplateNode]): TemplateNode =
    if tag.charAt(0).isUpper then TemplateNode.Component(tag, attrs, children)
    else TemplateNode.Element(tag, attrs, children)

  // ── Helpers ───────────────────────────────────────────────────────────────

  /** True if `pos` points to `<` followed by a letter or `_` (an opening tag). */
  private def isOpenTagAt(p: Int): Boolean =
    p + 1 < src.length && {
      val next = src(p + 1)
      next.isLetter || next == '_'
    }

  /** Reads a tag or attribute name (letters, digits, `-`, `_`, `.`, `:`). */
  private def collectName(): String =
    val start = pos
    while pos < src.length &&
          (src(pos).isLetterOrDigit || src(pos) == '-' || src(pos) == '_' || src(pos) == '.') do
      pos += 1
    src.substring(start, pos)

  /** Reads an attribute name (stops at `=`, whitespace, `>`, `/`).
    * Includes `:` for directive syntax (`bind:value`, `class:active`).
    */
  private def collectAttrName(): String =
    val start = pos
    while pos < src.length && src(pos) != '=' && !src(pos).isWhitespace &&
          src(pos) != '>' && src(pos) != '/' do
      pos += 1
    src.substring(start, pos)

  /** Reads text until `{`, a tag-start `<letter`, a closing `</`, or an HTML comment. */
  private def collectText(): String =
    val buf  = new StringBuilder
    var stop = false
    while !stop && pos < src.length do
      src(pos) match
        case '{' => stop = true
        case '<' =>
          val next = if pos + 1 < src.length then src(pos + 1) else '\u0000'
          if next.isLetter || next == '_' || next == '/' || src.startsWith("<!--", pos) then
            stop = true
          else
            buf += '<'; pos += 1
        case c =>
          buf += c; pos += 1
    buf.toString

  private def skipSpaces(): Unit =
    while pos < src.length && src(pos).isWhitespace do pos += 1

object TemplateParser:
  def parse(templateSource: String): List[TemplateNode] =
    new TemplateParser(templateSource).parse()
