/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.analysis

import scala.collection.mutable

import meltc.ast.*
import meltc.codegen.NameValidators
import meltc.{ CompileError, NodePositions }

/** Compile-time validator for static attribute names (§12.1.2).
  *
  * Walks the template AST and reports any element / component attribute
  * whose name contains characters forbidden by the HTML5 spec (whitespace,
  * quotes, `>`, `/`, `=`, or Unicode noncharacters).
  *
  * Dynamic attribute names arrive only through `Attr.Spread`; those are
  * validated at runtime inside `ServerRenderer.spreadAttrs`. This checker
  * only sees the static names that can be determined at compile time.
  */
object AttrNameChecker:

  def check(
    ast:               MeltFile,
    filename:          String,
    positions:         NodePositions = NodePositions.empty,
    templateSource:    String        = "",
    templateStartLine: Int           = 1
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
    case TemplateNode.Element(_, attrs, children) =>
      val span = positions.spanOf(node)
      attrs.foreach(a => checkAttr(a, errors, filename, span, templateSource, templateStartLine))
      children.foreach(c => walk(c, errors, filename, positions, templateSource, templateStartLine))

    case TemplateNode.Component(_, attrs, children) =>
      val span = positions.spanOf(node)
      attrs.foreach(a => checkAttr(a, errors, filename, span, templateSource, templateStartLine))
      children.foreach(c => walk(c, errors, filename, positions, templateSource, templateStartLine))

    case TemplateNode.DynamicElement(_, attrs, children) =>
      val span = positions.spanOf(node)
      attrs.foreach(a => checkAttr(a, errors, filename, span, templateSource, templateStartLine))
      children.foreach(c => walk(c, errors, filename, positions, templateSource, templateStartLine))

    case TemplateNode.Head(children) =>
      children.foreach(c => walk(c, errors, filename, positions, templateSource, templateStartLine))

    case TemplateNode.Window(attrs) =>
      val span = positions.spanOf(node)
      attrs.foreach(a => checkAttr(a, errors, filename, span, templateSource, templateStartLine))

    case TemplateNode.Body(attrs) =>
      val span = positions.spanOf(node)
      attrs.foreach(a => checkAttr(a, errors, filename, span, templateSource, templateStartLine))

    case TemplateNode.InlineTemplate(parts) =>
      parts.foreach {
        case InlineTemplatePart.Html(nodes) =>
          nodes.foreach(n => walk(n, errors, filename, positions, templateSource, templateStartLine))
        case _ => ()
      }

    case _ => ()

  private def checkAttr(
    attr:              Attr,
    errors:            mutable.ListBuffer[CompileError],
    filename:          String,
    parentSpan:        meltc.SourceSpan,
    templateSource:    String,
    templateStartLine: Int
  ): Unit =
    val nameOpt: Option[String] = attr match
      case Attr.Static(n, _)             => Some(n)
      case Attr.Dynamic(n, _)            => Some(n)
      case Attr.BooleanAttr(n)           => Some(n)
      case Attr.Directive(kind, n, _, _) => Some(s"$kind:$n")
      case Attr.EventHandler(event, _)   => Some(s"on$event")
      case Attr.Shorthand(n)             => Some(n)
      case Attr.Spread(_)                => None // dynamic — runtime-checked

    nameOpt.foreach { n =>
      // Directive kind:name pairs contain a `:` which HTML attribute names
      // never do; skip the full-name check for them and only validate the
      // portion after the colon.
      val toValidate = attr match
        case Attr.Directive(_, name, _, _) => name
        case Attr.EventHandler(event, _)   => s"on$event"
        case _                             => n

      if !NameValidators.isValidAttrName(toValidate) then
        errors += CompileError(
          message = s"Invalid attribute name '$n'. " +
            "Attribute names must not contain whitespace, quotes, '>', '/', '=', " +
            "or Unicode noncharacters.",
          line     = parentSpan.absoluteLine(templateSource, templateStartLine),
          column   = parentSpan.column(templateSource),
          filename = filename
        )
    }
