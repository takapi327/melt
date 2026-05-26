/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.analysis

import scala.collection.mutable
import scala.util.matching.Regex

import melt.ast.MeltFile
import melt.CompileWarning

/** Compile-time heuristic checker for missing `effect` / `layoutEffect` dependencies.
  *
  * Mirrors the intent of React's `exhaustive-deps` ESLint rule: warn when a
  * reactive variable is referenced inside an effect body but absent from the
  * deps list.
  *
  * The checker operates on the raw Scala string of the `<script>` block
  * (pre-type-checking) using regex-based analysis, identical in approach to
  * [[MalformedExpressionChecker]]. False-positives are possible for unusual
  * patterns; suppress with `// melt-ignore: exhaustive-deps` on the effect line.
  *
  * === Algorithm ===
  *
  *   1. Collect reactive variable names: `val x = State(...)` or derived signals.
  *   2. Extract every `effect(...)` / `layoutEffect(...)` call: deps list + body.
  *   3. For each call, find reactive var names referenced in the body via `.value`,
  *      `.set`, `.update` or bare name usage, that are absent from the deps list.
  *   4. Emit a [[CompileWarning]] for each missing dependency.
  */
object EffectDepsChecker:

  // Matches any `val x = ...` — used to detect local re-declarations inside
  // an effect body that shadow outer reactive vars.
  private val LocalValPattern: Regex =
    raw"""val\s+(\w+)\s*=""".r

  // Matches lambda params at the START of a body string, e.g.
  //   "(v1, v2) =>"  or  "case (v1, v2) =>"
  private val LambdaMultiParamRx: Regex =
    raw"""^[\s\n\r]*(?:case\s*)?\(([^)]*)\)\s*=>""".r

  // Matches a single lambda param at the start, e.g. "n =>" or "_ =>"
  private val LambdaSingleParamRx: Regex =
    raw"""^[\s\n\r]*(\w+)\s*=>""".r

  def check(
    ast:            MeltFile,
    filename:       String,
    scriptBodyLine: Int = 1
  ): List[CompileWarning] =
    ast.script match
      case None         => Nil
      case Some(script) =>
        val code         = script.code
        val reactiveVars = ScalaTextUtils.extractReactiveVars(code)
        if reactiveVars.isEmpty then Nil
        else
          val calls = extractEffectCalls(code, scriptBodyLine)
          calls.flatMap { call =>
            val depsSet = call.deps.toSet

            // Missing deps: referenced in body but not listed
            val refs            = referencedVars(call.body, reactiveVars)
            val missing         = refs -- depsSet
            val missingWarnings = missing.toList.sorted.map { name =>
              CompileWarning(
                message  = buildMissingMessage(name, call.deps, call.isLayout),
                line     = call.line,
                column   = 0,
                filename = filename
              )
            }

            // Direct access: dep referenced by name in body when a positional arg exists.
            val directWarnings = findDirectAccessDeps(call.body, call.deps).map {
              case (dep, arg) =>
                CompileWarning(
                  message  = buildDirectAccessMessage(dep, arg, call.isLayout),
                  line     = call.line,
                  column   = 0,
                  filename = filename
                )
            }

            // Unused deps: listed but not used in body (neither via dep.value nor via
            // the positional lambda argument).
            val unusedWarnings = findUnusedDeps(call.body, call.deps).map { name =>
              CompileWarning(
                message  = buildUnusedMessage(name, call.deps, call.isLayout),
                line     = call.line,
                column   = 0,
                filename = filename
              )
            }

            missingWarnings ++ directWarnings ++ unusedWarnings
          }

  // ── Internal data ─────────────────────────────────────────────────────────

  private case class EffectCall(
    deps:     List[String],
    body:     String,
    line:     Int,
    isLayout: Boolean
  )

  // ── Step 2: extract effect calls ─────────────────────────────────────────

  private def extractEffectCalls(code: String, scriptBodyLine: Int): List[EffectCall] =
    val stripped = ScalaTextUtils.stripStringLiterals(code)
    val calls    = mutable.ListBuffer.empty[EffectCall]
    val len      = stripped.length

    var i = 0
    while i < len do
      val (isEffect, isLayout, advance) = detectEffectKeyword(stripped, i)
      if isEffect then
        val start = i + advance
        // skip whitespace between keyword and '('
        var j = start
        while j < len && stripped(j) == ' ' do j += 1
        if j < len && stripped(j) == '(' then
          val lineNum = scriptBodyLine + countNewlines(code, i)
          // suppress if the line contains melt-ignore
          val lineContent = getLine(code, i)
          if !lineContent.contains("melt-ignore: exhaustive-deps") then
            val (deps, afterDeps) = extractParenContent(stripped, j)
            if afterDeps < len then
              // skip whitespace between ')' and '{'
              var k = afterDeps
              while k < len && (stripped(k) == ' ' || stripped(k) == '\n' || stripped(k) == '\r') do k += 1
              if k < len && stripped(k) == '{' then
                val (body, _) = extractBraceContent(code, k)
                val depNames  = parseDeps(deps)
                calls += EffectCall(depNames, body, lineNum, isLayout)
        i += advance + 1
      else i += 1

    calls.toList

  /** Returns (isEffect, isLayout, keywordLength) if position `i` starts an
    * `effect` or `layoutEffect` call (not a val assignment).
    */
  private def detectEffectKeyword(s: String, i: Int): (Boolean, Boolean, Int) =
    val layoutKw = "layoutEffect"
    val effectKw = "effect"

    def matchesAt(kw: String): Boolean =
      s.startsWith(kw, i) &&
        (i + kw.length >= s.length || !s(i + kw.length).isLetterOrDigit && s(i + kw.length) != '_') &&
        !isValRhs(s, i)

    if matchesAt(layoutKw) then (true, true, layoutKw.length)
    else if matchesAt(effectKw) then (true, false, effectKw.length)
    else (false, false, 0)

  /** Returns true if position `i` is on the right-hand side of a `val` / `def`
    * assignment, i.e. the identifier before `i` (skipping spaces) is `=`.
    */
  private def isValRhs(s: String, i: Int): Boolean =
    var j = i - 1
    while j >= 0 && s(j) == ' ' do j -= 1
    j >= 0 && s(j) == '='

  /** Extracts the content inside balanced parentheses starting at `start` (the `(`).
    * Returns (inner content, index after closing `)`).
    */
  private def extractParenContent(s: String, start: Int): (String, Int) =
    var depth = 0
    var i     = start
    val sb    = StringBuilder()
    while i < s.length do
      val c = s(i)
      if c == '(' then
        depth += 1
        if depth > 1 then sb.append(c)
      else if c == ')' then
        depth -= 1
        if depth == 0 then return (sb.toString, i + 1)
        else sb.append(c)
      else sb.append(c)
      i += 1
    (sb.toString, i)

  /** Extracts the body inside balanced braces starting at `start` (the `{`).
    * Uses the *original* (non-stripped) code so line counts are correct.
    */
  private def extractBraceContent(s: String, start: Int): (String, Int) =
    var depth = 0
    var i     = start
    val sb    = StringBuilder()
    while i < s.length do
      val c = s(i)
      if c == '{' then
        depth += 1
        if depth > 1 then sb.append(c)
      else if c == '}' then
        depth -= 1
        if depth == 0 then return (sb.toString, i + 1)
        else sb.append(c)
      else sb.append(c)
      i += 1
    (sb.toString, i)

  /** Parses comma-separated dep names from the raw dep string.
    *
    * Handles two forms:
    *   - `"count, name"` — two-dep typed overload
    *   - `"(count, name, userId)"` — N-dep Tuple overload (strip outer parens)
    */
  private def parseDeps(raw: String): List[String] =
    val trimmed = raw.trim
    // N-dep Tuple form: effect((dep1, dep2, dep3)) { ... }
    val inner =
      if trimmed.startsWith("(") && trimmed.endsWith(")") then trimmed.drop(1).dropRight(1)
      else trimmed
    inner.split(',').map(_.trim).filter(_.nonEmpty).toList

  // ── Step 3: find reactive var references in body ──────────────────────────

  private def referencedVars(body: String, reactiveVars: Set[String]): Set[String] =
    val stripped = ScalaTextUtils.stripStringLiterals(body)
    // Detect any local `val x = ...` inside the body that shadows an outer reactive var.
    val localDecls = LocalValPattern.findAllMatchIn(stripped).map(_.group(1)).toSet
    reactiveVars.filter { name =>
      if localDecls.contains(name) then false
      else
        val pattern = raw"\b${ Regex.quote(name) }\b".r
        pattern.findFirstIn(stripped).isDefined
    }

  // ── Step 3b: lambda header parsing ───────────────────────────────────────

  /** Extracts `(params, afterArrow)` from the start of a stripped body string
    * when the lambda parameter count matches `depCount`.
    * Returns `None` when the header cannot be parsed or counts differ.
    */
  private def parseLambdaHeader(
    stripped: String,
    depCount: Int
  ): Option[(List[String], String)] =
    val paramsOpt: Option[List[String]] =
      LambdaMultiParamRx
        .findFirstMatchIn(stripped)
        .map(_.group(1).split(',').map(_.trim).filter(_.nonEmpty).toList)
        .orElse(LambdaSingleParamRx.findFirstMatchIn(stripped).map(m => List(m.group(1))))
    paramsOpt match
      case Some(params) if params.size == depCount =>
        val arrowIdx   = stripped.indexOf("=>")
        val afterArrow = if arrowIdx >= 0 then stripped.substring(arrowIdx + 2) else stripped
        Some((params, afterArrow))
      case _ => None

  // ── Step 3c: find direct-access deps ─────────────────────────────────────

  /** Returns `(dep, argName)` pairs where a dep is referenced by name inside the
    * effect body even though a positional lambda argument already provides its value.
    *
    * Does NOT fire when the positional argument is `_` (intentional trigger/discard
    * pattern) or when the parameter name shadows the dep name.
    * Returns `Nil` when lambda params cannot be parsed.
    */
  private def findDirectAccessDeps(
    body: String,
    deps: List[String]
  ): List[(String, String)] =
    if deps.isEmpty then return Nil
    val stripped   = ScalaTextUtils.stripStringLiterals(body)
    val localDecls = LocalValPattern.findAllMatchIn(stripped).map(_.group(1)).toSet
    parseLambdaHeader(stripped, deps.size) match
      case None                       => Nil
      case Some((params, afterArrow)) =>
        deps.zip(params).flatMap {
          case (dep, param) =>
            if localDecls.contains(dep) then None          // local val shadows dep
            else if param == "_" || param == dep then None // trigger pattern / shadowing
            else if raw"\b${ Regex.quote(dep) }\b".r.findFirstIn(afterArrow).isDefined then Some((dep, param))
            else None
        }

  // ── Step 3d: find unused deps ─────────────────────────────────────────────

  /** Returns dep names that are listed but neither referenced by name in the body
    * nor accessed via their positional lambda argument.
    *
    * `_` params are treated as "used" (intentional trigger/discard pattern).
    * Falls back to a name-only heuristic when the lambda header cannot be parsed.
    */
  private def findUnusedDeps(body: String, deps: List[String]): List[String] =
    if deps.isEmpty then return Nil
    val stripped   = ScalaTextUtils.stripStringLiterals(body)
    val localDecls = LocalValPattern.findAllMatchIn(stripped).map(_.group(1)).toSet
    parseLambdaHeader(stripped, deps.size) match
      case Some((params, afterArrow)) =>
        deps.zip(params).flatMap {
          case (dep, param) =>
            if localDecls.contains(dep) then None
            else
              val depUsed   = raw"\b${ Regex.quote(dep) }\b".r.findFirstIn(afterArrow).isDefined
              val paramUsed = param == "_" ||
                raw"\b${ Regex.quote(param) }\b".r.findFirstIn(afterArrow).isDefined
              if !depUsed && !paramUsed then Some(dep) else None
        }
      case None =>
        // Fallback: only warn when SOME dep names ARE found in body (avoids false
        // positives when all values are accessed via positional arguments).
        val bodyRefs = deps.filter { dep =>
          !localDecls.contains(dep) &&
          raw"\b${ Regex.quote(dep) }\b".r.findFirstIn(stripped).isDefined
        }.toSet
        val unused = deps.toSet -- bodyRefs
        if unused.nonEmpty && unused.size < deps.size then unused.toList.sorted
        else Nil

  // ── Step 4: warning messages ──────────────────────────────────────────────

  private def buildMissingMessage(missing: String, deps: List[String], isLayout: Boolean): String =
    val fn      = if isLayout then "layoutEffect" else "effect"
    val current = if deps.isEmpty then "()" else deps.mkString(", ")
    s"'$missing' is referenced inside the $fn body but is not listed as a dependency. " +
      s"This may cause a bug where the effect does not re-run when '$missing' changes. " +
      s"hint: add it to the deps list like $fn($current, $missing) { ... }"

  private def buildDirectAccessMessage(dep: String, arg: String, isLayout: Boolean): String =
    val fn = if isLayout then "layoutEffect" else "effect"
    s"'$dep' is accessed directly inside the $fn body. " +
      s"Use the provided argument '$arg' instead. " +
      s"Accessing reactive state directly bypasses the deps contract."

  private def buildUnusedMessage(unused: String, deps: List[String], isLayout: Boolean): String =
    val fn      = if isLayout then "layoutEffect" else "effect"
    val newDeps = deps.filterNot(_ == unused)
    val hint    = if newDeps.isEmpty then s"$fn()" else s"$fn(${ newDeps.mkString(", ") })"
    s"'$unused' is listed as a dependency but is never referenced in the $fn body. " +
      s"Removing unused deps prevents unnecessary re-runs. " +
      s"hint: remove it like $hint { ... }"

  // ── Shared utilities ──────────────────────────────────────────────────────

  private def countNewlines(s: String, upTo: Int): Int =
    s.take(upTo).count(_ == '\n')

  private def getLine(s: String, pos: Int): String =
    val start = s.lastIndexOf('\n', pos - 1) + 1
    val end   = s.indexOf('\n', pos)
    if end < 0 then s.substring(start) else s.substring(start, end)
