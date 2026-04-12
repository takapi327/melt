/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.json

import scala.collection.mutable

/** Minimal zero-dependency JSON parser / encoder used by the Melt runtime.
  *
  * Props serialisation (§12.3.11) needs to round-trip a component's Props
  * case class between the SSR side (JVM) and the hydration entry (JS).
  * Both sides live in `melt-runtime`, which the design doc (§2.3) wants
  * to stay external-dependency-free, so we hand-roll a small parser that
  * covers the subset of JSON that [[PropsCodec]] actually emits:
  *
  *   - objects `{ "k": v, ... }`
  *   - arrays `[ v, ... ]`
  *   - strings (with standard `\` escapes and `\uXXXX`)
  *   - numbers (integer and decimal with optional exponent)
  *   - booleans / null
  *
  * This is the same hand-rolled parser that `ViteManifest` used to carry
  * privately, lifted into the shared source tree so both the JVM SSR
  * path and the JS hydration path can reach it without adding a circe /
  * upickle dependency.
  */
object SimpleJson:

  // ── AST ────────────────────────────────────────────────────────────────

  /** Discriminated union of JSON values. Kept deliberately small — the
    * shapes we care about are Obj, Arr, Str, Num, Bool, Null.
    */
  sealed trait JsonValue:
    def kind: String

  object JsonValue:
    final case class Obj(fields: Map[String, JsonValue]) extends JsonValue { def kind = "object" }
    final case class Arr(items: List[JsonValue])         extends JsonValue { def kind = "array"  }
    final case class Str(value: String)                  extends JsonValue { def kind = "string" }
    final case class Num(value: Double)                  extends JsonValue { def kind = "number" }
    final case class Bool(value: Boolean)                extends JsonValue { def kind = "bool"   }
    case object Null                                     extends JsonValue { def kind = "null"   }

    extension (obj: Obj)
      /** Looks up a field and returns its string value if present and of
        * the right shape, otherwise `None`.
        */
      def getString(key: String): Option[String] =
        obj.fields.get(key) match
          case Some(Str(s)) => Some(s)
          case _            => None

      def getDouble(key: String): Option[Double] =
        obj.fields.get(key) match
          case Some(Num(n)) => Some(n)
          case _            => None

      def getInt(key:   String): Option[Int]   = getDouble(key).map(_.toInt)
      def getLong(key:  String): Option[Long]  = getDouble(key).map(_.toLong)
      def getFloat(key: String): Option[Float] = getDouble(key).map(_.toFloat)

      def getBool(key: String): Option[Boolean] =
        obj.fields.get(key) match
          case Some(Bool(b)) => Some(b)
          case _             => None

      def getArr(key: String): Option[List[JsonValue]] =
        obj.fields.get(key) match
          case Some(Arr(items)) => Some(items)
          case _                => None

      def getObj(key: String): Option[Obj] =
        obj.fields.get(key) match
          case Some(o: Obj) => Some(o)
          case _            => None

      /** Returns `None` when the field is absent or explicitly `null`,
        * otherwise applies `f` to the underlying value.
        */
      def getOpt[A](key: String)(f: JsonValue => A): Option[A] =
        obj.fields.get(key) match
          case None | Some(Null) => None
          case Some(v)           => Some(f(v))

  // ── Parse ──────────────────────────────────────────────────────────────

  /** Parses a JSON document. Throws [[IllegalArgumentException]] on
    * syntactically invalid input — callers that accept untrusted input
    * should wrap this in `Try`.
    */
  def parse(src: String): JsonValue =
    val p = new Parser(src)
    val v = p.parseValue()
    p.skipWs()
    if p.pos != src.length then throw new IllegalArgumentException(s"unexpected trailing input at offset ${ p.pos }")
    v

  // ── Encode ─────────────────────────────────────────────────────────────

  /** Escapes a string for inclusion in a JSON string literal.
    *
    * In addition to the standard JSON escapes, also escapes the `</`
    * sequence so that the resulting JSON can be safely embedded inside
    * a `<script type="application/json">` tag without risk of early
    * termination by an HTML parser. The escape uses `<\/`, which is
    * still valid JSON per RFC 8259 §7 (forward slash MAY be escaped).
    */
  def encString(s: String): String =
    val buf = new StringBuilder(s.length + 2)
    buf += '"'
    var i = 0
    while i < s.length do
      val c = s.charAt(i)
      c match
        case '"'                                    => buf ++= "\\\""
        case '\\'                                   => buf ++= "\\\\"
        case '\b'                                   => buf ++= "\\b"
        case '\f'                                   => buf ++= "\\f"
        case '\n'                                   => buf ++= "\\n"
        case '\r'                                   => buf ++= "\\r"
        case '\t'                                   => buf ++= "\\t"
        case '/' if i > 0 && s.charAt(i - 1) == '<' =>
          // Break the `</` sequence so an HTML parser can't terminate
          // the enclosing <script type="application/json"> block.
          buf ++= "\\/"
        case c if c < 0x20 =>
          buf ++= "\\u%04x".format(c.toInt)
        case c =>
          buf += c
      i += 1
    buf += '"'
    buf.toString

  /** Encodes a number avoiding Java's `1.0` suffix for integral values.
    * Doubles that are NaN or infinite are rejected because JSON has no
    * representation for them.
    */
  def encNumber(d: Double): String =
    if d.isNaN || d.isInfinite then throw new IllegalArgumentException(s"cannot encode non-finite number: $d")
    else if d == d.toLong.toDouble && !d.toString.contains("E") then d.toLong.toString
    else d.toString

  // ── Parser implementation ──────────────────────────────────────────────

  private final class Parser(src: String):
    var pos: Int = 0

    def skipWs(): Unit =
      while pos < src.length && isWs(src.charAt(pos)) do pos += 1

    private def isWs(c: Char): Boolean =
      c == ' ' || c == '\t' || c == '\n' || c == '\r'

    def parseValue(): JsonValue =
      skipWs()
      if pos >= src.length then fail("unexpected end of input")
      src.charAt(pos) match
        case '{'                                     => parseObject()
        case '['                                     => parseArray()
        case '"'                                     => JsonValue.Str(parseString())
        case 't' | 'f'                               => JsonValue.Bool(parseBool())
        case 'n'                                     => parseNull()
        case c if c == '-' || (c >= '0' && c <= '9') => parseNumber()
        case c                                       => fail(s"unexpected '$c'")

    private def parseObject(): JsonValue.Obj =
      expect('{')
      skipWs()
      val fields = mutable.LinkedHashMap.empty[String, JsonValue]
      if !peekChar('}') then
        parsePair(fields)
        skipWs()
        while peekChar(',') do
          pos += 1
          skipWs()
          parsePair(fields)
          skipWs()
      expect('}')
      JsonValue.Obj(fields.toMap)

    private def parsePair(fields: mutable.LinkedHashMap[String, JsonValue]): Unit =
      skipWs()
      val key = parseString()
      skipWs()
      expect(':')
      val value = parseValue()
      fields(key) = value

    private def parseArray(): JsonValue.Arr =
      expect('[')
      skipWs()
      val items = mutable.ListBuffer.empty[JsonValue]
      if !peekChar(']') then
        items += parseValue()
        skipWs()
        while peekChar(',') do
          pos += 1
          items += parseValue()
          skipWs()
      expect(']')
      JsonValue.Arr(items.toList)

    private def parseString(): String =
      expect('"')
      val buf  = new StringBuilder
      var done = false
      while !done do
        if pos >= src.length then fail("unterminated string")
        val c = src.charAt(pos)
        pos += 1
        c match
          case '"'  => done = true
          case '\\' =>
            if pos >= src.length then fail("unterminated escape")
            val esc = src.charAt(pos)
            pos += 1
            esc match
              case '"'  => buf += '"'
              case '\\' => buf += '\\'
              case '/'  => buf += '/'
              case 'b'  => buf += '\b'
              case 'f'  => buf += '\f'
              case 'n'  => buf += '\n'
              case 'r'  => buf += '\r'
              case 't'  => buf += '\t'
              case 'u'  =>
                if pos + 4 > src.length then fail("truncated unicode escape")
                val hex = src.substring(pos, pos + 4)
                pos += 4
                buf += Integer.parseInt(hex, 16).toChar
              case other => fail(s"invalid escape '\\$other'")
          case _ => buf += c
      buf.toString

    private def parseBool(): Boolean =
      if src.startsWith("true", pos) then
        pos += 4
        true
      else if src.startsWith("false", pos) then
        pos += 5
        false
      else fail("expected true/false")

    private def parseNull(): JsonValue =
      if src.startsWith("null", pos) then
        pos += 4
        JsonValue.Null
      else fail("expected null")

    private def parseNumber(): JsonValue.Num =
      val start = pos
      if pos < src.length && src.charAt(pos) == '-' then pos += 1
      while pos < src.length && {
          val c = src.charAt(pos)
          (c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-'
        }
      do pos += 1
      JsonValue.Num(src.substring(start, pos).toDouble)

    private def peekChar(c: Char): Boolean =
      skipWs()
      pos < src.length && src.charAt(pos) == c

    private def expect(c: Char): Unit =
      skipWs()
      if pos >= src.length || src.charAt(pos) != c then fail(s"expected '$c'")
      pos += 1

    private def fail(msg: String): Nothing =
      throw new IllegalArgumentException(s"$msg at offset $pos")
