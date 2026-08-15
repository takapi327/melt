/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.parser

import melt.{ NodePositions, SourcePosition }
import melt.ast.*

/** Top-level parser for `.melt` files.
  *
  * Combines [[SectionSplitter]] and [[TemplateParser]] to produce a complete [[MeltFile]] AST.
  *
  * Usage:
  * {{{
  * MeltParser.parse(source) match
  *   case Right(meltFile) => // use the AST
  *   case Left(error)     => // report the parse error
  * }}}
  */
object MeltParser:

  /** Result of parsing a `.melt` file, containing both the AST and any warnings.
    *
    * @param ast               the parsed component AST
    * @param warnings          parser warnings with their character offsets
    * @param scriptBodyLine    1-based line in the original `.melt` source where the
    *                          script body (inside `<script lang="scala">`) begins
    * @param templateStartLine 1-based line in the original `.melt` source where
    *                          the HTML template section begins
    * @param templateSource    raw text of the HTML template section as extracted by
    *                          [[SectionSplitter]]. Used together with [[positions]] to
    *                          convert node offsets to 1-based line/column numbers.
    * @param positions         source positions for every [[melt.ast.TemplateNode]]
    *                          in `ast.template`, keyed by object identity.  Use
    *                          [[NodePositions.spanOf]] to retrieve a [[melt.SourceSpan]]
    *                          and then [[melt.SourceSpan.absoluteLine]] /
    *                          [[melt.SourceSpan.column]] to get human-readable coordinates.
    */
  case class ParseResult(
    ast:               MeltFile,
    warnings:          List[(String, Int)],
    scriptBodyLine:    Int           = 1,
    templateStartLine: Int           = 1,
    templateSource:    String        = "",
    positions:         NodePositions = NodePositions.empty,
    moduleBodyLine:    Int           = 1
  )

  def parse(source: String): Either[String, MeltFile] =
    parseWithWarnings(source).map(_.ast)

  /** Parses a `.melt` source and returns the AST together with any warnings. */
  def parseWithWarnings(source: String): Either[String, ParseResult] =
    try
      SectionSplitter.split(source).map { sections =>
        val (rawNodes, positions, templateWarnings) = TemplateParser.parseWithWarnings(sections.templateSource)
        // Comments are emitted by TemplateParser (so the formatter can preserve
        // them) but never render — strip them here so the rest of the compiler
        // pipeline (checkers, IR lowering, codegen) sees the AST it always has.
        val nodes = MeltParser.stripComments(rawNodes)

        // ── String import warnings with source-level offset ────────────────
        // Warnings were collected in SectionSplitter.split() alongside the
        // filtered code. Here we convert (message, path) pairs to
        // (message, charOffset) so the compiler can report accurate line numbers.
        val importWarningTuples: List[(String, Int)] =
          sections.rawScript.toList.flatMap { r =>
            r.importWarnings.map { (msg, path) =>
              val needle = s"""import "$path""""
              val offset = source.indexOf(needle)
              (msg, if offset >= 0 then offset else 0)
            }
          }

        val moduleImportWarningTuples: List[(String, Int)] =
          sections.moduleScript.toList.flatMap { r =>
            r.importWarnings.map { (msg, path) =>
              val needle = s"""import "$path""""
              val offset = source.indexOf(needle)
              (msg, if offset >= 0 then offset else 0)
            }
          }

        val ast = MeltFile(
          script       = sections.rawScript.map(r => ScriptSection(r.code, r.imports)),
          template     = nodes,
          style        = sections.style.map((content, lang) => StyleSection(content, lang)),
          moduleScript = sections.moduleScript.map(r => ScriptSection(r.code, r.imports))
        )

        // ── Source-position bookmarks (used for source-map LINES metadata) ────
        // We locate each section in the original source via a short-prefix search
        // rather than tracking byte offsets through the splitter transformations.
        // This is correct for the common case; edge-cases (e.g. duplicated content)
        // are tolerated as approximations.
        val scriptBodyLine: Int = sections.rawScript match
          case None     => 1
          case Some(rs) => SourcePosition.searchLine(source, rs.code.trim, default = 1)

        val moduleBodyLine: Int = sections.moduleScript match
          case None     => 1
          case Some(ms) => SourcePosition.searchLine(source, ms.code.trim, default = 1)

        val templateStartLine: Int =
          SourcePosition.searchLine(source, sections.templateSource, default = 1)

        ParseResult(
          ast,
          templateWarnings ++ importWarningTuples ++ moduleImportWarningTuples,
          scriptBodyLine,
          templateStartLine,
          sections.templateSource,
          positions,
          moduleBodyLine = moduleBodyLine
        )
      }
    catch case e: MeltParseException => Left(e.errorMessage)

  /** Recursively removes [[TemplateNode.Comment]] nodes from a template AST.
    * Comments are non-rendering; the compiler pipeline never handled them, so
    * they are dropped here (after the formatter-facing parse has kept them).
    *
    * Identity-preserving: a node/list whose subtree contains no comment is
    * returned unchanged (`eq`), so the [[melt.NodePositions]] IdentityHashMap
    * built during parsing still resolves source spans for the surviving nodes.
    */
  private def stripComments(nodes: List[TemplateNode]): List[TemplateNode] =
    var changed = false
    val out     = List.newBuilder[TemplateNode]
    nodes.foreach {
      case _: TemplateNode.Comment => changed = true
      case n =>
        val sn = stripNode(n)
        if sn ne n then changed = true
        out += sn
    }
    if changed then out.result() else nodes

  private def stripNode(node: TemplateNode): TemplateNode = node match
    case TemplateNode.Element(tag, attrs, children) =>
      val sc = stripComments(children); if sc eq children then node else TemplateNode.Element(tag, attrs, sc)
    case TemplateNode.Component(name, attrs, children) =>
      val sc = stripComments(children); if sc eq children then node else TemplateNode.Component(name, attrs, sc)
    case TemplateNode.Head(children) =>
      val sc = stripComments(children); if sc eq children then node else TemplateNode.Head(sc)
    case TemplateNode.DynamicElement(tag, attrs, children) =>
      val sc = stripComments(children); if sc eq children then node else TemplateNode.DynamicElement(tag, attrs, sc)
    case TemplateNode.KeyBlock(keyExpr, children) =>
      val sc = stripComments(children); if sc eq children then node else TemplateNode.KeyBlock(keyExpr, sc)
    case TemplateNode.SnippetDef(name, params, children) =>
      val sc = stripComments(children); if sc eq children then node else TemplateNode.SnippetDef(name, params, sc)
    case TemplateNode.InlineTemplate(parts) =>
      val sp = stripParts(parts); if sp eq parts then node else TemplateNode.InlineTemplate(sp)
    case TemplateNode.Boundary(attrs, children, pending, failed) =>
      val sc = stripComments(children)
      val sp = stripPendingOpt(pending)
      val sf = stripFailedOpt(failed)
      if (sc eq children) && (sp eq pending) && (sf eq failed) then node
      else TemplateNode.Boundary(attrs, sc, sp, sf)
    case TemplateNode.Await(valueExpr, handler, pending, failed) =>
      val sh = stripParts(handler)
      val sp = stripPendingOpt(pending)
      val sf = stripFailedOpt(failed)
      if (sh eq handler) && (sp eq pending) && (sf eq failed) then node
      else TemplateNode.Await(valueExpr, sh, sp, sf)
    case other => other // Text/Expression/RenderCall/Window/Body/Document

  private def stripParts(parts: List[InlineTemplatePart]): List[InlineTemplatePart] =
    var changed = false
    val out     = parts.map {
      case h @ InlineTemplatePart.Html(nodes) =>
        val sn = stripComments(nodes)
        if sn eq nodes then h else { changed = true; InlineTemplatePart.Html(sn) }
      case code => code
    }
    if changed then out else parts

  private def stripPendingOpt(p: Option[PendingBlock]): Option[PendingBlock] = p match
    case Some(pb) => val sc = stripComments(pb.children); if sc eq pb.children then p else Some(PendingBlock(sc))
    case None     => p

  private def stripFailedOpt(f: Option[FailedBlock]): Option[FailedBlock] = f match
    case Some(fb) =>
      val sc = stripComments(fb.children)
      if sc eq fb.children then f else Some(FailedBlock(fb.errorVar, fb.resetVar, sc))
    case None => f
