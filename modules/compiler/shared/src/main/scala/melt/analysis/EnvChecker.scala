/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.analysis

import scala.util.matching.Regex

/** Compile-time guardrail against reading server-only environment values in a
  * client-reachable component.
  *
  * The '''real''' boundary is module separation: [[meltkit.env.PrivateEnv]] is
  * JVM-only, so referencing it from a browser-compiled `.melt` fails to link on the
  * Scala.js cross-build. This checker is the friendly, early half — it flags the
  * obvious mistakes with a Melt-level message before scalac's cryptic "not found"
  * (or, for a raw `sys.env` read, before it silently leaks).
  *
  * Text-based like the other checkers (`.melt` scripts/expressions are unparsed
  * strings), so it is a denylist scan, not a semantic guarantee: it catches direct
  * `sys.env` / `System.getenv` / `PrivateEnv.` reads, not values reached through
  * aliases or helpers. Returns `(message, absoluteLine)`. The caller
  * (`MeltCompiler`) runs it '''only''' when `mode == CompileMode.SPA` — SSR-only
  * components legitimately read env server-side.
  */
object EnvChecker:

  private final case class Rule(pattern: Regex, message: String)

  private val rules = List(
    Rule(
      """\bsys\.env\b""".r,
      "`sys.env` reads an environment variable in a client-reachable component, which " +
        "leaks it into the browser bundle. Read env on the server (a route handler or a " +
        "JVM-only module via meltkit.env.PrivateEnv) and pass only non-secret values to the component."
    ),
    Rule(
      """System\.getenv""".r,
      "`System.getenv` reads an environment variable in a client-reachable component, which " +
        "leaks it into the browser bundle. Read env on the server (via meltkit.env.PrivateEnv) " +
        "and pass only non-secret values to the component."
    ),
    Rule(
      """\bPrivateEnv\.""".r,
      "meltkit.env.PrivateEnv is server-only; referencing it from a client component would leak " +
        "secrets into the browser. Read it in a route handler and pass non-secret data as props."
    )
  )

  // Scans the raw source rather than walking the script/handler AST: the tokens are
  // specific enough that whole-file matching won't trip on markup, and a line scan
  // yields the absolute line directly, whereas AST script bodies carry only
  // body-relative offsets the caller would have to re-base.
  def checkErrors(source: String): List[(String, Int)] =
    source.linesIterator.zipWithIndex.flatMap { (line, idx) =>
      // Drop a trailing line comment first, so documenting the boundary in a comment
      // (e.g. "// PrivateEnv.get(...) would not compile here") isn't itself flagged.
      val code = line.indexOf("//") match
        case -1 => line
        case i  => line.substring(0, i)
      rules.collect { case Rule(pattern, message) if pattern.findFirstIn(code).isDefined => (message, idx + 1) }
    }.toList
