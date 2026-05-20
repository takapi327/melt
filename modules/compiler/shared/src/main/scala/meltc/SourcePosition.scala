/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc

/** Utility for converting character offsets to 1-based line numbers. */
object SourcePosition:

  /** Returns the 1-based line number corresponding to `offset` in `source`.
    *
    * @param source the full source text
    * @param offset character offset (0-based) into `source`
    */
  def offsetToLine(source: String, offset: Int): Int =
    if offset <= 0 || source.isEmpty then 1
    else source.take(offset).count(_ == '\n') + 1

  /** Returns the 1-based line number of the first occurrence of `needle` in
    * `source`, or `default` if `needle` is not found.
    *
    * Uses a prefix search (up to 40 characters) so callers can pass the full
    * template/script body without performance concerns.
    */
  def searchLine(source: String, needle: String, default: Int = 1): Int =
    val search = needle.take(40).trim
    if search.isEmpty then default
    else
      val idx = source.indexOf(search)
      if idx < 0 then default
      else offsetToLine(source, idx)
