/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.analysis

import scala.collection.mutable

import meltc.{ CompileError, NodePositions }
import meltc.ast.*

/** Compile-time check for `{expr}` interpolation inside HTML raw-text
  * elements (`<script>`, `<style>`, `<textarea>`, `<title>`) — see
  * `docs/meltc-ssr-design.md` §12.1.6.
  *
  * Inside raw-text elements the HTML parser does not recognise child
  * tags or entity references, which means any form of escaping is
  * structurally ineffective. The checker therefore emits a compile
  * error on any `TemplateNode.Expression` / `InlineTemplate` found in
  * those positions.
  *
  * '''Exception''': `<title>` directly inside `<melt:head>` is special —
  * `ServerRenderer.head.title` takes care of escaping it properly, so the
  * checker skips `<title>` whose parent is `Head`.
  */
object RawTextInterpolationChecker:

  private val rawTextTags: Set[String] =
    Set("script", "style", "textarea", "title")

  def check(
    ast:               MeltFile,
    filename:          String,
    positions:         NodePositions = NodePositions.empty,
    templateSource:    String = "",
    templateStartLine: Int = 1
  ): List[CompileError] =
    val errors = mutable.ListBuffer.empty[CompileError]
    ast.template.foreach(node =>
      walk(node, parent = None, errors, filename, positions, templateSource, templateStartLine)
    )
    errors.toList

  /** `parent` is `Some("melt:head")` when we are recursing inside a
    * `TemplateNode.Head`, otherwise `None`. This is the only piece of
    * context we need to allow `<title>` interpolation under `<melt:head>`.
    */
  private def walk(
    node:              TemplateNode,
    parent:            Option[String],
    errors:            mutable.ListBuffer[CompileError],
    filename:          String,
    positions:         NodePositions,
    templateSource:    String,
    templateStartLine: Int
  ): Unit = node match
    case TemplateNode.Element(tag, _, children) =>
      val tagLower = tag.toLowerCase
      if rawTextTags.contains(tagLower) then
        // `<title>` inside `<melt:head>` is allowed because the head
        // visitor routes it through `renderer.head.title(...)`.
        val allowInterpolation = tagLower == "title" && parent.contains("melt:head")
        if !allowInterpolation then
          children.foreach {
            case n @ (_: TemplateNode.Expression | _: TemplateNode.InlineTemplate) =>
              val span = positions.spanOf(n)
              errors += CompileError(
                message = s"Expression interpolation is not allowed inside a <$tagLower> " +
                  "element (raw-text element). Escaping is structurally impossible here. " +
                  (if tagLower == "script" then
                     "Use a JSON data attribute and parse it from your client-side code instead."
                   else if tagLower == "style" then "Move dynamic values to attribute bindings (e.g. style={...})."
                   else if tagLower == "textarea" then "Use `bind:value={...}` to populate the textarea content."
                   else "Wrap the element in `<melt:head>` to allow dynamic titles."),
                line     = span.absoluteLine(templateSource, templateStartLine),
                column   = span.column(templateSource),
                filename = filename
              )
            case _ => ()
          }

        // Continue walking so that nested violations are still caught.
        children.foreach(c => walk(c, Some(tagLower), errors, filename, positions, templateSource, templateStartLine))
      else
        children.foreach(c => walk(c, Some(tagLower), errors, filename, positions, templateSource, templateStartLine))

    case TemplateNode.Component(_, _, children) =>
      children.foreach(c => walk(c, parent, errors, filename, positions, templateSource, templateStartLine))

    case TemplateNode.Head(children) =>
      // Children of <melt:head> are walked with parent = "melt:head" so
      // that a <title> immediately inside it can pass through.
      children.foreach(c => walk(c, Some("melt:head"), errors, filename, positions, templateSource, templateStartLine))

    case TemplateNode.DynamicElement(_, _, children) =>
      children.foreach(c => walk(c, parent, errors, filename, positions, templateSource, templateStartLine))

    case TemplateNode.InlineTemplate(parts) =>
      parts.foreach {
        case InlineTemplatePart.Html(nodes) =>
          nodes.foreach(n => walk(n, parent, errors, filename, positions, templateSource, templateStartLine))
        case _ => ()
      }

    case _ => ()
