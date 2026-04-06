/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc

import meltc.codegen.{ CssScoper, ScalaCodeGen }
import meltc.parser.MeltParser

/** Entry point of the meltc compiler. */
object MeltCompiler:

  /** Compiles a `.melt` source file into `.scala` code.
    *
    * @param source     raw `.melt` source text
    * @param filename   file name used in error messages (e.g. `"App.melt"`)
    * @param objectName Scala object name to generate (e.g. `"App"`)
    * @param pkg        Scala package (may be empty)
    */
  def compile(
    source:     String,
    filename:   String,
    objectName: String,
    pkg:        String
  ): CompileResult =
    MeltParser.parseWithWarnings(source) match
      case Left(err) =>
        CompileResult(None, None, List(CompileError(err, 0, 0, filename)), Nil)
      case Right(result) =>
        val ast      = result.ast
        val scopeId  = ScalaCodeGen.scopeIdFor(objectName)
        val code     = ScalaCodeGen.generate(ast, objectName, pkg, scopeId)
        val warnings = result.warnings.map {
          case (msg, pos) =>
            CompileWarning(msg, 0, pos, filename)
        }
        CompileResult(Some(code), ast.style.map(s => CssScoper.scope(s.css, scopeId)), Nil, warnings)

  /** Convenience overload — derives `objectName` from `filename` and uses no package.
    *
    * `"App.melt"` → `objectName = "App"`, `pkg = ""`
    */
  def compile(source: String, filename: String): CompileResult =
    val objectName = filename.stripSuffix(".melt").capitalize
    compile(source, filename, objectName, "")

/** Result of a compilation. */
case class CompileResult(
  scalaCode: Option[String],
  scopedCss: Option[String],
  errors:    List[CompileError],
  warnings:  List[CompileWarning]
):
  def isSuccess: Boolean = errors.isEmpty

/** A compilation error. */
case class CompileError(
  message:  String,
  line:     Int,
  column:   Int,
  filename: String
)

/** A compilation warning. */
case class CompileWarning(
  message:  String,
  line:     Int,
  column:   Int,
  filename: String
)
