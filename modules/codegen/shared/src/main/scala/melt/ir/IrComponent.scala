/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.ir

/** The top-level IR node representing a compiled `.melt` component.
  *
  * Produced by [[AstToIr.lower]] from a [[melt.ast.MeltFile]].
  * Consumed by [[melt.emit.SpaEmitter]] and [[melt.emit.SsrEmitter]].
  */
case class IrComponent(
  objectName:        String,
  pkg:               String,
  scopeId:           String,
  propsType:         Option[IrPropsType],
  scriptBody:        String, // Scala code (verbatim, not parsed)
  fileImports:       List[String], // import "path/to/style.css"
  typeDecls:         List[String], // top-level type declarations (SSR: hoisted out of apply())
  style:             Option[IrStyle],
  template:          List[IrNode],
  hoistedNodes:      List[IrHoistedNode] = Nil,                  // populated by StaticHoistPass
  hydration:         Boolean,
  sourcePath:        String,
  sourceMap:         IrSourceMap,
  scriptBodyLine:    Int                 = 1,                    // 1-based line of script body start (for source-map)
  templateStartLine: Int                 = 1,                    // 1-based line of template start (for source-map)
  nodePositions:     IrNodePositions     = IrNodePositions.empty, // per-node source positions built by AstToIr
  reactiveVars:      Set[String]         = Set.empty             // State/Signal/memo vars from the script section
)

/** Maps [[IrNode]] instances (by reference identity) to their source (line, col).
  * Built by [[AstToIr.lower]] and consumed by [[melt.emit.SpaEmitter]].
  *
  * Reference-identity semantics (via [[java.util.IdentityHashMap]]) mean that
  * two structurally equal IrNodes created at different positions are tracked
  * separately, just like [[melt.NodePositions]] does for [[melt.ast.TemplateNode]].
  */
final class IrNodePositions(
  private val underlying: java.util.IdentityHashMap[AnyRef, (Int, Int)]
):
  def get(node: IrNode): Option[(Int, Int)] =
    val pos = underlying.get(node)
    if pos == null then None else Some(pos)

object IrNodePositions:
  val empty:     IrNodePositions = new IrNodePositions(new java.util.IdentityHashMap)
  def builder(): Builder         = new Builder

  final class Builder:
    private val map = new java.util.IdentityHashMap[AnyRef, (Int, Int)]
    def put(node: IrNode, line: Int, col: Int): Unit            = map.put(node, (line, col))
    def build():                                IrNodePositions = new IrNodePositions(map)

/** A static element that has been lifted to object level by [[melt.ir.opt.StaticHoistPass]].
  * The [[melt.emit.SpaEmitter]] emits:
  *   - `private val _hoist_N = <original element construction>` at object level (once)
  *   - `_hoist_N.cloneNode(true)` at each call site ([[IrNode.IrHoistRef]])
  *
  * Cloning is required because each mounted component instance needs its own DOM node.
  */
case class IrHoistedNode(id: String, node: IrNode.IrStaticElement)

case class IrPropsType(
  typeName:        String,
  typeParams:      String,  // e.g. "[A]" or ""
  baseName:        String,  // e.g. "Props" or "Config"
  allHaveDefaults: Boolean, // used for hydration fallback
  scriptDecl:      String   // the case class declaration text
)

case class IrStyle(
  scopedCss: String, // already CSS-scoped via CssScoper
  scopeId:   String
)

/** Lightweight source-position mapping for the emitted file.
  * Replaces the ad-hoc `LineTracker.linesMetadata()` string format.
  */
case class IrSourceMap(
  sourcePath: String,
  entries:    Vector[IrSourceMapEntry]
)

case class IrSourceMapEntry(
  generatedLine: Int,
  sourceLine:    Int,
  sourceColumn:  Int
)

object IrSourceMap:
  val empty: IrSourceMap = IrSourceMap("", Vector.empty)
