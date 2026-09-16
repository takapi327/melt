/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.json

/** Minimal zero-dependency JSON parser / encoder used by the Melt runtime.
  *
  * Round-trips a component's Props case class between the SSR side (JVM)
  * and the hydration entry (JS). Both sides live in `melt-runtime` which
  * stays external-dependency-free, so we hand-roll a small parser that
  * covers the subset of JSON that [[PropsCodec]] actually emits:
  *
  *   - objects `{ "k": v, ... }`
  *   - arrays `[ v, ... ]`
  *   - strings (with standard `\` escapes and `\uXXXX`)
  *   - numbers (integer and decimal with optional exponent)
  *   - booleans / null
  */
object SimpleJson:

  /** Discriminated union of JSON values. */
  sealed trait JsonValue:
    def kind: String

  /** Insertion-ordered field container for [[JsonValue.Obj]].
    *
    * Deliberately **not** a `scala.collection.Map`. Linking any Scala map
    * implementation — `immutable.Map`, `mutable.HashMap`, `mutable.LinkedHashMap`
    * alike — makes the immutable collection hierarchy reachable in a Scala.js
    * bundle, and from there:
    *
    * {{{
    * immutable.HashMap → HashCollisionMapNode's `Predef.assert`
    *   → Predef's module init assigns `immutable.Set`
    *   → Set4.incl escalates to immutable.HashSet
    *   → BitmapIndexedSetNode.toString uses String.format
    *   → java.util.Formatter (+ every IllegalFormat*Exception)
    * }}}
    *
    * Measured cost of that chain: **~41 KB gzip**. See `memo/design-bundle-size.md` §2.6.
    * `mutable.AnyRefMap` avoids it but is deprecated, so it fails the build's `-Werror`.
    *
    * Two parallel arrays with a linear scan, plus a hash index once an object grows
    * past [[JsonFields.indexThreshold]] fields. Insertion order is preserved, so a
    * parsed document keeps its field order.
    */
  final class JsonFields private[json] (initialCapacity: Int):
    private var ks:    Array[String]    = new Array[String](initialCapacity)
    private var vs:    Array[JsonValue] = new Array[JsonValue](initialCapacity)
    private var count: Int              = 0

    /** Open-addressing index over [[ks]], built lazily once an object grows past
      * [[JsonFields.indexThreshold]] fields. Each slot holds `position + 1`, so `0`
      * means empty. Plain `Array[Int]` keeps this free of Scala collections.
      *
      * Without it, building an n-field object is O(n²) because every insert scans
      * for a duplicate key. Measured on Scala.js: 1,000 fields 3 ms, 5,000 fields
      * 72 ms. Hydration payloads are far smaller, but a `Map`-valued prop is not
      * bounded, so the index keeps the worst case linear.
      */
    private var idx:  Array[Int] = null
    private var mask: Int        = 0

    private def slotFor(key: String): Int =
      // Scramble: String.hashCode has weak low bits, which open addressing relies on.
      val h0 = key.hashCode
      val h  = h0 ^ (h0 >>> 16)
      var i  = h & mask
      var r  = -1
      var go = true
      while go do
        val slot = idx(i)
        if slot == 0 then
          r = ~i; go = false
        else if ks(slot - 1) == key then
          r = slot - 1; go = false
        else i = (i + 1) & mask
      r

    private def rebuildIndex(): Unit =
      var cap = 8
      while cap < count * 2 do cap <<= 1
      idx  = new Array[Int](cap)
      mask = cap - 1
      var i = 0
      while i < count do
        val s = slotFor(ks(i))
        // Freshly built from distinct keys, so every probe lands on a free slot.
        if s < 0 then idx(~s) = i + 1
        i += 1

    private def indexOf(key: String): Int =
      if idx ne null then
        val s = slotFor(key)
        if s < 0 then -1 else s
      else
        var i = 0
        var r = -1
        while r < 0 && i < count do
          if ks(i) == key then r = i
          i += 1
        r

    /** Appends a field, or replaces the value of an existing key in place. */
    private[json] def put(key: String, value: JsonValue): Unit =
      val i = indexOf(key)
      if i >= 0 then vs(i) = value
      else
        if count == ks.length then
          val cap    = if ks.length == 0 then 4 else ks.length * 2
          val nextKs = new Array[String](cap)
          val nextVs = new Array[JsonValue](cap)
          Array.copy(ks, 0, nextKs, 0, count)
          Array.copy(vs, 0, nextVs, 0, count)
          ks = nextKs
          vs = nextVs
        ks(count) = key
        vs(count) = value
        count += 1
        if idx eq null then
          if count >= JsonFields.indexThreshold then rebuildIndex()
        else if count * 2 > mask then rebuildIndex()
        else
          val s = slotFor(key)
          if s < 0 then idx(~s) = count

    def size:    Int     = count
    def isEmpty: Boolean = count == 0

    def contains(key: String): Boolean = indexOf(key) >= 0

    def get(key: String): Option[JsonValue] =
      val i = indexOf(key)
      if i < 0 then None else Some(vs(i))

    def getOrElse(key: String, default: => JsonValue): JsonValue =
      val i = indexOf(key)
      if i < 0 then default else vs(i)

    /** @throws NoSuchElementException when the field is absent. */
    def apply(key: String): JsonValue =
      val i = indexOf(key)
      if i < 0 then throw new NoSuchElementException(s"JSON object has no field '$key'")
      else vs(i)

    /** Visits every field in insertion order. */
    def foreach(f: (String, JsonValue) => Unit): Unit =
      var i = 0
      while i < count do
        f(ks(i), vs(i))
        i += 1

    /** Field names in insertion order. */
    def keys: List[String] =
      var acc = List.empty[String]
      var i   = count - 1
      while i >= 0 do
        acc = ks(i) :: acc
        i -= 1
      acc

    override def equals(that: Any): Boolean = that match
      case o: JsonFields if o.size == count =>
        var i  = 0
        var eq = true
        while eq && i < count do
          eq = o.get(ks(i)).contains(vs(i))
          i += 1
        eq
      case _ => false

    override def hashCode(): Int =
      var h = 0
      var i = 0
      while i < count do
        h += ks(i).hashCode ^ vs(i).hashCode
        i += 1
      h

    override def toString: String =
      val b = new StringBuilder("JsonFields(")
      var i = 0
      while i < count do
        if i > 0 then b ++= ", "
        b ++= ks(i)
        b ++= " -> "
        b ++= vs(i).toString
        i += 1
      b += ')'
      b.toString

  object JsonFields:
    /** Field count at which a hash index replaces the linear scan. Below it the scan
      * is cheaper than the index it would have to build; JSON objects in hydration
      * payloads sit well under this. */
    private[json] val indexThreshold = 16

    def empty: JsonFields = new JsonFields(4)

    /** Builds from key/value pairs, for tests and hand-written payloads. */
    def apply(pairs: (String, JsonValue)*): JsonFields =
      val f = new JsonFields(if pairs.isEmpty then 4 else pairs.size)
      pairs.foreach((k, v) => f.put(k, v))
      f

  object JsonValue:
    /** A JSON object. See [[JsonFields]] for why `fields` is not a Scala `Map`. */
    final case class Obj(fields: JsonFields) extends JsonValue:
      def kind = "object"
    final case class Arr(items: List[JsonValue]) extends JsonValue:
      def kind = "array"
    final case class Str(value: String) extends JsonValue:
      def kind = "string"
    final case class Num(value: Double) extends JsonValue:
      def kind = "number"
    final case class Bool(value: Boolean) extends JsonValue:
      def kind = "bool"
    case object Null extends JsonValue:
      def kind = "null"

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

  /** Escapes a string for inclusion in a JSON string literal.
    *
    * In addition to the standard JSON escapes, also escapes the `</`
    * sequence so that the resulting JSON can be safely embedded inside
    * a `<script type="application/json">` tag without risk of early
    * termination by an HTML parser. The escape uses `<\/`, which is
    * still valid JSON per RFC 8259 (forward slash MAY be escaped).
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
      val fields = JsonFields.empty
      if !peekChar('}') then
        parsePair(fields)
        skipWs()
        while peekChar(',') do
          pos += 1
          skipWs()
          parsePair(fields)
          skipWs()
      expect('}')
      JsonValue.Obj(fields)

    private def parsePair(fields: JsonFields): Unit =
      skipWs()
      val key = parseString()
      skipWs()
      expect(':')
      val value = parseValue()
      fields.put(key, value)

    private def parseArray(): JsonValue.Arr =
      expect('[')
      skipWs()
      // Accumulate reversed and flip once: `List` links cheaply, whereas
      // `mutable.ListBuffer` reaches the collection hierarchy that this file
      // exists to keep out of the bundle (see JsonFields).
      var items: List[JsonValue] = Nil
      if !peekChar(']') then
        items = parseValue() :: items
        skipWs()
        while peekChar(',') do
          pos += 1
          items = parseValue() :: items
          skipWs()
      expect(']')
      JsonValue.Arr(items.reverse)

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
