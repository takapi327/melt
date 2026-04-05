/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc

import meltc.parser.MeltParser

/** Entry point of the meltc compiler. */
object MeltCompiler:

  /** Compiles a `.melt` source file into `.scala` code.
    *
    * Phase 2: parses the source into an AST and reports syntax errors.
    * Code generation (Phase 3) is not yet implemented; `scalaCode` is always `None`.
    */
  def compile(source: String, filename: String): CompileResult =
    MeltParser.parse(source) match
      case Left(err) =>
        CompileResult(None, None, List(CompileError(err, 0, 0, filename)), Nil)
      case Right(_) =>
        // Code generation will be implemented in Phase 3
        CompileResult(None, None, Nil, Nil)

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
