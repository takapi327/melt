/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.analysis

import scala.collection.mutable
import scala.util.matching.Regex

/** Shared text-analysis utilities for Scala source code.
  *
  * Extracted from [[EffectDepsChecker]] so that the same string-stripping and
  * reactive-variable-collection logic can be reused by the IR lowering phase
  * ([[melt.ir.AstToIr]]) without introducing a circular dependency.
  */
object ScalaTextUtils:

  // ── Reactive variable detection patterns ──────────────────────────────────

  private val StateDeclPattern: Regex =
    raw"""val\s+(\w+)\s*=\s*State\s*\(""".r

  private val SignalDerivedPattern: Regex =
    raw"""val\s+(\w+)\s*=\s*\w+\.(map|flatMap|signal)\s*[({]""".r

  private val MemoPattern: Regex =
    raw"""val\s+(\w+)\s*=\s*memo\s*\(""".r

  /** Collects the names of reactive variables declared in a script section.
    *
    * Recognises:
    *   - `val x = State(...)`
    *   - `val x = someSignal.map(...)`  / `.flatMap(...)` / `.signal(...)`
    *   - `val x = memo(...)`
    *
    * String literals and comments are stripped before matching so that patterns
    * inside quoted strings are not misidentified.
    */
  def extractReactiveVars(scriptCode: String): Set[String] =
    val stripped = stripStringLiterals(scriptCode)
    val names    = mutable.Set.empty[String]
    StateDeclPattern.findAllMatchIn(stripped).foreach(m => names += m.group(1))
    SignalDerivedPattern.findAllMatchIn(stripped).foreach(m => names += m.group(1))
    MemoPattern.findAllMatchIn(stripped).foreach(m => names += m.group(1))
    names.toSet

  // ── String / comment stripping ─────────────────────────────────────────────

  /** Replaces the contents of string literals and comments with spaces,
    * while preserving code inside Scala string-interpolation `${...}` blocks.
    *
    * Handles:
    *   - `"..."` plain string literals
    *   - `"""..."""` triple-quoted strings
    *   - `s"..."` / `f"..."` interpolated strings — `${expr}` blocks are kept
    *   - `//` line comments and `/* */` block comments
    */
  def stripStringLiterals(src: String): String =
    val sb  = StringBuilder(src.length)
    var i   = 0
    val len = src.length

    while i < len do
      if i + 2 < len && src(i) == '"' && src(i + 1) == '"' && src(i + 2) == '"' then
        val interp = isInterpPrefix(src, i)
        sb.append("   ")
        i += 3
        var closed = false
        while i < len && !closed do
          if i + 2 < len && src(i) == '"' && src(i + 1) == '"' && src(i + 2) == '"' then
            sb.append("   ")
            i += 3
            closed = true
          else if interp && src(i) == '$' && i + 1 < len && src(i + 1) == '{' then
            i = appendInterpBlock(src, i, sb, len)
          else if interp && src(i) == '$' && i + 1 < len &&
            (src(i + 1).isLetter || src(i + 1) == '_')
          then i = appendInterpIdent(src, i, sb, len)
          else
            sb.append(' ')
            i += 1
      else if src(i) == '"' then
        val interp = isInterpPrefix(src, i)
        sb.append('"')
        i += 1
        var closed = false
        while i < len && !closed do
          if src(i) == '\\' then
            sb.append("  ")
            i += 2
          else if src(i) == '"' then
            sb.append('"')
            i += 1
            closed = true
          else if interp && src(i) == '$' && i + 1 < len && src(i + 1) == '{' then
            i = appendInterpBlock(src, i, sb, len)
          else if interp && src(i) == '$' && i + 1 < len &&
            (src(i + 1).isLetter || src(i + 1) == '_')
          then i = appendInterpIdent(src, i, sb, len)
          else
            sb.append(' ')
            i += 1
      else if i + 1 < len && src(i) == '/' && src(i + 1) == '/' then
        sb.append("//")
        i += 2
        while i < len && src(i) != '\n' do
          sb.append(' ')
          i += 1
      else if i + 1 < len && src(i) == '/' && src(i + 1) == '*' then
        sb.append("/*")
        i += 2
        var closed = false
        while i < len && !closed do
          if i + 1 < len && src(i) == '*' && src(i + 1) == '/' then
            sb.append("*/")
            i += 2
            closed = true
          else
            sb.append(' ')
            i += 1
      else
        sb.append(src(i))
        i += 1

    sb.toString

  /** True if the character before position `pos` is an interpolation prefix
    * (`s` or `f`) that is NOT part of a longer identifier.
    */
  private def isInterpPrefix(src: String, pos: Int): Boolean =
    if pos <= 0 then false
    else
      val prev = src(pos - 1)
      (prev == 's' || prev == 'f') && (pos < 2 || !src(pos - 2).isLetterOrDigit)

  /** Appends a `${...}` interpolation block (starting at the `$` character at
    * position `pos`) to `sb`, preserving all code inside the braces.
    * Returns the position after the closing `}`.
    */
  private def appendInterpBlock(src: String, pos: Int, sb: StringBuilder, len: Int): Int =
    sb.append("${")
    var i     = pos + 2 // skip '${'
    var depth = 1
    while i < len && depth > 0 do
      val c = src(i)
      if c == '{' then
        depth += 1
        sb.append(c)
        i += 1
      else if c == '}' then
        depth -= 1
        sb.append('}')
        i += 1
      else
        sb.append(c)
        i += 1
    i

  /** Appends a `$identifier` simple interpolation (starting at the `$` at
    * position `pos`) to `sb`.
    * Returns the position after the identifier.
    */
  private def appendInterpIdent(src: String, pos: Int, sb: StringBuilder, len: Int): Int =
    sb.append('$')
    var i = pos + 1
    while i < len && (src(i).isLetterOrDigit || src(i) == '_') do
      sb.append(src(i))
      i += 1
    i
