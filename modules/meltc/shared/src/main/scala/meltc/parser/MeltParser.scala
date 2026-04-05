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

  def parse(source: String): Either[String, MeltFile] =
    SectionSplitter.split(source).map { sections =>
      MeltFile(
        script   = sections.rawScript.map(r => ScriptSection(r.code, r.propsType)),
        template = TemplateParser.parse(sections.templateSource),
        style    = sections.style.map(css => StyleSection(css))
      )
    }
