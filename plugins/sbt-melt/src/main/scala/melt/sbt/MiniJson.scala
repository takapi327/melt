/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.sbt

/** A minimal JSON reader/writer sufficient for rewriting Source Maps V3 files
  * (objects, arrays, strings, numbers, booleans, null). Object key order is
  * preserved on both parse and render so a composed map stays diff-friendly
  * against the linker's original.
  */
object MiniJson:

  sealed trait JValue
  final case class JObj(fields: Vector[(String, JValue)]) extends JValue:
    def get(key: String):   Option[JValue] = fields.find(_._1 == key).map(_._2)
    def field(key: String): JValue         =
      get(key).getOrElse(throw new NoSuchElementException(s"missing JSON key: $key"))
    def withField(key: String, value: JValue): JObj =
      if fields.exists(_._1 == key) then JObj(fields.map { case (k, v) => if k == key then (k, value) else (k, v) })
      else JObj(fields :+ (key -> value))
  final case class JArr(elems: Vector[JValue]) extends JValue
  final case class JStr(value: String)         extends JValue
  final case class JNum(raw: String)           extends JValue
  final case class JBool(value: Boolean)       extends JValue
  case object JNull                            extends JValue

  extension (v: JValue)
    def asObject: JObj           = v.asInstanceOf[JObj]
    def asArray:  Vector[JValue] = v.asInstanceOf[JArr].elems
    def asString: String         = v.asInstanceOf[JStr].value

  def parse(input: String): JValue =
    val p = new Parser(input)
    p.skipWs()
    val v = p.parseValue()
    p.skipWs()
    if !p.atEnd then throw new IllegalArgumentException(s"trailing JSON at index ${ p.pos }")
    v

  private final class Parser(s: String):
    var pos = 0
    def atEnd:    Boolean = pos >= s.length
    def skipWs(): Unit    =
      while pos < s.length && (s(pos) == ' ' || s(pos) == '\n' || s(pos) == '\r' || s(pos) == '\t') do pos += 1

    def parseValue(): JValue =
      skipWs()
      s(pos) match
        case '{' => parseObject()
        case '[' => parseArray()
        case '"' => JStr(parseString())
        case 't' => expect("true"); JBool(true)
        case 'f' => expect("false"); JBool(false)
        case 'n' => expect("null"); JNull
        case _   => parseNumber()

    private def expect(word: String): Unit =
      if !s.regionMatches(pos, word, 0, word.length) then
        throw new IllegalArgumentException(s"expected '$word' at index $pos")
      pos += word.length

    private def parseObject(): JObj =
      pos += 1 // {
      val fields = Vector.newBuilder[(String, JValue)]
      skipWs()
      if s(pos) == '}' then pos += 1
      else
        var more = true
        while more do
          skipWs()
          val key = parseString()
          skipWs()
          if s(pos) != ':' then throw new IllegalArgumentException(s"expected ':' at index $pos")
          pos += 1
          val value = parseValue()
          fields += (key -> value)
          skipWs()
          s(pos) match
            case ',' => pos += 1
            case '}' => pos += 1; more = false
            case c   => throw new IllegalArgumentException(s"expected ',' or '}' but got '$c' at index $pos")
      JObj(fields.result())

    private def parseArray(): JArr =
      pos += 1 // [
      val elems = Vector.newBuilder[JValue]
      skipWs()
      if s(pos) == ']' then pos += 1
      else
        var more = true
        while more do
          elems += parseValue()
          skipWs()
          s(pos) match
            case ',' => pos += 1
            case ']' => pos += 1; more = false
            case c   => throw new IllegalArgumentException(s"expected ',' or ']' but got '$c' at index $pos")
      JArr(elems.result())

    private def parseString(): String =
      if s(pos) != '"' then throw new IllegalArgumentException(s"expected '\"' at index $pos")
      pos += 1
      val sb = new StringBuilder
      while s(pos) != '"' do
        val c = s(pos)
        if c == '\\' then
          pos += 1
          s(pos) match
            case '"'  => sb += '"'
            case '\\' => sb += '\\'
            case '/'  => sb += '/'
            case 'b'  => sb += '\b'
            case 'f'  => sb += '\f'
            case 'n'  => sb += '\n'
            case 'r'  => sb += '\r'
            case 't'  => sb += '\t'
            case 'u'  =>
              val hex = s.substring(pos + 1, pos + 5)
              sb += Integer.parseInt(hex, 16).toChar
              pos += 4
            case other => throw new IllegalArgumentException(s"invalid escape '\\$other' at index $pos")
          pos += 1
        else
          sb += c
          pos += 1
      pos += 1 // closing "
      sb.toString

    private def parseNumber(): JNum =
      val start = pos
      while pos < s.length && {
          val c = s(pos)
          (c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E'
        }
      do pos += 1
      JNum(s.substring(start, pos))

  def render(v: JValue): String =
    val sb = new StringBuilder
    renderTo(sb, v)
    sb.toString

  private def renderTo(sb: StringBuilder, v: JValue): Unit =
    v match
      case JObj(fields) =>
        sb += '{'
        var first = true
        fields.foreach {
          case (k, value) =>
            if !first then sb += ','
            first = false
            renderString(sb, k)
            sb += ':'
            renderTo(sb, value)
        }
        sb += '}'
      case JArr(elems) =>
        sb += '['
        var first = true
        elems.foreach { e =>
          if !first then sb += ','
          first = false
          renderTo(sb, e)
        }
        sb += ']'
      case JStr(value)  => renderString(sb, value)
      case JNum(raw)    => sb ++= raw
      case JBool(value) => sb ++= (if value then "true" else "false")
      case JNull        => sb ++= "null"

  private def renderString(sb: StringBuilder, s: String): Unit =
    sb += '"'
    var i = 0
    while i < s.length do
      val c = s(i)
      c match
        case '"'  => sb ++= "\\\""
        case '\\' => sb ++= "\\\\"
        case '\b' => sb ++= "\\b"
        case '\f' => sb ++= "\\f"
        case '\n' => sb ++= "\\n"
        case '\r' => sb ++= "\\r"
        case '\t' => sb ++= "\\t"
        case _    =>
          if c < 0x20 then sb ++= f"\\u${ c.toInt }%04x"
          else sb += c
      i += 1
    sb += '"'
