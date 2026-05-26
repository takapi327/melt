/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.codegen

import melt.ast.*
import melt.NodePositions

/** Generates JVM-targeted HTML string-rendering code from a parsed
  * [[melt.ast.MeltFile]].
  *
  * Each `.melt` file becomes a Scala `object` with a single
  * `apply(props: Props = Props()): RenderResult` method that returns the
  * HTML fragment for the component tree.
  *
  * Selected by `MeltCompiler.compile(...)` when `CompileMode.SSR` is passed.
  *
  * Delegates to [[melt.ir.AstToIr]] + [[melt.emit.SsrEmitter]].
  */
object SsrCodeGen extends CodeGen:

  def scopeIdFor(objectName: String, filePath: String = ""): String =
    SpaCodeGen.scopeIdFor(objectName, filePath)

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
    melt.emit.SsrEmitter.emit(ir)
