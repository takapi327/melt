/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.parser

import meltc.ast.*
import meltc.{ NodePositions, SourceSpan }

/** Parses an HTML template string into a list of [[TemplateNode]] values.
  *
  * The parser handles:
  *   - HTML elements (lowercase tag → [[TemplateNode.Element]])
  *   - Component references (uppercase tag → [[TemplateNode.Component]])
  *   - Scala expressions `{...}` with nested-brace support (§B.1)
  *   - Static, dynamic, directive, event-handler, and boolean attributes
  *   - Self-closing tags `<br />` and void elements
  *   - HTML comments `<!-- ... -->` (discarded)
  *   - HTML entity decoding in text and static attribute values
  *   - Whitespace collapsing between elements
  *   - Warnings for non-void self-closing tags
  *
  * @param src        the template source text to parse
  * @param baseOffset character offset of `src(0)` within the full `templateSource`.
  *                   `0` for the top-level parser; set to the fragment start index
  *                   when parsing inline HTML fragments inside expressions.
  * @param posBuilder shared builder that accumulates [[SourceSpan]] entries for
  *                   every node.  Passed through to sub-parsers via
  *                   [[ExprExtractor.extractRich]] so all nodes share a single map.
  */
private[parser] final class TemplateParser(
  src:        String,
  baseOffset: Int,
  posBuilder: NodePositions.Builder
):

  private var pos: Int = 0

  private val _warnings = List.newBuilder[(String, Int)]

  /** All valid HTML5 element names — mirrors `HtmlTag.knownTags` in the runtime.
    * Kept in sync manually; used to validate `<melt:element this={"..."}>` string literals.
    */
  private val KnownHtmlTags: Set[String] = Set(
    "a",
    "abbr",
    "address",
    "area",
    "article",
    "aside",
    "audio",
    "b",
    "base",
    "bdi",
    "bdo",
    "blockquote",
    "br",
    "button",
    "canvas",
    "caption",
    "cite",
    "code",
    "col",
    "colgroup",
    "data",
    "datalist",
    "dd",
    "del",
    "details",
    "dfn",
    "dialog",
    "div",
    "dl",
    "dt",
    "em",
    "embed",
    "fieldset",
    "figcaption",
    "figure",
    "footer",
    "form",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "header",
    "hgroup",
    "hr",
    "i",
    "iframe",
    "img",
    "input",
    "ins",
    "kbd",
    "label",
    "legend",
    "li",
    "link",
    "main",
    "map",
    "mark",
    "menu",
    "meta",
    "meter",
    "nav",
    "noscript",
    "object",
    "ol",
    "optgroup",
    "option",
    "output",
    "p",
    "picture",
    "pre",
    "progress",
    "q",
    "rp",
    "rt",
    "ruby",
    "s",
    "samp",
    "script",
    "search",
    "section",
    "select",
    "slot",
    "small",
    "source",
    "span",
    "strong",
    "style",
    "sub",
    "summary",
    "sup",
    "table",
    "tbody",
    "td",
    "template",
    "textarea",
    "tfoot",
    "th",
    "thead",
    "time",
    "tr",
    "track",
    "u",
    "ul",
    "var",
    "video",
    "wbr"
  )

  /** HTML void elements that never have a closing tag. */
  private val VoidElements: Set[String] = Set(
    "area",
    "base",
    "br",
    "col",
    "embed",
    "hr",
    "img",
    "input",
    "link",
    "meta",
    "param",
    "source",
    "track",
    "wbr"
  )

  def parse(): List[TemplateNode] =
    val raw = parseNodes(insideTag = false)
    collapseWhitespace(raw)

  /** Parses a single top-level element (or self-closing tag) and returns.
    * Used by [[ExprExtractor.extractRich]] to parse inline HTML fragments.
    */
  def parseFragment(): List[TemplateNode] =
    skipSpaces()
    if pos < src.length && src(pos) == '<' && isOpenTagAt(pos) then
      val nodeStart = pos
      val node      = parseElement()
      posBuilder.add(node, SourceSpan(baseOffset + nodeStart))
      List(node)
    else Nil

  /** Current parser position — used by callers to determine how many characters were consumed. */
  def position: Int = pos

  def warnings: List[(String, Int)] = _warnings.result()

  // ── Node-sequence parsing ─────────────────────────────────────────────────

  private def parseNodes(insideTag: Boolean): List[TemplateNode] =
    val nodes = List.newBuilder[TemplateNode]
    var stop  = false

    while !stop && pos < src.length do
      if src.startsWith("</", pos) then
        if insideTag then stop = true
        else
          // Stray closing tag at the top level — skip it to avoid an infinite loop.
          // A well-formed .melt template should not have unmatched closing tags,
          // but we parse leniently rather than crashing.
          val gtPos = src.indexOf('>', pos)
          pos = if gtPos < 0 then src.length else gtPos + 1
      else
        val nodeStart = pos // capture source offset before consuming any characters
        src(pos) match
          case '{' =>
            pos += 1
            if src.startsWith("/snippet", pos) then
              // End of snippet body — consume until `}` and stop this parse level
              while pos < src.length && src(pos) != '}' do pos += 1
              if pos < src.length then pos += 1
              stop = true
            else if src.startsWith("#snippet ", pos) then
              pos += "#snippet ".length
              val n = parseSnippetDef()
              posBuilder.add(n, SourceSpan(baseOffset + nodeStart))
              nodes += n
            else if src.startsWith("@render ", pos) then
              pos += "@render ".length
              val n = parseRenderCall()
              posBuilder.add(n, SourceSpan(baseOffset + nodeStart))
              nodes += n
            else
              val (parts, end) = ExprExtractor.extractRich(src, pos, posBuilder)
              pos = end
              val hasHtml = parts.exists(_.isInstanceOf[meltc.ast.InlineTemplatePart.Html])
              if hasHtml then
                if parts.nonEmpty then
                  val n = TemplateNode.InlineTemplate(parts)
                  posBuilder.add(n, SourceSpan(baseOffset + nodeStart))
                  nodes += n
              else
                // Pure Scala expression — flatten to Expression node
                val expr = parts.collect { case meltc.ast.InlineTemplatePart.Code(c) => c }.mkString
                if expr.nonEmpty then
                  val n = TemplateNode.Expression(expr)
                  posBuilder.add(n, SourceSpan(baseOffset + nodeStart))
                  nodes += n

          case '<' if isOpenTagAt(pos) =>
            val n = parseElement()
            posBuilder.add(n, SourceSpan(baseOffset + nodeStart))
            nodes += n

          case '<' if src.startsWith("<!--", pos) =>
            val end = src.indexOf("-->", pos + 4)
            pos = if end < 0 then src.length else end + 3

          case _ =>
            val text = collectText()
            if !text.isBlank then
              val n = TemplateNode.Text(HtmlEntities.decode(text))
              posBuilder.add(n, SourceSpan(baseOffset + nodeStart))
              nodes += n

    nodes.result()

  // ── Element / component parsing ───────────────────────────────────────────

  private def parseElement(): TemplateNode =
    pos += 1 // consume '<'

    val tag = collectName()
    skipSpaces()
    val attrs = collectAttrs()

    val selfClose = pos < src.length && src(pos) == '/'
    if selfClose then
      // Warn if a non-void element is self-closed (e.g., <span />)
      if !VoidElements.contains(tag.toLowerCase) && tag.charAt(0).isLower then
        _warnings += ((s"<$tag /> is self-closed but is not a void element — use <$tag></$tag> instead", pos))
      pos += 1
    if pos < src.length && src(pos) == '>' then pos += 1

    val children =
      if selfClose || VoidElements.contains(tag.toLowerCase) then Nil
      else
        val ch = parseNodes(insideTag = true)
        consumeClosingTag()
        collapseWhitespace(ch)

    makeNode(tag, attrs, children)

  private def consumeClosingTag(): Unit =
    if pos < src.length && src.startsWith("</", pos) then
      pos += 2
      skipSpaces()
      while pos < src.length && src(pos) != '>' do pos += 1
      if pos < src.length then pos += 1

  /** Parses a `{#snippet name(params)}...{/snippet}` block.
    * Called after `{#snippet ` has been consumed from the source.
    */
  private def parseSnippetDef(): TemplateNode.SnippetDef =
    // Collect snippet name
    skipSpaces()
    val name = collectName()
    skipSpaces()

    // Collect optional parameter list
    val params: List[SnippetParam] =
      if pos < src.length && src(pos) == '(' then
        pos += 1 // consume '('
        val rawParams = collectBalancedUntil(')')
        if pos < src.length && src(pos) == ')' then pos += 1 // consume ')'
        parseSnippetParams(rawParams)
      else Nil

    // Consume the closing `}` of the opening tag
    skipSpaces()
    if pos < src.length && src(pos) == '}' then pos += 1

    // Parse body nodes until {/snippet}
    val children = parseNodes(insideTag = false)

    TemplateNode.SnippetDef(name, params, children)

  /** Parses a `{@render expr}` directive.
    * Called after `{@render ` has been consumed from the source.
    */
  private def parseRenderCall(): TemplateNode.RenderCall =
    val (parts, end) = ExprExtractor.extractRich(src, pos, posBuilder)
    pos = end
    val expr = parts.collect { case InlineTemplatePart.Code(c) => c }.mkString.trim
    TemplateNode.RenderCall(expr)

  /** Collects characters until `stopChar` is found (or end of input),
    * respecting nested brackets.
    */
  private def collectBalancedUntil(stopChar: Char): String =
    val buf   = new StringBuilder
    var depth = 0
    while pos < src.length && !(src(pos) == stopChar && depth == 0) do
      src(pos) match
        case '(' | '[' | '{' => depth += 1; buf += src(pos)
        case ')' | ']' | '}' =>
          if depth > 0 then { depth -= 1; buf += src(pos) }
          else return buf.toString
        case c => buf += c
      pos += 1
    buf.toString

  /** Parses a comma-separated snippet parameter string like `"todo: Todo, index: Int"`.
    * Handles nested brackets in type annotations (e.g. `Seq[String]`, `T => String`).
    */
  private def parseSnippetParams(raw: String): List[SnippetParam] =
    if raw.trim.isEmpty then return Nil
    val params  = List.newBuilder[SnippetParam]
    val current = new StringBuilder
    var depth   = 0
    raw.foreach {
      case c @ ('[' | '(')   => depth += 1; current += c
      case c @ (']' | ')')   => depth -= 1; current += c
      case ',' if depth == 0 =>
        params += parseOneSnippetParam(current.toString.trim)
        current.clear()
      case c => current += c
    }
    val last = current.toString.trim
    if last.nonEmpty then params += parseOneSnippetParam(last)
    params.result()

  private def parseOneSnippetParam(s: String): SnippetParam =
    val colon = s.indexOf(':')
    if colon < 0 then SnippetParam(s.trim, None)
    else SnippetParam(s.substring(0, colon).trim, Some(s.substring(colon + 1).trim))

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

    // Spread attribute `{...expr}` or shorthand attribute `{varName}`
    if src(pos) == '{' then
      pos += 1 // consume '{'
      val isSpread = pos + 2 < src.length && src(pos) == '.' && src(pos + 1) == '.' && src(pos + 2) == '.'
      if isSpread then
        pos += 3 // consume '...'
        val (expr, end) = ExprExtractor.extract(src, pos)
        pos = end
        return Some(Attr.Spread(expr))
      else
        val (varName, end) = ExprExtractor.extract(src, pos)
        pos = end
        return Some(Attr.Shorthand(varName))

    val name = collectAttrName()
    if name.isEmpty then { pos += 1; return None } // skip unknown char

    skipSpaces()
    if pos >= src.length || src(pos) != '=' then
      // Directive with no value (e.g. `transition:fade`, `transition:fade|global`) or boolean attribute
      val colon = name.indexOf(':')
      return Some(
        if colon >= 0 then
          val kind            = name.substring(0, colon)
          val rest            = name.substring(colon + 1)
          val (dirName, mods) = splitModifiers(rest)
          Attr.Directive(kind, dirName, None, mods)
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
        val value = HtmlEntities.decode(src.substring(start, pos))
        if pos < src.length then pos += 1
        Some(makeAttrStatic(name, value))

      case '\'' =>
        pos += 1
        val start = pos
        while pos < src.length && src(pos) != '\'' do pos += 1
        val value = HtmlEntities.decode(src.substring(start, pos))
        if pos < src.length then pos += 1
        Some(makeAttrStatic(name, value))

      case '{' =>
        pos += 1
        val (expr, end) = ExprExtractor.extract(src, pos)
        pos = end
        Some(makeAttrExpr(name, expr))

      case _ =>
        val start = pos
        while pos < src.length && !src(pos).isWhitespace && src(pos) != '>' && src(pos) != '/' do pos += 1
        val raw = src.substring(start, pos)
        if raw.contains('"') || raw.contains('\'') then
          val cleaned = raw.filterNot(c => c == '"' || c == '\'')
          _warnings += (
            (
              s"""Malformed attribute "$name": unquoted value contains a quote character. """ +
                s"""Did you mean $name="$cleaned"?""",
              start
            )
          )
        Some(makeAttrStatic(name, HtmlEntities.decode(raw)))

  private def makeAttrStatic(name: String, value: String): Attr =
    val colon = name.indexOf(':')
    if colon >= 0 then
      val kind            = name.substring(0, colon)
      val rest            = name.substring(colon + 1)
      val (dirName, mods) = splitModifiers(rest)
      Attr.Directive(kind, dirName, Some(value), mods)
    else Attr.Static(name, value)

  private def makeAttrExpr(name: String, expr: String): Attr =
    val colon = name.indexOf(':')
    if colon >= 0 then
      val kind            = name.substring(0, colon)
      val rest            = name.substring(colon + 1)
      val (dirName, mods) = splitModifiers(rest)
      Attr.Directive(kind, dirName, Some(expr), mods)
    else if name.startsWith("on") && name.length > 2 then Attr.EventHandler(name.substring(2), expr)
    else Attr.Dynamic(name, expr)

  /** Splits `"fade|global|local"` into `("fade", Set("global", "local"))`. */
  private def splitModifiers(s: String): (String, Set[String]) =
    val parts = s.split('|')
    (parts.head, parts.tail.toSet)

  // ── Node factory ──────────────────────────────────────────────────────────

  private def makeNode(tag: String, attrs: List[Attr], children: List[TemplateNode]): TemplateNode =
    tag match
      case "melt:head"     => TemplateNode.Head(children)
      case "melt:window"   => TemplateNode.Window(attrs)
      case "melt:body"     => TemplateNode.Body(attrs)
      case "melt:document" => TemplateNode.Document(attrs)
      case "melt:element"  =>
        val tagExpr = attrs
          .collectFirst {
            case Attr.Dynamic("this", expr) => expr
          }
          .getOrElse {
            _warnings += (("<melt:element> requires a `this={expr}` attribute", pos))
            "\"div\""
          }
        // Validate string literals at meltc phase
        val StringLiteral = """^"([^"]*)"$""".r
        tagExpr match
          case StringLiteral(name) if KnownHtmlTags.contains(name) =>
            _warnings += ((
              s"""<melt:element this={"$name"}> uses a static tag — use <$name> directly""",
              pos
            ))
          case StringLiteral(name) =>
            _warnings += ((
              s""""$name" is not a valid HTML tag name. """ +
                s"""Use a known HTML5 tag or HtmlTag.trusted("$name") for custom elements.""",
              pos
            ))
          case _ => // dynamic expression — type-checked by scalac via HtmlTag
        val restAttrs = attrs.filter {
          case Attr.Dynamic("this", _) => false
          case _                       => true
        }
        TemplateNode.DynamicElement(tagExpr, restAttrs, children)
      case "melt:boundary" =>
        // Separate <melt:pending> and <melt:failed> from main children
        val pendingOpt = children.collectFirst {
          case TemplateNode.Element("melt:pending", _, pChildren) =>
            PendingBlock(pChildren)
        }
        val failedOpt = children.collectFirst {
          case TemplateNode.Element("melt:failed", fAttrs, fChildren) =>
            // <melt:failed (error, reset)> is parsed as BooleanAttr("(error,") + BooleanAttr("reset)")
            val attrNames = fAttrs.collect { case Attr.BooleanAttr(n) => n }
            val combined  = attrNames.mkString(" ").replace("(", "").replace(")", "")
            val params    = combined.split(",").map(_.trim).filter(_.nonEmpty)
            val errorVar  = params.lift(0).getOrElse("error")
            val resetVar  = params.lift(1).getOrElse("reset")
            FailedBlock(errorVar, resetVar, fChildren)
        }
        val mainChildren = children.filterNot {
          case TemplateNode.Element("melt:pending", _, _) => true
          case TemplateNode.Element("melt:failed", _, _)  => true
          case _                                          => false
        }
        TemplateNode.Boundary(attrs, mainChildren, pendingOpt, failedOpt)

      case "melt:key" =>
        val keyExpr = attrs
          .collectFirst {
            case Attr.Dynamic("this", expr) => expr
          }
          .getOrElse {
            _warnings += (("<melt:key> requires a `this={expr}` attribute", pos))
            "0"
          }
        TemplateNode.KeyBlock(keyExpr, children)

      case _ =>
        if tag.charAt(0).isUpper then TemplateNode.Component(tag, attrs, children)
        else TemplateNode.Element(tag, attrs, children)

  // ── Whitespace collapsing ────────────────────────────────────────────────

  /** Collapses inter-element whitespace in a list of nodes, following Svelte conventions.
    *
    *   - Runs of whitespace in text nodes are collapsed to a single space
    *   - Leading whitespace of the first text node and trailing whitespace of the last
    *     text node (within a parent) are trimmed
    *   - Text nodes that become empty after collapsing are removed
    */
  private def collapseWhitespace(nodes: List[TemplateNode]): List[TemplateNode] =
    if nodes.isEmpty then return nodes

    val collapsed = nodes.map {
      case TemplateNode.Text(content) =>
        val ws = content.replaceAll("\\s+", " ")
        TemplateNode.Text(ws)
      case other => other
    }

    // Trim leading whitespace of first text node
    val trimmed1 = collapsed match
      case TemplateNode.Text(t) :: rest =>
        val lt = t.stripLeading()
        if lt.isEmpty then rest else TemplateNode.Text(lt) :: rest
      case other => other

    // Trim trailing whitespace of last text node
    val trimmed2 =
      if trimmed1.isEmpty then trimmed1
      else
        trimmed1.last match
          case TemplateNode.Text(t) =>
            val tt = t.stripTrailing()
            if tt.isEmpty then trimmed1.init else trimmed1.init :+ TemplateNode.Text(tt)
          case _ => trimmed1

    // Remove empty text nodes
    trimmed2.filter {
      case TemplateNode.Text("") => false
      case _                     => true
    }

  // ── Helpers ───────────────────────────────────────────────────────────────

  /** True if `pos` points to `<` followed by a letter or `_` (an opening tag). */
  private def isOpenTagAt(p: Int): Boolean =
    p + 1 < src.length && {
      val next = src(p + 1)
      next.isLetter || next == '_'
    }

  /** Reads a tag or attribute name (letters, digits, `-`, `_`, `.`, `:`).
    * The `:` character is included to support `melt:head`, `melt:window`, and `melt:body`
    * special element tags.
    */
  private def collectName(): String =
    val start = pos
    while pos < src.length &&
      (src(pos).isLetterOrDigit || src(pos) == '-' || src(pos) == '_' || src(pos) == '.' || src(pos) == ':')
    do pos += 1
    src.substring(start, pos)

  /** Reads an attribute name (stops at `=`, whitespace, `>`, `/`).
    * Includes `:` for directive syntax (`bind:value`, `class:active`).
    */
  private def collectAttrName(): String =
    val start = pos
    while pos < src.length && src(pos) != '=' && !src(pos).isWhitespace &&
      src(pos) != '>' && src(pos) != '/'
    do pos += 1
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
          if next.isLetter || next == '_' || next == '/' || src.startsWith("<!--", pos) then stop = true
          else
            buf += '<'; pos += 1
        case c =>
          buf += c; pos += 1
    buf.toString

  private def skipSpaces(): Unit =
    while pos < src.length && src(pos).isWhitespace do pos += 1

object TemplateParser:
  def parse(templateSource: String): List[TemplateNode] =
    val builder = new NodePositions.Builder()
    new TemplateParser(templateSource, baseOffset = 0, posBuilder = builder).parse()

  /** Parses the template and also returns node positions and any warnings. */
  def parseWithWarnings(
    templateSource: String
  ): (List[TemplateNode], NodePositions, List[(String, Int)]) =
    val builder = new NodePositions.Builder()
    val p       = new TemplateParser(templateSource, baseOffset = 0, posBuilder = builder)
    val nodes   = p.parse()
    (nodes, builder.result(), p.warnings)
