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
  objectName:   String,
  pkg:          String,
  scopeId:      String,
  propsType:    Option[IrPropsType],
  scriptBody:   String,             // Scala code (verbatim, not parsed)
  fileImports:  List[String],       // import "path/to/style.css"
  typeDecls:    List[String],       // top-level type declarations (SSR: hoisted out of apply())
  style:        Option[IrStyle],
  template:     List[IrNode],
  hoistedNodes: List[IrHoistedNode] = Nil,  // populated by StaticHoistPass
  hydration:    Boolean,
  sourcePath:   String,
  sourceMap:    IrSourceMap
)

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
  typeParams:      String,           // e.g. "[A]" or ""
  baseName:        String,           // e.g. "Props" or "Config"
  allHaveDefaults: Boolean,          // used for hydration fallback
  scriptDecl:      String            // the case class declaration text
)

case class IrStyle(
  scopedCss: String,                  // already CSS-scoped via CssScoper
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
