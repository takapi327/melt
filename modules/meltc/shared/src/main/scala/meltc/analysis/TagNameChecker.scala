/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.analysis

import scala.collection.mutable

import meltc.{ CompileError, NodePositions }
import meltc.ast.*
import meltc.codegen.NameValidators

/** Compile-time validator for HTML tag names and component names (§12.1.3).
  *
  * `<melt:head>`, `<melt:window>`, `<melt:body>` are already normalised into
  * dedicated AST nodes by `TemplateParser`, so they never reach this
  * checker — it therefore only ever sees lowercase HTML tags and PascalCase
  * component references.
  */
object TagNameChecker:

  def check(
    ast:               MeltFile,
    filename:          String,
    positions:         NodePositions = NodePositions.empty,
    templateSource:    String = "",
    templateStartLine: Int = 1
  ): List[CompileError] =
    val errors = mutable.ListBuffer.empty[CompileError]
    ast.template.foreach(node => walk(node, errors, filename, positions, templateSource, templateStartLine))
    errors.toList

  private def walk(
    node:              TemplateNode,
    errors:            mutable.ListBuffer[CompileError],
    filename:          String,
    positions:         NodePositions,
    templateSource:    String,
    templateStartLine: Int
  ): Unit = node match
    case TemplateNode.Element(tag, _, children) =>
      if !NameValidators.isValidTagName(tag) then
        val span = positions.spanOf(node)
        errors += CompileError(
          message = s"Invalid HTML tag name '$tag'. " +
            "Tag names must start with an ASCII letter and follow WHATWG " +
            "custom-element naming rules.",
          line     = span.absoluteLine(templateSource, templateStartLine),
          column   = span.column(templateSource),
          filename = filename
        )
      children.foreach(c => walk(c, errors, filename, positions, templateSource, templateStartLine))

    case TemplateNode.Component(name, _, children) =>
      if !NameValidators.isValidComponentName(name) then
        val span = positions.spanOf(node)
        errors += CompileError(
          message = s"Invalid component name '$name'. " +
            "Component names must be valid Scala type identifiers " +
            "(start with an uppercase letter, followed by letters, " +
            "digits, or underscores).",
          line     = span.absoluteLine(templateSource, templateStartLine),
          column   = span.column(templateSource),
          filename = filename
        )
      children.foreach(c => walk(c, errors, filename, positions, templateSource, templateStartLine))

    case TemplateNode.Head(children) =>
      children.foreach(c => walk(c, errors, filename, positions, templateSource, templateStartLine))

    case TemplateNode.DynamicElement(_, _, children) =>
      children.foreach(c => walk(c, errors, filename, positions, templateSource, templateStartLine))

    case TemplateNode.InlineTemplate(parts) =>
      parts.foreach {
        case InlineTemplatePart.Html(nodes) =>
          nodes.foreach(n => walk(n, errors, filename, positions, templateSource, templateStartLine))
        case _ => ()
      }

    case _ => ()
