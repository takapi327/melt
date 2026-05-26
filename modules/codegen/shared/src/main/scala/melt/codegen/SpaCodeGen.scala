/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.codegen

import melt.ast.*
import melt.NodePositions

/** Generates Scala.js source code from a parsed [[melt.ast.MeltFile]].
  *
  * Each `.melt` file becomes a Scala `object` with:
  *   - `apply(): dom.Element` or `apply(props: Props): dom.Element`
  *
  * This generator is selected when `MeltCompiler.compile` receives
  * `CompileMode.SPA` (the default, for backwards compatibility). For the
  * JVM / SSR code generator see [[SsrCodeGen]].
  *
  * Delegates to [[melt.ir.AstToIr]] + [[melt.emit.SpaEmitter]].
  */
object SpaCodeGen extends CodeGen:

  /** Generates a scope ID from the component name and file path (deterministic DJB2 hash).
    *
    * Uses a DJB2-variant hash (same family as Svelte 5) over the combined
    * `filePath:objectName` key so that same-named components in different
    * directories get distinct scope IDs. The 32-bit output space (~4.3 billion)
    * gives a 50% collision probability only after ~77,000 components.
    */
  def scopeIdFor(objectName: String, filePath: String = ""): String =
    val key  = if filePath.nonEmpty then s"$filePath:$objectName" else objectName
    val hash = key.foldLeft(5381L)((h, c) => ((h << 5) - h) ^ c.toLong)
    f"melt-${ hash & 0xffffffffL }%08x"

  def generate(
    ast:               MeltFile,
    objectName:        String,
    pkg:               String,
    scopeId:           String,
    hydration:         Boolean = false,
    sourcePath:        String = "",
    scriptBodyLine:    Int = 1,
    templateStartLine: Int = 1,
    templateSource:    String = "",
    positions:         NodePositions = NodePositions.empty
  ): String =
    val ir = melt.ir.AstToIr.lower(
      ast, objectName, pkg, scopeId, hydration, sourcePath,
      scriptBodyLine, templateStartLine, templateSource, positions
    )
    melt.emit.SpaEmitter.emit(ir)

  /** HTML attributes that carry boolean semantics — their presence means `true`,
    * absence means `false`. Used by [[melt.ir.AstToIr]] to classify
    * `Attr.Dynamic(name, expr)` as [[melt.ir.IrAttr.DynamicBooleanAttr]].
    */
  val htmlBooleanAttrs: Set[String] = Set(
    "disabled",
    "checked",
    "readonly",
    "required",
    "selected",
    "multiple",
    "autofocus",
    "autoplay",
    "controls",
    "default",
    "defer",
    "formnovalidate",
    "hidden",
    "ismap",
    "loop",
    "nomodule",
    "novalidate",
    "open",
    "reversed",
    "scoped",
    "seamless"
  )
