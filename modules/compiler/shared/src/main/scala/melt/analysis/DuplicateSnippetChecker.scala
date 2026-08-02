/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.analysis

import scala.collection.mutable

import melt.{ CompileError, NodePositions }
import melt.ast.*

/** Rejects two `{#snippet name(...)}` definitions with the same name among the same
  * sibling group. They lower to two `val name = …` in one block, so scalac would
  * otherwise report a confusing "already defined" error on generated code. Checking
  * per sibling group avoids flagging same-named snippets in genuinely separate scopes.
  */
object DuplicateSnippetChecker:

  def check(
    ast:               MeltFile,
    filename:          String,
    positions:         NodePositions = NodePositions.empty,
    templateSource:    String = "",
    templateStartLine: Int = 1
  ): List[CompileError] =
    val errors = mutable.ListBuffer.empty[CompileError]

    /** Checks one sibling list for duplicate snippet names, then recurses. */
    def checkSiblings(nodes: List[TemplateNode]): Unit =
      val seen = mutable.HashSet.empty[String]
      nodes.foreach {
        case s @ TemplateNode.SnippetDef(name, _, _) =>
          if !seen.add(name) then
            val span = positions.spanOf(s)
            errors += CompileError(
              s"Duplicate snippet name '$name' in the same scope. Snippet names must be unique among siblings.",
              span.absoluteLine(templateSource, templateStartLine),
              span.column(templateSource),
              filename
            )
        case _ => ()
      }
      nodes.foreach(recurse)

    def recurse(node: TemplateNode): Unit =
      node match
        case TemplateNode.Element(_, _, children)                => checkSiblings(children)
        case TemplateNode.Component(_, _, children)              => checkSiblings(children)
        case TemplateNode.Head(children)                         => checkSiblings(children)
        case TemplateNode.DynamicElement(_, _, children)         => checkSiblings(children)
        case TemplateNode.KeyBlock(_, children)                  => checkSiblings(children)
        case TemplateNode.SnippetDef(_, _, children)             => checkSiblings(children)
        case TemplateNode.Boundary(_, children, pending, failed) =>
          checkSiblings(children)
          pending.foreach(p => checkSiblings(p.children))
          failed.foreach(f => checkSiblings(f.children))
        case TemplateNode.Await(_, _, pending, failed) =>
          pending.foreach(p => checkSiblings(p.children))
          failed.foreach(f => checkSiblings(f.children))
        case TemplateNode.InlineTemplate(parts) =>
          parts.foreach {
            case InlineTemplatePart.Html(nodes) => checkSiblings(nodes)
            case _                              => ()
          }
        case _ => ()

    checkSiblings(ast.template)
    errors.toList
