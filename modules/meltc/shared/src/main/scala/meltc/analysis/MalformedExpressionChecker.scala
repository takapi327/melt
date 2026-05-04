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

  private val htmlClosingTagPattern = "</".r

  def check(ast: MeltFile, filename: String): List[CompileError] =
    val errors = mutable.ListBuffer.empty[CompileError]
    ast.template.foreach(node => walk(node, errors, filename))
    errors.toList

  private def walk(
    node:     TemplateNode,
    errors:   mutable.ListBuffer[CompileError],
    filename: String
  ): Unit = node match
    case TemplateNode.Expression(code) =>
      if containsHtmlClosingTag(code) then errors += malformedError(filename)

    case TemplateNode.InlineTemplate(parts) =>
      parts.foreach {
        case InlineTemplatePart.Code(code) =>
          if containsHtmlClosingTag(code) then errors += malformedError(filename)
        case InlineTemplatePart.Html(nodes) =>
          nodes.foreach(n => walk(n, errors, filename))
      }

    case TemplateNode.Element(_, _, children) =>
      children.foreach(c => walk(c, errors, filename))

    case TemplateNode.Component(_, _, children) =>
      children.foreach(c => walk(c, errors, filename))

    case TemplateNode.Head(children) =>
      children.foreach(c => walk(c, errors, filename))

    case TemplateNode.DynamicElement(_, _, children) =>
      children.foreach(c => walk(c, errors, filename))

    case _ => ()

  /** Returns true if `code` contains `</` outside of string literals. */
  private def containsHtmlClosingTag(code: String): Boolean =
    containsHtmlClosingTagInStripped(stripStringLiterals(code))

  private def containsHtmlClosingTagInStripped(code: String): Boolean =
    code.contains("</")

  /** Replaces the contents of string literals with spaces so that `</`
    * inside a string (e.g. `"</span>"`) does not trigger a false positive.
    * Handles both `"..."` and `"""..."""` (triple-quoted) forms.
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
      else
        sb.append(src(i))
        i += 1
    sb.toString

  private def malformedError(filename: String): CompileError =
    CompileError(
      message = "Template expression contains a raw HTML closing tag ('</'), which usually means " +
        "a `{` in your template is missing its closing `}`. " +
        "Check that every `{...}` expression has a matching `}`.",
      line     = 0,
      column   = 0,
      filename = filename
    )
