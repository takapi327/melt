/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.format

import melt.parser.{ MeltParseException, TemplateParser }
import melt.template.{ TemplateFormatUnsupported, TemplateFormatter }

/** Formats a `.melt` template region (Phase 2).
  *
  * Renders the parsed template with [[TemplateFormatter]] and only accepts the
  * result when it re-parses to the **same AST** as the input
  * (`parse(format(x)) == parse(x)`). Because Melt rendering is a pure function of
  * the (collapsed) AST, that equality guarantees the visible output is unchanged.
  *
  * Comments are now part of the AST ([[melt.ast.TemplateNode.Comment]]) so they
  * are preserved and protected by the valve.
  *
  * The template is left byte-for-byte unchanged (with a warning) when:
  *   - it contains a node the formatter can't yet re-serialise (`<melt:boundary>`
  *     / `<melt:await>` → [[TemplateFormatUnsupported]]);
  *   - parsing throws (e.g. an unsupported `{@…}` directive);
  *   - the AST valve fails (a formatter bug — never write possibly-wrong output).
  *
  * All outcomes are `Right`: a skipped template must not abort the rest of the
  * file's formatting.
  */
object TemplateFmt:

  def format(inner: String, opts: TemplateFormatter.Options = TemplateFormatter.Options()): Either[String, String] =
    parse(inner) match
      case None                      => skip(inner, "it could not be parsed")
      case Some((nodesA, positions)) =>
        renderSafely(nodesA, positions, inner, opts) match
          case Left(reason) => skip(inner, reason)
          case Right(out)   =>
            parse(out).map(_._1) match
              case Some(nodesB) if nodesB == nodesA => Right(out)
              case _                                => skip(inner, "the structural check failed")

  private def renderSafely(
    nodes:     List[melt.ast.TemplateNode],
    positions: melt.NodePositions,
    inner:     String,
    opts:      TemplateFormatter.Options
  ): Either[String, String] =
    try Right(TemplateFormatter.format(nodes, positions, inner, opts))
    catch case e: TemplateFormatUnsupported => Left(s"it contains ${ e.what }")

  private def parse(src: String): Option[(List[melt.ast.TemplateNode], melt.NodePositions)] =
    try
      val (nodes, positions, _) = TemplateParser.parseWithWarnings(src)
      Some((nodes, positions))
    catch case _: MeltParseException => None

  private def skip(inner: String, reason: String): Either[String, String] =
    System.err.println(s"[meltfmt] <template> left unchanged: $reason")
    Right(inner)
