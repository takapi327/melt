/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.codegen

import melt.analysis.ScalaTextUtils
import melt.ir.ReactiveKind

/** Lightweight heuristic inference of reactive kind for template expressions.
  *
  * Operates on raw Scala code strings without type information.
  * Results are conservative — false positives ([[ReactiveKind.LikelyReactive]])
  * are possible but false negatives ([[ReactiveKind.LikelyStatic]] for a truly
  * reactive expression) are avoided by design.
  *
  * The actual static/reactive distinction at runtime is always resolved by
  * scalac's overload resolution (e.g. `Hydrating.text` overloads).
  * [[ReactiveKind]] is purely a hint for future IR optimisations.
  */
object ReactiveInferencer:

  private val ValueCallPattern = raw"""\b\w+(?:\.\w+)*\.value\b""".r

  /** Infers the reactive kind of a template expression.
    *
    * @param code         the expression code string (before string/comment stripping)
    * @param reactiveVars variable names declared as `State`/`Signal`/`memo` in the script section
    */
  def infer(code: String, reactiveVars: Set[String] = Set.empty): ReactiveKind =
    val stripped = ScalaTextUtils.stripStringLiterals(code)
    if isLiteral(code.trim) then ReactiveKind.LikelyStatic
    else if ValueCallPattern.findFirstIn(stripped).isDefined then ReactiveKind.LikelyReactive
    else if containsReactiveVar(stripped, reactiveVars) then ReactiveKind.LikelyReactive
    else ReactiveKind.Unknown

  /** Returns `true` for numeric, boolean, `null`, or string literals. */
  private def isLiteral(code: String): Boolean = code match
    case "true" | "false" | "null"                         => true
    case s if s.matches("""-?\d+(\.\d+)?[fFdDlL]?""")      => true
    case s if s.startsWith("\"") || s.startsWith("\"\"\"") => true
    case _                                                 => false

  /** Returns `true` if any reactive variable name appears as a word boundary in `stripped`. */
  private def containsReactiveVar(stripped: String, reactiveVars: Set[String]): Boolean =
    reactiveVars.exists { name =>
      raw"\b${ scala.util.matching.Regex.quote(name) }\b".r.findFirstIn(stripped).isDefined
    }
