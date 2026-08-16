/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.format

import java.io.{ OutputStreamWriter, PrintWriter }
import java.nio.file.{ Path, Paths }

import scala.collection.mutable.ListBuffer

import org.scalafmt.interfaces.{ Scalafmt, ScalafmtReporter }

/** Formats the Scala inside a `.melt` `<script lang="scala">` section using
  * scalafmt (via `scalafmt-dynamic`, which resolves the version declared in the
  * given `.scalafmt.conf`).
  *
  * Design decisions validated by Phase 0 spike #1 (see `memo/design-melt-fmt.md`):
  *   - Uses the `format(config, file, code)` string API — never `--stdin`, which
  *     silently blanks output for some align/statement layouts.
  *   - `withRespectProjectFilters(false)` so the synthetic filename is not
  *     excluded by `project.git = true` (which would make formatting a no-op).
  *   - No `runner.dialectOverride.withAllowToplevelTerms`: scala3 already parses
  *     the top-level statements that appear in `.melt` scripts.
  *   - Melt string-imports (`import "..."`) are not valid Scala, so they are
  *     pulled aside before formatting and restored (at the top) afterwards.
  *   - Dedent by the block's common indent before formatting, then re-apply the
  *     same indent afterwards so the section keeps its indentation level and the
  *     transform stays idempotent. (The Melt compiler currently requires the
  *     script body at column 0; normalising to a fixed non-zero indent would
  *     need a compiler change to dedent scripts — see the design memo.)
  *
  * Not thread-safe: reuse one instance per (single-threaded) formatting run.
  */
final class ScriptFormatter(scalafmtConfig: Path, indent: Int = 2):

  private val errors = ListBuffer.empty[String]

  private val reporter: ScalafmtReporter = new ScalafmtReporter:
    def error(file:          Path, message: String):    Unit = errors += message
    def error(file:          Path, e:       Throwable): Unit = errors += Option(e.getMessage).getOrElse(e.toString)
    def excluded(file:       Path):                     Unit = errors += s"excluded by project filters: $file"
    def parsedConfig(config: Path, ver:     String):    Unit = ()
    def downloadWriter():             PrintWriter        = new PrintWriter(System.err)
    def downloadOutputStreamWriter(): OutputStreamWriter = new OutputStreamWriter(System.err)

  private val scalafmt: Scalafmt =
    Scalafmt
      .create(getClass.getClassLoader)
      .withReporter(reporter)
      .withRespectProjectFilters(false)

  private val virtualFile = Paths.get("melt-script.scala")
  private val StringImport: String => Boolean = _.matches("""\s*import\s+".*""")

  /** Formats the raw inner text between `<script ...>` and `</script>`.
    * On a scalafmt parse error (or exclusion) returns `Left(message)` and the
    * caller keeps the original text.
    */
  def format(rawInner: String): Either[String, String] =
    val lines = rawInner.split("\n", -1).toList
    // Drop leading/trailing blank lines but keep the indentation of real lines.
    val body = lines.dropWhile(_.trim.isEmpty).reverse.dropWhile(_.trim.isEmpty).reverse
    if body.forall(_.trim.isEmpty) then Right(rawInner) // empty/comment-free script → leave as-is
    else
      val baseIndent = body.filter(_.trim.nonEmpty).map(leadingSpaces).min
      val dedented   = body.map(l => if l.trim.isEmpty then "" else l.substring(baseIndent))

      // Separate Melt string-imports (not valid Scala) from the formattable code.
      val (impLines, restLines) = dedented.partition(StringImport)
      val restCode              = restLines.mkString("\n").strip

      errors.clear()
      val formattedRest =
        if restCode.isEmpty then ""
        else scalafmt.format(scalafmtConfig, virtualFile, restCode + "\n").strip

      if errors.nonEmpty then Left(errors.mkString("; "))
      else
        val outLines =
          impLines ++ (if formattedRest.isEmpty then Nil else formattedRest.split("\n", -1).toList)
        // Normalise every script body to a fixed left margin (`indent`, default
        // 2). Requires the compiler's SectionSplitter to dedent scripts, which it
        // now does — see memo/design-melt-fmt.md "script indent".
        val pad      = " " * indent
        val rendered = outLines.map(l => if l.isEmpty then "" else pad + l).mkString("\n")
        Right("\n" + rendered + "\n")

  private def leadingSpaces(s: String): Int = s.takeWhile(_ == ' ').length
