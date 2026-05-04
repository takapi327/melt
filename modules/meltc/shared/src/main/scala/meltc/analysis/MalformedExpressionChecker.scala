/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.analysis

import scala.collection.mutable

import meltc.ast.*
import meltc.CompileError

/** Compile-time check for HTML closing tags leaked into Scala expression code.
  *
  * When a `{expr}` in a template is missing its closing `}`, the expression
  * extractor consumes subsequent HTML (e.g. `</span>`, `</div>`) as Scala
  * code. This produces confusing scalac errors like
  * `value </ is not a member of Var[Int]`.
  *
  * This checker detects the pattern and emits a clear meltc error before
  * code generation runs.
  */
object MalformedExpressionChecker:

  /** @param templateSource    raw text of the template section extracted from the `.melt` file.
    *                         Used together with each node's `_pos` offset to compute
    *                         the 1-based line in the original file for error messages.
    * @param templateStartLine 1-based line in the original `.melt` source where the
    *                         template section begins.
    */
  def check(
    ast:               MeltFile,
    filename:          String,
    templateSource:    String = "",
    templateStartLine: Int    = 1
  ): List[CompileError] =
    val errors = mutable.ListBuffer.empty[CompileError]
    ast.template.foreach(node => walk(node, errors, filename, templateSource, templateStartLine))
    errors.toList

  private def walk(
    node:              TemplateNode,
    errors:            mutable.ListBuffer[CompileError],
    filename:          String,
    templateSource:    String,
    templateStartLine: Int
  ): Unit = node match
    case TemplateNode.Expression(code) =>
      if containsHtmlClosingTag(code) then
        errors += malformedError(filename, lineOf(node, templateSource, templateStartLine))

    case TemplateNode.InlineTemplate(parts) =>
      parts.foreach {
        case InlineTemplatePart.Code(code) =>
          if containsHtmlClosingTag(code) then
            errors += malformedError(filename, lineOf(node, templateSource, templateStartLine))
        case InlineTemplatePart.Html(nodes) =>
          nodes.foreach(n => walk(n, errors, filename, templateSource, templateStartLine))
      }

    case TemplateNode.Element(_, _, children) =>
      children.foreach(c => walk(c, errors, filename, templateSource, templateStartLine))

    case TemplateNode.Component(_, _, children) =>
      children.foreach(c => walk(c, errors, filename, templateSource, templateStartLine))

    case TemplateNode.Head(children) =>
      children.foreach(c => walk(c, errors, filename, templateSource, templateStartLine))

    case TemplateNode.DynamicElement(_, _, children) =>
      children.foreach(c => walk(c, errors, filename, templateSource, templateStartLine))

    case TemplateNode.SnippetDef(_, _, children) =>
      children.foreach(c => walk(c, errors, filename, templateSource, templateStartLine))

    case TemplateNode.KeyBlock(_, children) =>
      children.foreach(c => walk(c, errors, filename, templateSource, templateStartLine))

    case TemplateNode.Boundary(_, children, pending, failed) =>
      children.foreach(c => walk(c, errors, filename, templateSource, templateStartLine))
      pending.foreach(_.children.foreach(c => walk(c, errors, filename, templateSource, templateStartLine)))
      failed.foreach(_.children.foreach(c => walk(c, errors, filename, templateSource, templateStartLine)))

    case TemplateNode.RenderCall(expr) =>
      if containsHtmlClosingTag(expr) then
        errors += malformedError(filename, lineOf(node, templateSource, templateStartLine))

    case _ => ()

  /** Converts a node's `_pos` offset (relative to the template source string) to the
    * 1-based line number in the original `.melt` file.
    */
  private def lineOf(node: TemplateNode, templateSource: String, templateStartLine: Int): Int =
    if templateSource.isEmpty || node._pos <= 0 then templateStartLine
    else templateStartLine + templateSource.take(node._pos).count(_ == '\n')

  /** Returns true if `code` contains `</` outside of string literals. */
  private def containsHtmlClosingTag(code: String): Boolean =
    containsHtmlClosingTagInStripped(stripStringLiterals(code))

  private def containsHtmlClosingTagInStripped(code: String): Boolean =
    code.contains("</")

  /** Replaces the contents of string literals and comments with spaces so that
    * `</` inside a string (e.g. `"</span>"`) or comment (e.g. `// </div>`) does
    * not trigger a false positive.
    * Handles `"..."`, `"""..."""` (triple-quoted), `//` line comments, and `/* */` block comments.
    */
  private def stripStringLiterals(src: String): String =
    val sb  = StringBuilder(src.length)
    var i   = 0
    val len = src.length
    while i < len do
      if i + 2 < len && src(i) == '"' && src(i + 1) == '"' && src(i + 2) == '"' then
        // Triple-quoted string
        sb.append("   ")
        i += 3
        var closed = false
        while i < len && !closed do
          if i + 2 < len && src(i) == '"' && src(i + 1) == '"' && src(i + 2) == '"' then
            sb.append("   ")
            i += 3
            closed = true
          else
            sb.append(' ')
            i += 1
      else if src(i) == '"' then
        sb.append('"')
        i += 1
        var closed = false
        while i < len && !closed do
          if src(i) == '\\' then
            sb.append("  ")
            i += 2
          else if src(i) == '"' then
            sb.append('"')
            i += 1
            closed = true
          else
            sb.append(' ')
            i += 1
      else if i + 1 < len && src(i) == '/' && src(i + 1) == '/' then
        // Line comment — skip to end of line
        sb.append("//")
        i += 2
        while i < len && src(i) != '\n' do
          sb.append(' ')
          i += 1
      else if i + 1 < len && src(i) == '/' && src(i + 1) == '*' then
        // Block comment — skip to */
        sb.append("/*")
        i += 2
        var closed = false
        while i < len && !closed do
          if i + 1 < len && src(i) == '*' && src(i + 1) == '/' then
            sb.append("*/")
            i += 2
            closed = true
          else
            sb.append(' ')
            i += 1
      else
        sb.append(src(i))
        i += 1
    sb.toString

  private def malformedError(filename: String, line: Int): CompileError =
    CompileError(
      message = "Template expression contains a raw HTML closing tag ('</'), which usually means " +
        "a `{` in your template is missing its closing `}`. " +
        "Check that every `{...}` expression has a matching `}`.",
      line     = line,
      column   = 0,
      filename = filename
    )
