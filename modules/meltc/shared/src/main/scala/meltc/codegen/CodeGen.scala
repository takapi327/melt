/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.codegen

import meltc.ast.MeltFile

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

  /** Generates a deterministic CSS scope id from the component name. */
  def scopeIdFor(objectName: String): String

  /** Compiles a [[meltc.ast.MeltFile]] into a Scala source string.
    *
    * @param ast        parsed `.melt` AST
    * @param objectName the generated Scala object name (e.g. `"Counter"`)
    * @param pkg        Scala package for the generated file (may be empty)
    * @param scopeId    CSS scope id, typically `scopeIdFor(objectName)`
    */
  def generate(
    ast:        MeltFile,
    objectName: String,
    pkg:        String,
    scopeId:    String
  ): String
