/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.css

/** Formats a parsed CSS AST ([[CssNode]]) into a canonical, indented CSS string.
  *
  * This is the "案a-light" formatter from `memo/design-melt-css-fmt.md`: it
  * re-emits the existing AST with normalised whitespace, one declaration per
  * line, and consistent indentation. Declaration *values* are kept verbatim
  * (only surrounding/collapsible whitespace is normalised) so the transform
  * never changes meaning; deep value normalisation (colour casing, argument
  * spacing) is intentionally out of scope.
  *
  * Separate from [[CssSerializer]] (which is scoping-only and must not change):
  * this one is for the `.melt` formatter and never runs on the compile path.
  *
  * Output is rendered at column 0 (depth-0 nodes get no leading indent); the
  * caller (`StyleFormatter`) re-applies the section's left margin, exactly like
  * the `<script>` formatter does.
  */
object CssFormatter:

  /** Layout of a selector list (`a, b, c`). */
  enum SelectorList:
    case Newline    // each selector on its own line (default)
    case SingleLine // `a, b, c` on one line

  final case class Options(
    indent:       Int          = 2,
    selectorList: SelectorList = SelectorList.Newline
  )

  /** Renders `nodes` as a canonical CSS string (no trailing newline). */
  def format(nodes: List[CssNode], opts: Options = Options()): String =
    renderNodes(nodes, depth = 0, opts, topLevel = true).mkString("\n")

  // ── Node rendering ──────────────────────────────────────────────────────────

  /** Renders a node list to lines. Top-level rules/at-rules are separated by a
    * blank line; inside a block, declarations and nested rules are consecutive.
    */
  private def renderNodes(nodes: List[CssNode], depth: Int, opts: Options, topLevel: Boolean): List[String] =
    val blocks = nodes.map(renderNode(_, depth, opts))
    if topLevel then
      blocks match
        case Nil     => Nil
        case h :: t  => h ++ t.flatMap("" :: _)
    else blocks.flatten

  private def renderNode(node: CssNode, depth: Int, opts: Options): List[String] =
    val pad = " " * (opts.indent * depth)
    node match
      case CssNode.StyleRule(selector, body) =>
        val selLines = normalizeSelector(selector, opts).split("\n", -1).toList
        val head = selLines.zipWithIndex.map { (s, i) =>
          if i == selLines.size - 1 then s"$pad$s {" else s"$pad$s"
        }
        head ++ renderNodes(body, depth + 1, opts, topLevel = false) ++ List(s"$pad}")

      case CssNode.AtRule(name, prelude, None) =>
        val tail = if prelude.nonEmpty then s" $prelude" else ""
        List(s"$pad@$name$tail;")

      case CssNode.AtRule(name, prelude, Some(body)) =>
        val open = if prelude.nonEmpty then s"$pad@$name $prelude {" else s"$pad@$name {"
        val inner =
          if CssNode.PassthroughAtRules.contains(name) then
            // keyframes / font-face etc.: body is one RawText — re-indent only.
            body.flatMap {
              case CssNode.RawText(text) => reindentRawBlock(text, depth + 1, opts)
              case other                 => renderNode(other, depth + 1, opts)
            }
          else renderNodes(body, depth + 1, opts, topLevel = false)
        (open :: inner) :+ s"$pad}"

      case CssNode.RawText(text) =>
        List(s"$pad${ formatDeclaration(text) }")

      case CssNode.Comment(text) =>
        // Position only; comment body is never altered.
        List(s"$pad$text")

  // ── Declarations ────────────────────────────────────────────────────────────

  /** Normalises one declaration (`property: value;`).
    *
    * Splits on the first top-level `:`, trims the property, and normalises the
    * value's whitespace (verbatim otherwise). Custom properties (`--x`) keep
    * their value untouched apart from trimming, since it may be an arbitrary
    * token stream. A trailing `;` is always emitted.
    */
  private def formatDeclaration(raw: String): String =
    val text = raw.strip.stripSuffix(";").strip
    findTopLevelColon(text) match
      case -1 => text // malformed / not a declaration — leave as-is (already trimmed)
      case i  =>
        val prop  = text.substring(0, i).strip
        val value = text.substring(i + 1).strip
        val v     = if prop.startsWith("--") then value else collapseWhitespace(value)
        if v.isEmpty then s"$prop:;" else s"$prop: $v;"

  /** Index of the first `:` at depth 0, outside strings/`()`/`[]`; -1 if none. */
  private def findTopLevelColon(s: String): Int =
    var i     = 0
    var depth = 0
    while i < s.length do
      s(i) match
        case '"' | '\'' => i = skipString(s, i)
        case '(' | '['  => depth += 1; i += 1
        case ')' | ']'  => depth -= 1; i += 1
        case ':' if depth == 0 => return i
        case _          => i += 1
    -1

  /** Collapses runs of whitespace to a single space, outside quoted strings. */
  private def collapseWhitespace(s: String): String =
    val out = new StringBuilder
    var i   = 0
    while i < s.length do
      val c = s(i)
      if c == '"' || c == '\'' then
        val end = skipString(s, i)
        out ++= s.substring(i, end)
        i = end
      else if c.isWhitespace then
        while i < s.length && s(i).isWhitespace do i += 1
        if out.nonEmpty && i < s.length then out += ' '
      else
        out += c
        i += 1
    out.toString

  // ── Selectors ───────────────────────────────────────────────────────────────

  /** Token/bracket/string-aware selector normaliser (design memo, round 4).
    *
    * Operates only at depth 0 (outside `()`/`[]`) and outside quoted strings, so
    * attribute operators (`~=`, `|=`, …), quoted attribute values, `:is()`
    * inner commas, and `An+B` (`2n+1`) are all preserved:
    *   - collapses whitespace,
    *   - the selector-list `,` becomes `,\n` (Newline) or `, ` (SingleLine),
    *   - combinators `>` `+` `~` get exactly one space on each side.
    */
  private def normalizeSelector(selector: String, opts: Options): String =
    val s   = selector.strip
    val out = new StringBuilder
    var i   = 0
    var depth = 0

    def trimTrailingSpaces(): Unit =
      while out.nonEmpty && out.last == ' ' do out.deleteCharAt(out.length - 1)
    def skipFollowingWs(): Unit =
      while i + 1 < s.length && s(i + 1).isWhitespace do i += 1

    while i < s.length do
      val c = s(i)
      if c == '"' || c == '\'' then
        val end = skipString(s, i)
        out ++= s.substring(i, end)
        i = end
      else if c == '(' || c == '[' then
        depth += 1; out += c; i += 1
      else if c == ')' || c == ']' then
        depth -= 1; out += c; i += 1
      else if c.isWhitespace then
        while i < s.length && s(i).isWhitespace do i += 1
        if out.nonEmpty && i < s.length then out += ' '
      else if depth == 0 && c == ',' then
        trimTrailingSpaces()
        out += ','
        out ++= (if opts.selectorList == SelectorList.Newline then "\n" else " ")
        skipFollowingWs(); i += 1
      else if depth == 0 && (c == '>' || c == '+' || c == '~') then
        trimTrailingSpaces()
        if out.nonEmpty then out += ' '
        out += c; out += ' '
        skipFollowingWs(); i += 1
      else
        out += c; i += 1
    out.toString

  // ── Passthrough at-rule bodies ──────────────────────────────────────────────

  /** Re-indents a verbatim raw block (keyframes/font-face body) to `depth`,
    * mirroring the script formatter: dedent by common indent, re-pad.
    */
  private def reindentRawBlock(text: String, depth: Int, opts: Options): List[String] =
    val raw  = text.split("\n", -1).toList
    val body = raw.dropWhile(_.trim.isEmpty).reverse.dropWhile(_.trim.isEmpty).reverse
    if body.forall(_.trim.isEmpty) then Nil
    else
      val base     = body.filter(_.trim.nonEmpty).map(l => l.takeWhile(_ == ' ').length).min
      val pad      = " " * (opts.indent * depth)
      body.map(l => if l.trim.isEmpty then "" else pad + l.substring(base))

  // ── Shared scanning ─────────────────────────────────────────────────────────

  /** Given `s(i)` is a quote, returns the index just past the closing quote
    * (or end of string). Honours `\` escapes.
    */
  private def skipString(s: String, from: Int): Int =
    val q = s(from)
    var i = from + 1
    while i < s.length && s(i) != q do
      if s(i) == '\\' then i += 1
      i += 1
    if i < s.length then i + 1 else i
