/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.ir.opt

import melt.ir.{ IrComponent, IrHoistedNode, IrNode, mapChildren }

/** Identifies [[IrNode.IrStaticElement]] nodes in the template and marks them
  * for hoisting to object-level `val`s in the emitted code.
  *
  * A static element has only [[melt.ir.IrAttr.StaticAttr]] / [[melt.ir.IrAttr.BooleanAttr]]
  * attributes and only [[IrNode.IrStaticText]] / [[IrNode.IrStaticElement]] children.
  * It contains no reactive bindings and does not need to be recreated per-mount.
  *
  * == Emitter contract ==
  * When a node is replaced by [[IrNode.IrHoistRef]], the [[melt.emit.SpaEmitter]] emits:
  *   - An object-level `private val _hoist_N = ...` (created once)
  *   - A reference `_hoist_N.cloneNode(true)` at the call site
  *
  * Cloning is necessary because each mounted instance needs its own DOM node.
  * [[melt.emit.SsrEmitter]] re-emits the hoisted element inline (cloning has no
  * benefit in SSR since each render is independent).
  */
object StaticHoistPass extends IrPass:
  val name = "StaticHoistPass"

  def run(ir: IrComponent): IrComponent =
    var counter = 0
    val hoisted = scala.collection.mutable.ListBuffer.empty[IrHoistedNode]

    def processNode(node: IrNode): IrNode = node match
      case e: IrNode.IrStaticElement =>
        val id = s"_hoist_$counter"
        counter += 1
        hoisted += IrHoistedNode(id, e)
        IrNode.IrHoistRef(id)   // replace in-place with a reference
      case IrNode.IrInlineTemplate(_) =>
        node                    // do not recurse into bridge nodes
      case other =>
        other.mapChildren(processNode)

    val newTemplate = ir.template.map(processNode)
    ir.copy(
      template     = newTemplate,
      hoistedNodes = hoisted.toList
    )
