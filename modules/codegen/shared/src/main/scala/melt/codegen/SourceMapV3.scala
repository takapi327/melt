/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.codegen

/** Generates [[https://sourcemaps.info/spec.html Source Maps V3]] JSON from
  * `(generatedLine, sourceLine, sourceColumn)` triples collected by [[LineTracker]].
  *
  * == Encoding ==
  * Each triple is encoded as a 4-field VLQ segment:
  * `[generatedColDelta, sourceFileIdxDelta, sourceLineDelta, sourceColDelta]`
  *
  * Since melt generates exactly one `.scala` per `.melt` file, the source-file
  * index is always 0 and its delta is always 0.  Generated-column is always 0
  * (we map whole lines, not intra-line tokens).  Only the source-line and
  * source-column deltas carry meaningful information.
  *
  * == Cross-platform ==
  * Uses only pure Scala — no `java.util.Base64` or platform-specific APIs —
  * so it compiles for JVM, Scala.js, and Scala Native.
  */
object SourceMapV3:

  private val Base64Chars =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

  // ── VLQ encoding ────────────────────────────────────────────────────────────

  /** Encodes a single signed integer as a Base64 VLQ string. */
  private def encodeVlq(n: Int): String =
    val sb      = new StringBuilder
    // Sign is stored in the LSB of the first group (positive → even, negative → odd)
    var encoded = if n < 0 then ((-n) << 1) | 1 else n << 1
    while
      val digit = encoded & 0x1f        // 5 payload bits
      encoded >>>= 5
      sb += Base64Chars(if encoded > 0 then digit | 0x20 else digit) // set continuation bit
      encoded > 0
    do ()
    sb.toString

  // ── Mappings string ─────────────────────────────────────────────────────────

  /** Encodes source-map entries into a V3 `mappings` string.
    *
    * Lines are separated by `;`.  A line with a mapping contains one 4-field
    * VLQ segment; a line without a mapping is represented by an empty string
    * (two consecutive `;` in the output).
    *
    * @param entries `(generatedLine, sourceLine, sourceColumn)` — all 1-based,
    *                ascending by generatedLine (as produced by [[LineTracker.mappings]])
    */
  private def encodeMappings(entries: Seq[(Int, Int, Int)]): String =
    if entries.isEmpty then return ""
    val sb          = new StringBuilder
    val lastGenLine = entries.last._1
    val byLine      = entries.map(e => e._1 -> e).toMap
    var prevSrcLine = 0 // 0-based, cumulative across all lines
    var prevSrcCol  = 0 // 0-based, cumulative across all lines
    (1 to lastGenLine).foreach { genLine =>
      if genLine > 1 then sb += ';'
      byLine.get(genLine).foreach { case (_, srcLine, srcCol) =>
        val sl = srcLine - 1 // convert to 0-based
        val sc = srcCol  - 1
        sb ++= encodeVlq(0)              // generated column (always start of line → 0)
        sb ++= encodeVlq(0)              // source-file index delta (single source → always 0)
        sb ++= encodeVlq(sl - prevSrcLine)
        sb ++= encodeVlq(sc - prevSrcCol)
        prevSrcLine = sl
        prevSrcCol  = sc
      }
    }
    sb.toString

  // ── Base64 encoding ─────────────────────────────────────────────────────────

  /** Pure-Scala Base64 encoder (RFC 4648, no line wrapping). */
  private def encodeBase64(bytes: Array[Byte]): String =
    val sb = new StringBuilder
    var i  = 0
    while i < bytes.length do
      val b0 = bytes(i) & 0xff
      val b1 = if i + 1 < bytes.length then bytes(i + 1) & 0xff else 0
      val b2 = if i + 2 < bytes.length then bytes(i + 2) & 0xff else 0
      sb += Base64Chars((b0 >> 2) & 0x3f)
      sb += Base64Chars(((b0 << 4) | (b1 >> 4)) & 0x3f)
      sb += (if i + 1 < bytes.length then Base64Chars(((b1 << 2) | (b2 >> 6)) & 0x3f) else '=')
      sb += (if i + 2 < bytes.length then Base64Chars(b2 & 0x3f) else '=')
      i += 3
    sb.toString

  // ── Public API ──────────────────────────────────────────────────────────────

  /** Generates a Source Maps V3 JSON string.
    *
    * @param sourcePath absolute path to the original `.melt` file (embedded in `sources`)
    * @param entries    `(generatedLine, sourceLine, sourceColumn)` — all 1-based
    */
  def generate(sourcePath: String, entries: Seq[(Int, Int, Int)]): String =
    val mappings    = encodeMappings(entries)
    val escapedPath = sourcePath.replace("\\", "\\\\").replace("\"", "\\\"")
    s"""{"version":3,"sources":["$escapedPath"],"names":[],"mappings":"$mappings"}"""

  /** Generates a Source Maps V3 JSON string and encodes it as Base64. */
  def generateBase64(sourcePath: String, entries: Seq[(Int, Int, Int)]): String =
    encodeBase64(generate(sourcePath, entries).getBytes("UTF-8"))
