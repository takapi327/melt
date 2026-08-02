/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.sbt

/** A full Source Maps V3 `mappings` codec (multi-source, multi-segment, 4/5-field),
  * used to compose the Scala.js linker map (`.js` → `.scala`) with each generated
  * file's `.scala` → `.melt` map so browser debuggers land in the original `.melt`.
  *
  * The simpler encoder in `melt.codegen.SourceMapV3` only handles the single-source,
  * one-segment-per-line case produced at codegen time and cannot round-trip a real
  * linker map — hence this standalone codec.
  *
  * All coordinates in the decoded form are ABSOLUTE and 0-based; the VLQ delta
  * encoding used by the wire format is handled internally.
  */
object SourceMapV3Codec:

  /** A resolved position within a source file. `nameIndex` is present for 5-field
    * segments. */
  final case class SourceRef(srcIndex: Int, srcLine: Int, srcCol: Int, nameIndex: Option[Int])

  /** One mapping segment: a generated column and (for all but "unmapped" segments)
    * the source position it maps to. */
  final case class Segment(genCol: Int, source: Option[SourceRef])

  private val Base64 =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
  private val Base64Index: Map[Char, Int] = Base64.zipWithIndex.toMap

  private def encodeVlq(n: Int): String =
    val sb      = new StringBuilder
    var encoded = if n < 0 then ((-n) << 1) | 1 else n << 1
    while
      var digit = encoded & 0x1f
      encoded >>>= 5
      if encoded > 0 then digit |= 0x20
      sb += Base64(digit)
      encoded > 0
    do ()
    sb.toString

  /** Decodes the `mappings` string into per-generated-line segment lists. */
  def decode(mappings: String): Vector[Vector[Segment]] =
    // These four accumulate across the WHOLE file (V3 spec); only genCol resets per line.
    var srcIndex = 0
    var srcLine  = 0
    var srcCol   = 0
    var nameIdx  = 0

    mappings.split(";", -1).toVector.map { line =>
      if line.isEmpty then Vector.empty[Segment]
      else
        var genCol = 0
        line.split(",", -1).toVector.collect {
          case seg if seg.nonEmpty =>
            val fields = readVlqs(seg)
            genCol += fields(0)
            if fields.length >= 4 then
              srcIndex += fields(1)
              srcLine += fields(2)
              srcCol += fields(3)
              val nameOpt =
                if fields.length >= 5 then
                  nameIdx += fields(4)
                  Some(nameIdx)
                else None
              Segment(genCol, Some(SourceRef(srcIndex, srcLine, srcCol, nameOpt)))
            else Segment(genCol, None)
        }
    }

  /** Encodes per-generated-line segment lists back into a `mappings` string. */
  def encode(lines: Vector[Vector[Segment]]): String =
    var srcIndex = 0
    var srcLine  = 0
    var srcCol   = 0
    var nameIdx  = 0
    val out      = new StringBuilder
    var lineNo   = 0
    while lineNo < lines.length do
      if lineNo > 0 then out += ';'
      var genCol = 0
      val segs   = lines(lineNo)
      var s      = 0
      while s < segs.length do
        if s > 0 then out += ','
        val seg = segs(s)
        out ++= encodeVlq(seg.genCol - genCol)
        genCol = seg.genCol
        seg.source.foreach { r =>
          out ++= encodeVlq(r.srcIndex - srcIndex); srcIndex = r.srcIndex
          out ++= encodeVlq(r.srcLine - srcLine); srcLine    = r.srcLine
          out ++= encodeVlq(r.srcCol - srcCol); srcCol       = r.srcCol
          r.nameIndex.foreach { n => out ++= encodeVlq(n - nameIdx); nameIdx = n }
        }
        s += 1
      lineNo += 1
    out.toString

  /** Reads all VLQ-encoded integers from one comma-free segment string. */
  private def readVlqs(seg: String): Vector[Int] =
    val result = Vector.newBuilder[Int]
    var i      = 0
    while i < seg.length do
      var shift = 0
      var value = 0
      var cont  = true
      while cont && i < seg.length do
        val d = Base64Index.getOrElse(seg(i), 0)
        i += 1
        cont = (d & 0x20) != 0
        value |= (d & 0x1f) << shift
        shift += 5
      result += (if (value & 1) != 0 then -(value >> 1) else value >> 1)
    result.result()
