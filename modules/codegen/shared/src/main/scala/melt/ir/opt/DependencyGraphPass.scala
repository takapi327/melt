/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.ir.opt

import scala.collection.mutable

import melt.analysis.ScalaTextUtils
import melt.ir.{ mapChildren, IrComponent, IrNode, ReactiveKind }

/** Identifies [[IrNode.IrDynamicText]] nodes that share a single reactive variable
  * and annotates them with a [[IrNode.IrDynamicText.mergeGroup]] name so that
  * [[melt.emit.SpaEmitter]] can emit a single `subscribe` call instead of N separate ones.
  *
  * == Eligibility criteria ==
  * A text node is eligible for merging when ALL of the following hold:
  *   1. `kind == ReactiveKind.LikelyReactive`
  *   2. Exactly ONE reactive variable can be identified as the subscription source
  *      (either via a `.value` call pattern or as a bare reactive variable reference).
  *   3. At least TWO eligible nodes are DIRECT siblings within the SAME parent
  *      [[IrNode.IrElement]] or [[IrNode.IrStaticElement]] and share the same variable.
  *
  * == Scoping constraint ==
  * Merged subscriptions are emitted inside `Hydrating.withChildren(v) { ... }` by
  * [[melt.emit.SpaEmitter.emitElementCore]].  Because Scala block-scoping makes variables
  * declared inside a `withChildren` block invisible outside, only DIRECT children of
  * the same element can be merged.  Nodes inside [[IrNode.IrBoundary]],
  * [[IrNode.IrKeyBlock]], [[IrNode.IrSnippetDef]], or [[IrNode.IrComponent]] create new
  * Scala lambda scopes in the emitter and are therefore never merged across those boundaries.
  *
  * == What counts as a single reactive variable ==
  *   - `{count.value}`, `{count.value * 2}` → source is `count` (`.value` call)
  *   - `{count}` where `count` is in `reactiveVars` → source is `count` (bare reference)
  *   - `{count.value + name.value}` → TWO sources; not eligible
  *
  * == Effect on [[melt.emit.SpaEmitter]] ==
  * The emitter uses `mergeGroup` to:
  *   1. Emit the text node declaration only (without an inline `Bind.*` subscription).
  *   2. Collect all nodes per group and emit a single merged `subscribe` block at the
  *      end of the `withChildren` block.
  *
  * SSR ([[melt.emit.SsrEmitter]]) ignores `mergeGroup` entirely since each render is
  * stateless and subscriptions are not used.
  */
object DependencyGraphPass extends IrPass:
  val name = "DependencyGraphPass"

  private val ValueCallPattern = raw"""\b(\w+)(?:\.\w+)*\.value\b""".r

  def run(ir: IrComponent): IrComponent =
    val newTemplate = processNodes(ir.template, ir.reactiveVars)
    ir.copy(template = newTemplate)

  // ── Tree walk ─────────────────────────────────────────────────────────────

  private def processNodes(nodes: List[IrNode], reactiveVars: Set[String]): List[IrNode] =
    nodes.map(processNode(_, reactiveVars))

  /** Recursively processes one node.
    * Elements delegate to [[processElementChildren]] which handles per-sibling merging.
    * All other container nodes recurse into children without merging across scope boundaries.
    */
  private def processNode(node: IrNode, reactiveVars: Set[String]): IrNode = node match
    case e: IrNode.IrElement       => e.copy(children = processElementChildren(e.children, reactiveVars))
    case e: IrNode.IrStaticElement => e.copy(children = processElementChildren(e.children, reactiveVars))
    case _                         => node.mapChildren(processNode(_, reactiveVars))

  // ── Per-element sibling merge ─────────────────────────────────────────────

  /** Counts and annotates DIRECT [[IrNode.IrDynamicText]] children for merging,
    * then recurses into all non-text children.
    */
  private def processElementChildren(children: List[IrNode], reactiveVars: Set[String]): List[IrNode] =
    // Count reactive vars referenced by direct IrDynamicText children
    val varCounts = mutable.HashMap.empty[String, Int]
    children.foreach {
      case IrNode.IrDynamicText(expr, ReactiveKind.LikelyReactive, None) =>
        extractSingleVarName(expr.code, reactiveVars).foreach { v =>
          varCounts(v) = varCounts.getOrElse(v, 0) + 1
        }
      case _ => ()
    }

    val mergeVars = varCounts.filter(_._2 >= 2).keySet.toSet

    children.map {
      case dt @ IrNode.IrDynamicText(expr, ReactiveKind.LikelyReactive, None) if mergeVars.nonEmpty =>
        extractSingleVarName(expr.code, reactiveVars)
          .filter(mergeVars.contains)
          .fold(dt: IrNode)(v => dt.copy(mergeGroup = Some(v)))
      case other => processNode(other, reactiveVars)
    }

  // ── Reactive variable extraction ─────────────────────────────────────────

  /** Extracts the single reactive variable that drives this expression, or `None`
    * if zero or multiple variables are identified (making subscription merging unsafe).
    */
  private def extractSingleVarName(code: String, reactiveVars: Set[String]): Option[String] =
    val stripped = ScalaTextUtils.stripStringLiterals(code)

    // Collect all vars referenced via .value
    val valueVars = ValueCallPattern
      .findAllMatchIn(stripped)
      .map(_.group(1))
      .toList
      .distinct

    valueVars match
      case single :: Nil => Some(single) // exactly one .value source
      case _ :: _ :: _   => None         // multiple .value sources — cannot merge
      case Nil =>
        // No .value pattern — check if the whole expression is a bare reactive var
        val trimmed = stripped.trim
        if reactiveVars.contains(trimmed) then Some(trimmed)
        else None
