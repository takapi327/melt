/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.template

import melt.NodePositions
import melt.ast.*
import melt.codegen.HtmlVoidElements

/** Raised when a template contains a node the formatter cannot yet re-serialise
  * faithfully (e.g. `<melt:boundary>` / `<melt:await>`). The caller keeps the
  * original template text unchanged. */
final class TemplateFormatUnsupported(val what: String) extends RuntimeException(what)

/** Pretty-prints a parsed `.melt` template AST ([[TemplateNode]]) back to HTML.
  *
  * Design: `memo/design-melt-template-fmt.md`. The renderer relies on the fact
  * that Melt rendering is a pure function of the (collapsed) AST, so a caller can
  * validate output with the `parse(format(x)) == parse(x)` oracle.
  *
  * Whitespace rule (valve-safe): a parent's children are laid out multi-line only
  * where the AST already has inter-sibling whitespace; adjacent siblings with no
  * whitespace between them stay glued on one line; parent-boundary whitespace may
  * be added freely (the parser trims it). Scala-bearing leaves are kept verbatim:
  * [[TemplateNode.Expression]] via its `code`, [[TemplateNode.InlineTemplate]]
  * via a source slice (needs the end offsets recorded by `TemplateParser`).
  */
object TemplateFormatter:

  final case class Options(indent: Int = 2)

  private final class Ctx(val positions: NodePositions, val source: String, val opts: Options)

  /** Renders `nodes` to a canonical HTML string (no trailing newline, column 0). */
  def format(
    nodes:     List[TemplateNode],
    positions: NodePositions,
    source:    String,
    opts:      Options = Options()
  ): String =
    val ctx = new Ctx(positions, source, opts)
    if inlineMode(nodes) then renderChildrenInline(nodes, ctx)
    else renderBlock(nodes, 0, ctx).mkString("\n")

  // ── Node dispatch ───────────────────────────────────────────────────────────

  /** Renders a node as block lines, each already indented to `depth`. */
  private def renderNode(node: TemplateNode, depth: Int, ctx: Ctx): List[String] =
    val pad = " " * (ctx.opts.indent * depth)
    node match
      case TemplateNode.Element(tag, attrs, children)   => renderElementLike(tag, attrs, children, depth, ctx)
      case TemplateNode.Component(name, attrs, children) => renderElementLike(name, attrs, children, depth, ctx)
      case TemplateNode.Head(children)                   => renderElementLike("melt:head", Nil, children, depth, ctx)
      case TemplateNode.Window(attrs)                    => renderElementLike("melt:window", attrs, Nil, depth, ctx)
      case TemplateNode.Body(attrs)                      => renderElementLike("melt:body", attrs, Nil, depth, ctx)
      case TemplateNode.Document(attrs)                  => renderElementLike("melt:document", attrs, Nil, depth, ctx)
      case TemplateNode.DynamicElement(tagExpr, attrs, children) =>
        renderElementLike("melt:element", Attr.Dynamic("this", tagExpr) :: attrs, children, depth, ctx)
      case TemplateNode.KeyBlock(keyExpr, children) =>
        renderElementLike("melt:key", List(Attr.Dynamic("this", keyExpr)), children, depth, ctx)
      case TemplateNode.SnippetDef(name, params, children) => renderSnippet(name, params, children, depth, ctx)
      case TemplateNode.RenderCall(expr)                   => List(s"$pad{@render $expr}")
      case TemplateNode.Text(t)                            => List(pad + escapeText(t))
      case TemplateNode.Comment(content)                   => List(s"$pad<!--$content-->")
      case TemplateNode.Expression(code)                   => List(s"$pad{$code}")
      case TemplateNode.InlineTemplate(_)                  => reindentFirst(inlineTemplateSource(node, ctx), pad)
      case TemplateNode.Boundary(_, _, _, _)               => throw new TemplateFormatUnsupported("<melt:boundary>")
      case TemplateNode.Await(_, _, _, _)                  => throw new TemplateFormatUnsupported("<melt:await>")

  /** Single-line (inline-context) rendering of a node. */
  private def renderInline(node: TemplateNode, ctx: Ctx): String =
    node match
      case TemplateNode.Text(t)          => escapeText(t)
      case TemplateNode.Comment(content)  => s"<!--$content-->"
      case TemplateNode.Expression(code) => s"{$code}"
      case TemplateNode.InlineTemplate(_) => inlineTemplateSource(node, ctx)
      case TemplateNode.RenderCall(expr)  => s"{@render $expr}"
      case TemplateNode.Element(tag, attrs, children)    => inlineElement(tag, attrs, children, ctx)
      case TemplateNode.Component(name, attrs, children) => inlineElement(name, attrs, children, ctx)
      case TemplateNode.Head(children)                   => inlineElement("melt:head", Nil, children, ctx)
      case TemplateNode.Window(attrs)                    => inlineElement("melt:window", attrs, Nil, ctx)
      case TemplateNode.Body(attrs)                      => inlineElement("melt:body", attrs, Nil, ctx)
      case TemplateNode.Document(attrs)                  => inlineElement("melt:document", attrs, Nil, ctx)
      case TemplateNode.DynamicElement(tagExpr, attrs, children) =>
        inlineElement("melt:element", Attr.Dynamic("this", tagExpr) :: attrs, children, ctx)
      case TemplateNode.KeyBlock(keyExpr, children) =>
        inlineElement("melt:key", List(Attr.Dynamic("this", keyExpr)), children, ctx)
      case TemplateNode.SnippetDef(name, params, children) =>
        s"{#snippet $name(${ renderParams(params) })}${ renderChildrenInline(children, ctx) }{/snippet}"
      case TemplateNode.Boundary(_, _, _, _) => throw new TemplateFormatUnsupported("<melt:boundary>")
      case TemplateNode.Await(_, _, _, _)    => throw new TemplateFormatUnsupported("<melt:await>")

  // ── Elements ────────────────────────────────────────────────────────────────

  private def renderElementLike(
    tag:      String,
    attrs:    List[Attr],
    children: List[TemplateNode],
    depth:    Int,
    ctx:      Ctx
  ): List[String] =
    val pad  = " " * (ctx.opts.indent * depth)
    val open = s"$pad<$tag${ formatAttrs(attrs) }"
    if children.isEmpty then
      if HtmlVoidElements.isVoid(tag) then List(s"$open />")
      else List(s"$open></$tag>")
    else if inlineMode(children) then
      // one line: <tag>...children...</tag> (may carry newlines only from a verbatim InlineTemplate)
      (s"$open>" + renderChildrenInline(children, ctx) + s"</$tag>").split("\n", -1).toList
    else
      (s"$open>" :: renderBlock(children, depth + 1, ctx)) :+ s"$pad</$tag>"

  private def inlineElement(tag: String, attrs: List[Attr], children: List[TemplateNode], ctx: Ctx): String =
    val open = s"<$tag${ formatAttrs(attrs) }"
    if children.isEmpty then
      if HtmlVoidElements.isVoid(tag) then s"$open />" else s"$open></$tag>"
    else s"$open>${ renderChildrenInline(children, ctx) }</$tag>"

  private def renderSnippet(
    name:     String,
    params:   List[SnippetParam],
    children: List[TemplateNode],
    depth:    Int,
    ctx:      Ctx
  ): List[String] =
    val pad    = " " * (ctx.opts.indent * depth)
    val header = s"$pad{#snippet $name(${ renderParams(params) })}"
    val footer = s"$pad{/snippet}"
    if children.isEmpty then List(header, footer)
    else if inlineMode(children) then
      (header + renderChildrenInline(children, ctx) + s"{/snippet}").split("\n", -1).toList
    else (header :: renderBlock(children, depth + 1, ctx)) :+ footer

  // ── Children layout ─────────────────────────────────────────────────────────

  /** Block layout: each child on its own line(s).
    *
    * Safe because `TemplateParser` discards whitespace-only text between siblings
    * (`!text.isBlank`), so inter-sibling whitespace is not part of the AST —
    * inserting newlines between block children never changes `parse(...)`. */
  private def renderBlock(children: List[TemplateNode], depth: Int, ctx: Ctx): List[String] =
    children.filterNot(isWhitespaceOnlyText).flatMap(renderNode(_, depth, ctx))

  /** Inline layout: concatenate every child's single-line form, preserving the
    * whitespace held inside content text nodes. */
  private def renderChildrenInline(children: List[TemplateNode], ctx: Ctx): String =
    children.map(renderInline(_, ctx)).mkString

  // ── Attributes ──────────────────────────────────────────────────────────────

  private def formatAttrs(attrs: List[Attr]): String =
    if attrs.isEmpty then "" else " " + attrs.map(renderAttr).mkString(" ")

  private def renderAttr(a: Attr): String = a match
    case Attr.Static(name, value)     => s"""$name="${ escapeAttr(value) }""""
    case Attr.Dynamic(name, expr)     => s"$name={$expr}"
    case Attr.EventHandler(event, e)  => s"on$event={$e}"
    case Attr.BooleanAttr(name)       => name
    case Attr.Spread(expr)            => s"{...$expr}"
    case Attr.Shorthand(varName)      => s"{$varName}"
    case Attr.Directive(kind, name, expr, mods) =>
      val m    = mods.toList.sorted.map("|" + _).mkString
      val base = s"$kind:$name$m"
      expr match
        case Some(e) => s"$base={$e}"
        case None    => base

  private def renderParams(params: List[SnippetParam]): String =
    params
      .map(p => p.typeAnnotation.fold(p.name)(t => s"${ p.name }: $t"))
      .mkString(", ")

  // ── Verbatim InlineTemplate ─────────────────────────────────────────────────

  private def inlineTemplateSource(node: TemplateNode, ctx: Ctx): String =
    val span = ctx.positions.spanOf(node)
    if span.startOffset < 0 || span.endOffset < 0 || span.endOffset > ctx.source.length then
      throw new TemplateFormatUnsupported("InlineTemplate without a recorded source span")
    ctx.source.substring(span.startOffset, span.endOffset)

  /** Indents only the first line of a (possibly multi-line) verbatim block; the
    * interior lines are left untouched to preserve Scala 3 significant indentation. */
  private def reindentFirst(text: String, pad: String): List[String] =
    text.split("\n", -1).toList match
      case Nil          => List(pad)
      case head :: tail => (pad + head) :: tail

  // ── Predicates / escaping ───────────────────────────────────────────────────

  /** Lay a parent out inline when it has readable text content, or when none of
    * its children are element-like (so nothing benefits from its own line). All
    * choices here are valve-safe; this is purely a readability decision. */
  private def inlineMode(children: List[TemplateNode]): Boolean =
    hasContentText(children) || !children.exists(isElementLike)

  private def hasContentText(children: List[TemplateNode]): Boolean =
    children.exists {
      case TemplateNode.Text(t) => t.strip.nonEmpty
      case _                    => false
    }

  private def isElementLike(node: TemplateNode): Boolean = node match
    case _: TemplateNode.Element | _: TemplateNode.Component | _: TemplateNode.Head |
        _: TemplateNode.Window | _: TemplateNode.Body | _: TemplateNode.Document |
        _: TemplateNode.DynamicElement | _: TemplateNode.KeyBlock | _: TemplateNode.SnippetDef |
        _: TemplateNode.Boundary | _: TemplateNode.Await | _: TemplateNode.Comment =>
      true
    case _ => false

  private def isWhitespaceOnlyText(node: TemplateNode): Boolean = node match
    case TemplateNode.Text(t) => t.strip.isEmpty
    case _                    => false

  private def escapeText(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace("{", "&lbrace;").replace("}", "&rbrace;")

  private def escapeAttr(s: String): String =
    s.replace("&", "&amp;").replace("\"", "&quot;")
