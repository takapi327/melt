/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.parser

import meltc.ast.{ MeltFile, ScriptSection, StyleSection }

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

  /** Result of parsing a `.melt` file, containing both the AST and any warnings. */
  case class ParseResult(ast: MeltFile, warnings: List[(String, Int)])

  def parse(source: String): Either[String, MeltFile] =
    parseWithWarnings(source).map(_.ast)

  /** Parses a `.melt` source and returns the AST together with any warnings. */
  def parseWithWarnings(source: String): Either[String, ParseResult] =
    SectionSplitter.split(source).map { sections =>
      val (nodes, templateWarnings) = TemplateParser.parseWithWarnings(sections.templateSource)
      val ast                       = MeltFile(
        script   = sections.rawScript.map(r => ScriptSection(r.code, r.propsType)),
        template = nodes,
        style    = sections.style.map((content, lang) => StyleSection(content, lang))
      )
      ParseResult(ast, templateWarnings)
    }
