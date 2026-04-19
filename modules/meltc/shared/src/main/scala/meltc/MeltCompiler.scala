/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc

import meltc.analysis.{
  A11yChecker,
  AttrNameChecker,
  BindingContextChecker,
  RawTextInterpolationChecker,
  SecurityChecker,
  TagNameChecker
}
import meltc.codegen.{ CodeGen, CssScoper, SpaCodeGen, SsrCodeGen }
import meltc.css.StylePreprocessor
import meltc.parser.MeltParser

/** Entry point of the meltc compiler. */
object MeltCompiler:

  /** Compiles a `.melt` source file into `.scala` code.
    *
    * @param source     raw `.melt` source text
    * @param filename   file name used in error messages (e.g. `"App.melt"`)
    * @param objectName Scala object name to generate (e.g. `"App"`)
    * @param pkg        Scala package (may be empty)
    * @param mode       Target code-generation mode. Defaults to [[CompileMode.SPA]]
    *                   for backwards compatibility with existing callers.
    * @param hydration  When `true` and `mode == SPA`, emit
    *                   `@JSExportTopLevel("hydrate", moduleID = ...)`
    *                   hydration entries. Defaults to `false` so existing
    *                   single-module Scala.js examples keep working.
    */
  def compile(
    source:       String,
    filename:     String,
    objectName:   String,
    pkg:          String,
    mode:         CompileMode = CompileMode.SPA,
    hydration:    Boolean = false,
    preprocessor: StylePreprocessor = StylePreprocessor.cssOnly
  ): CompileResult =
    MeltParser.parseWithWarnings(source) match
      case Left(err) =>
        CompileResult(None, None, List(CompileError(err, 0, 0, filename)), Nil)
      case Right(result) =>
        val ast = result.ast
        val codegen: CodeGen = mode match
          case CompileMode.SPA => SpaCodeGen
          case CompileMode.SSR => SsrCodeGen
        val scopeId = codegen.scopeIdFor(objectName, filename)

        // ── Semantic checks (§12.1.2, §12.1.3, §12.1.6) ────────────────
        val semanticErrors =
          AttrNameChecker.check(ast, filename) ++
            TagNameChecker.check(ast, filename) ++
            RawTextInterpolationChecker.check(ast, filename) ++
            BindingContextChecker.check(ast, filename)

        val securityErrors = SecurityChecker.checkErrors(ast, source).map {
          case (msg, line) =>
            CompileError(msg, line, 0, filename)
        }

        val allErrors = semanticErrors ++ securityErrors
        if allErrors.nonEmpty then CompileResult(None, None, allErrors, Nil)
        else
          val code           = codegen.generate(ast, objectName, pkg, scopeId, hydration)
          val parserWarnings = result.warnings.map {
            case (msg, pos) =>
              val line = offsetToLine(source, pos)
              CompileWarning(msg, line, 0, filename)
          }
          val a11yWarnings = A11yChecker.check(ast, source).map {
            case (msg, line) =>
              CompileWarning(msg, line, 0, filename)
          }
          val securityWarnings = SecurityChecker.check(ast, source).map {
            case (msg, line) =>
              CompileWarning(msg, line, 0, filename)
          }
          val allWarnings = parserWarnings ++ a11yWarnings ++ securityWarnings

          val cssResult: Either[String, Option[String]] =
            ast.style match
              case None    => Right(None)
              case Some(s) =>
                preprocessor.process(s.content, s.lang).map(css => Some(CssScoper.scope(css, scopeId)))

          cssResult match
            case Left(err) =>
              CompileResult(None, None, List(CompileError(err, 0, 0, filename)), Nil)
            case Right(scopedCss) =>
              CompileResult(Some(code), scopedCss, Nil, allWarnings)

  /** Converts a character offset to a 1-based line number. */
  private def offsetToLine(source: String, offset: Int): Int =
    if offset <= 0 || source.isEmpty then 1
    else source.take(offset).count(_ == '\n') + 1

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
