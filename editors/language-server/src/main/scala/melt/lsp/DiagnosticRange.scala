/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.lsp

/** Computes the character span (start/end column) a diagnostic should underline.
  *
  * The melt compiler reports errors/warnings with a line but no column (its
  * checkers work at line granularity), so a naive range highlights the whole line
  * from column 0. This narrows the squiggle when possible:
  *
  *   - if the compiler supplied a real 1-based `column`, underline from there;
  *   - otherwise, if the message quotes a token (e.g. `has no field 'emial'`) that
  *     appears on the source line, underline exactly that token;
  *   - else fall back to the whole line.
  *
  * All columns are 0-based (LSP convention).
  */
object DiagnosticRange:

  private val QuotedToken = """[`'"]([^`'"]+)[`'"]""".r

  /** @return `(startColumn, endColumn)`, both 0-based, inclusive-exclusive. */
  def locate(message: String, column: Int, sourceLine: String): (Int, Int) =
    if column > 0 then
      val start = column - 1
      (start, math.max(start + 1, sourceLine.length))
    else
      tokenFrom(message)
        .flatMap(token => indexOf(sourceLine, token).map(i => (i, i + token.length)))
        .getOrElse((0, sourceLine.length))

  private def tokenFrom(message: String): Option[String] =
    QuotedToken.findFirstMatchIn(message).map(_.group(1)).filter(_.trim.nonEmpty)

  private def indexOf(line: String, token: String): Option[Int] =
    line.indexOf(token) match
      case -1 => None
      case i  => Some(i)
