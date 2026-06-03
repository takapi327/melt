/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.analysis

import scala.util.matching.Regex

import melt.ast.ScriptSection

/** Static analysis pass for `<script lang="scala" module>` sections.
  *
  * Returns `List[(message, localLine)]` tuples (same pattern as [[SecurityChecker]]).
  * `localLine` is 1-based within the module script body.
  * The caller (`MeltCompiler`) adds `moduleBodyLine - 1` to get the absolute `.melt` line.
  */
object ModuleScriptChecker:

  private val PropsRef: Regex = """\bprops\b""".r

  private val StateOrVarRef: Regex = """\bState\s*\(|\bVar\s*\(""".r

  /** Checks for `props` references inside a module script.
    *
    * `props` is a parameter of `def apply()` and does not exist in the module (object) scope.
    * While scalac would catch this, an early Melt-level error improves DX.
    *
    * @return list of `(message, localLine)` pairs; empty if no issues found
    */
  def check(moduleScript: ScriptSection): List[(String, Int)] =
    moduleScript.code.linesIterator.zipWithIndex.toList.flatMap { (line, idx) =>
      if PropsRef.findFirstIn(line).isDefined then
        List((
          "`props` is not available in <script module>; module script runs once and has no component instance",
          idx + 1
        ))
      else Nil
    }

  /** Checks for `State`/`Var` usage inside a module script (SSR context only).
    *
    * In SSR, module-level `State`/`Var` values are shared across ALL requests
    * (JVM object singleton). This method is called by `MeltCompiler` only when
    * `mode == CompileMode.SSR`; the mode guard lives in the caller to avoid a
    * circular dependency between `melt-compiler` and `melt-codegen`.
    *
    * @return list of `(message, localLine)` pairs; empty if no issues found
    */
  def checkSsrState(moduleScript: ScriptSection): List[(String, Int)] =
    if StateOrVarRef.findFirstIn(moduleScript.code).isDefined then
      List((
        "<script module> contains State/Var which is shared across ALL SSR requests; " +
          "use request-scoped state in <script> instead",
        0
      ))
    else Nil
