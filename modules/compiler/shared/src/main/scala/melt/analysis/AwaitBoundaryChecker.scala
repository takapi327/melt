/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.analysis

import scala.collection.mutable

import melt.{ CompileError, NodePositions }
import melt.ast.*

/** Compile-time validator for `<melt:await>` placement (async-SSR, T2.0).
  *
  * An async boundary emits a stable marker span
  * (`<!--melt:sb:ID-->` … `<!--/melt:sb:ID-->`) that the server splices over after
  * resolving the query, and that the client adopts during hydration. That only
  * works when the marker sits at a **static** position: if a `<melt:await>` is
  * nested inside a reactive region — a conditional/list (`{cond}` / `{xs.map(…)}`),
  * another await's handler, a `<melt:key>` block, or a `{#snippet}` — its marker
  * lands inside a `<!--[melt:dyn-->` region, so the hydration cursor and the
  * server splice disagree and the DOM tears.
  *
  * T2.0 therefore requires every `<melt:await>` to live outside any reactive
  * region (nested awaits are likewise rejected: single-level only for now).
  */
object AwaitBoundaryChecker:

  def check(
    ast:               MeltFile,
    filename:          String,
    positions:         NodePositions = NodePositions.empty,
    templateSource:    String = "",
    templateStartLine: Int = 1
  ): List[CompileError] =
    val errors = mutable.ListBuffer.empty[CompileError]
    ast.template.foreach(node => walk(node, insideReactive = false, errors, filename, positions, templateSource, templateStartLine))
    errors.toList

  private def walk(
    node:              TemplateNode,
    insideReactive:    Boolean,
    errors:            mutable.ListBuffer[CompileError],
    filename:          String,
    positions:         NodePositions,
    templateSource:    String,
    templateStartLine: Int
  ): Unit =
    def descend(n: TemplateNode, reactive: Boolean): Unit =
      walk(n, reactive, errors, filename, positions, templateSource, templateStartLine)

    node match
      // Static containers keep the current reactivity context.
      case TemplateNode.Element(_, _, children)        => children.foreach(descend(_, insideReactive))
      case TemplateNode.Component(_, _, children)      => children.foreach(descend(_, insideReactive))
      case TemplateNode.Head(children)                 => children.foreach(descend(_, insideReactive))
      case TemplateNode.DynamicElement(_, _, children) => children.foreach(descend(_, insideReactive))
      case TemplateNode.Boundary(_, children, pending, failed) =>
        children.foreach(descend(_, insideReactive))
        pending.foreach(_.children.foreach(descend(_, insideReactive)))
        failed.foreach(_.children.foreach(descend(_, insideReactive)))

      // Reactive regions: an await marker nested here would desync hydration.
      case TemplateNode.InlineTemplate(parts) =>
        parts.foreach {
          case InlineTemplatePart.Html(nodes) => nodes.foreach(descend(_, reactive = true))
          case _                              => ()
        }
      case TemplateNode.KeyBlock(_, children)     => children.foreach(descend(_, reactive = true))
      case TemplateNode.SnippetDef(_, _, children) => children.foreach(descend(_, reactive = true))

      case TemplateNode.Await(_, handler, pending, failed) =>
        if insideReactive then
          val span = positions.spanOf(node)
          errors += CompileError(
            message = "<melt:await> cannot appear inside a reactive region (a conditional, " +
              "list, another <melt:await> handler, <melt:key>, or {#snippet}). Move it to a " +
              "static position in the template so its server-rendered boundary marker stays " +
              "stable for hydration.",
            line     = span.absoluteLine(templateSource, templateStartLine),
            column   = span.column(templateSource),
            filename = filename
          )
        // The handler is itself a reactive region → any nested await is rejected too.
        handler.foreach {
          case InlineTemplatePart.Html(nodes) => nodes.foreach(descend(_, reactive = true))
          case _                              => ()
        }
        pending.foreach(_.children.foreach(descend(_, insideReactive)))
        failed.foreach(_.children.foreach(descend(_, insideReactive)))

      case _ => ()
