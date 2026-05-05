/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.parser

import meltc.ast.{ MeltFile, ScriptSection, StyleSection }
import meltc.{ NodePositions, SourcePosition }

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
    * @param positions         source positions for every [[meltc.ast.TemplateNode]]
    *                          in `ast.template`, keyed by object identity.  Use
    *                          [[NodePositions.spanOf]] to retrieve a [[meltc.SourceSpan]]
    *                          and then [[meltc.SourceSpan.absoluteLine]] /
    *                          [[meltc.SourceSpan.column]] to get human-readable coordinates.
    */
  case class ParseResult(
    ast:               MeltFile,
    warnings:          List[(String, Int)],
    scriptBodyLine:    Int          = 1,
    templateStartLine: Int          = 1,
    templateSource:    String       = "",
    positions:         NodePositions = NodePositions.empty
  )

  def parse(source: String): Either[String, MeltFile] =
    parseWithWarnings(source).map(_.ast)

  /** Parses a `.melt` source and returns the AST together with any warnings. */
  def parseWithWarnings(source: String): Either[String, ParseResult] =
    SectionSplitter.split(source).map { sections =>
      val (nodes, positions, templateWarnings) = TemplateParser.parseWithWarnings(sections.templateSource)
      val ast                                  = MeltFile(
        script   = sections.rawScript.map(r => ScriptSection(r.code, r.propsType)),
        template = nodes,
        style    = sections.style.map((content, lang) => StyleSection(content, lang))
      )

      // ── Source-position bookmarks (used for source-map LINES metadata) ────
      // We locate each section in the original source via a short-prefix search
      // rather than tracking byte offsets through the splitter transformations.
      // This is correct for the common case; edge-cases (e.g. duplicated content)
      // are tolerated as approximations.
      val scriptBodyLine: Int = sections.rawScript match
        case None     => 1
        case Some(rs) => SourcePosition.searchLine(source, rs.code.trim, default = 1)

      val templateStartLine: Int =
        SourcePosition.searchLine(source, sections.templateSource, default = 1)

      ParseResult(ast, templateWarnings, scriptBodyLine, templateStartLine, sections.templateSource, positions)
    }
