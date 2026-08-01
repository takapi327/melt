/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.analysis

import scala.collection.mutable

import melt.NodePositions
import melt.ast.*

/** Warns when a lifecycle registration (`onMount` / `onCleanup` / `effect` /
  * `layoutEffect`) is written inside a reactive template region — a conditional,
  * list, `<melt:key>` block, or `{#snippet}`.
  *
  * Lifecycle calls are meant to run once, at the top level of `<script>`. Placed
  * inside a reactive region they register conditionally (only when that branch
  * renders) or repeatedly (once per list item / re-render), which is almost never
  * intended. Scala can't catch this on its own — the call type-checks fine — so a
  * warning surfaces the footgun.
  */
object LifecyclePlacementChecker:

  /** Matches a lifecycle call: the name followed (allowing spaces) by `(` or `{`. */
  private val callPattern =
    raw"\b(onMount|onCleanup|onDestroy|effect|layoutEffect)\s*[({]".r

  def check(
    ast:               MeltFile,
    positions:         NodePositions = NodePositions.empty,
    templateSource:    String = "",
    templateStartLine: Int = 1
  ): List[(String, Int)] =
    val warnings = mutable.ListBuffer.empty[(String, Int)]

    def warnFor(code: String, node: TemplateNode): Unit =
      callPattern.findFirstMatchIn(code).foreach { m =>
        val span = positions.spanOf(node)
        warnings += (
          s"Lifecycle call '${ m.group(1) }' is inside a reactive region (a conditional, list, " +
            "<melt:key>, or {#snippet}); it will register conditionally/repeatedly, not once at mount. " +
            "Move it to the top level of <script>."
          -> span.absoluteLine(templateSource, templateStartLine)
        )
      }

    def visit(node: TemplateNode, reactive: Boolean): Unit =
      node match
        case TemplateNode.Expression(code) =>
          if reactive then warnFor(code, node)
        case TemplateNode.InlineTemplate(parts) =>
          parts.foreach {
            case InlineTemplatePart.Code(code) => warnFor(code, node)
            case InlineTemplatePart.Html(nodes) => nodes.foreach(visit(_, reactive = true))
          }
        case TemplateNode.KeyBlock(_, children)      => children.foreach(visit(_, reactive = true))
        case TemplateNode.SnippetDef(_, _, children) => children.foreach(visit(_, reactive = true))
        case TemplateNode.Element(_, _, children)        => children.foreach(visit(_, reactive))
        case TemplateNode.Component(_, _, children)      => children.foreach(visit(_, reactive))
        case TemplateNode.Head(children)                 => children.foreach(visit(_, reactive))
        case TemplateNode.DynamicElement(_, _, children) => children.foreach(visit(_, reactive))
        case TemplateNode.Boundary(_, children, pending, failed) =>
          children.foreach(visit(_, reactive))
          pending.foreach(_.children.foreach(visit(_, reactive)))
          failed.foreach(_.children.foreach(visit(_, reactive)))
        case TemplateNode.Await(_, handler, pending, failed) =>
          handler.foreach {
            case InlineTemplatePart.Code(code)  => warnFor(code, node)
            case InlineTemplatePart.Html(nodes) => nodes.foreach(visit(_, reactive = true))
          }
          pending.foreach(_.children.foreach(visit(_, reactive = true)))
          failed.foreach(_.children.foreach(visit(_, reactive = true)))
        case _ => ()

    ast.template.foreach(visit(_, reactive = false))
    warnings.toList
