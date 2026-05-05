/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.codegen

import meltc.ast.MeltFile
import meltc.NodePositions

/** Common contract for all `meltc` code generators.
  *
  * Two implementations exist:
  *
  *   - [[SpaCodeGen]] — emits Scala.js DOM-manipulating code, the existing
  *     client-rendered mode (`--mode spa`, the default).
  *   - [[SsrCodeGen]] — emits JVM HTML string-generating code for
  *     server-side rendering (`--mode ssr`).
  *
  * Parsing and the AST are shared — only code generation branches. The
  * trait exists so that `MeltCompiler` can dispatch to the correct
  * generator based on [[meltc.CompileMode]] without coupling to either
  * concrete implementation.
  */
trait CodeGen:

  /** Generates a deterministic CSS scope id from the component name and file path.
    *
    * @param objectName Scala object name (e.g. `"Counter"`)
    * @param filePath   source file path (e.g. `"src/components/Counter.melt"`).
    *                   Including the file path structurally prevents collisions
    *                   between same-named components in different directories.
    *                   Defaults to empty string for backwards compatibility.
    */
  def scopeIdFor(objectName: String, filePath: String = ""): String

  /** Compiles a [[meltc.ast.MeltFile]] into a Scala source string.
    *
    * @param ast               parsed `.melt` AST
    * @param objectName        the generated Scala object name (e.g. `"Counter"`)
    * @param pkg               Scala package for the generated file (may be empty)
    * @param scopeId           CSS scope id, typically `scopeIdFor(objectName)`
    * @param hydration         Phase C only — when `true`, [[SpaCodeGen]] additionally
    *                          emits a `@JSExportTopLevel("hydrate", moduleID = ...)`
    *                          hydration entry. [[SsrCodeGen]] ignores this flag.
    *                          Defaults to `false` so that existing single-module
    *                          SPA examples keep working without any build changes.
    * @param sourcePath        absolute filesystem path to the original `.melt` source
    *                          file.  Used to emit the `SOURCE:` field in the
    *                          `-- MELT GENERATED --` source-map comment so that
    *                          sbt's `sourcePositionMappers` can remap scalac errors
    *                          back to the original file.  Defaults to `""` (no
    *                          source-map block emitted) for backwards compatibility.
    * @param scriptBodyLine    1-based line in the `.melt` source where the script body
    *                          begins.  Recorded in the `LINES:` field.
    * @param templateStartLine 1-based line in the `.melt` source where the HTML template
    *                          section begins.  Recorded in the `LINES:` field.
    * @param templateSource    raw text of the HTML template section from the original
    *                          `.melt` file.  Used together with [[positions]] to compute
    *                          per-node source line numbers for finer-grained `LINES:` entries.
    *                          Defaults to `""` (section-level granularity only) for backwards
    *                          compatibility.
    * @param positions         source-position map produced by the parser, keyed by node identity.
    *                          Used to determine the `.melt` line number for each generated
    *                          statement.  Defaults to [[NodePositions.empty]].
    */
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
  ): String
