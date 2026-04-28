/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.collection.immutable

/** Reads a Vite-generated `.vite/manifest.json` and resolves chunks for
  * a given component `moduleID`.
  *
  * Vite's manifest keys are source-file paths. When using
  * `@scala-js/vite-plugin-scalajs`, each public Scala.js module appears
  * under a key prefixed with `scalajs:`, e.g. `scalajs:counter.js`.
  *
  * `ViteManifest` is immutable and thread-safe. Load it once at
  * application startup and share the instance across all requests.
  */
final class ViteManifest private[meltkit] (
  private val entries:   immutable.Map[String, ViteManifest.Entry],
  private val uriPrefix: String = "scalajs"
):

  /** Returns `<script type="module" src="...">` tags for all JS modules
    * in this manifest, suitable for injection into an SPA HTML shell via
    * [[Template.render]].
    */
  def scriptTags(basePath: String = "/assets"): String =
    val base = basePath.stripSuffix("/")
    entries.toList
      .sortBy(_._1)
      .map { case (_, entry) => s"""<script type="module" src="$base/${ entry.file }"></script>""" }
      .mkString("\n")

  /** Returns the recursive list of JS chunk files required to load the
    * given component `moduleID`, in dependency order (shared chunks first,
    * owning chunk last).
    */
  def chunksFor(moduleId: String): List[String] =
    resolve(s"$uriPrefix:$moduleId.js", Set.empty)

  /** Returns the recursive list of CSS files required for the given
    * component `moduleID`, in dependency order.
    */
  def cssFor(moduleId: String): List[String] =
    resolveCss(s"$uriPrefix:$moduleId.js", Set.empty)

  private def resolve(key: String, visited: Set[String]): List[String] =
    if visited.contains(key) then Nil
    else
      entries.get(key) match
        case Some(entry) =>
          val next = visited + key
          val deps = entry.imports.flatMap(k => resolve(k, next))
          (deps :+ entry.file).distinct
        case None => Nil

  private def resolveCss(key: String, visited: Set[String]): List[String] =
    if visited.contains(key) then Nil
    else
      entries.get(key) match
        case Some(entry) =>
          val next = visited + key
          val deps = entry.imports.flatMap(k => resolveCss(k, next))
          (deps ++ entry.css).distinct
        case None => Nil

object ViteManifest:

  /** One entry in the Vite manifest. */
  final case class Entry(
    file:    String,
    imports: List[String] = Nil,
    css:     List[String] = Nil,
    isEntry: Boolean      = false
  )

  /** An empty manifest, useful for tests or API-only servers. */
  val empty: ViteManifest = new ViteManifest(immutable.Map.empty)

  /** Builds a manifest from an in-memory entries map — primarily for tests. */
  def fromEntries(entries: Map[String, Entry], uriPrefix: String = "scalajs"): ViteManifest =
    new ViteManifest(entries.to(immutable.Map), uriPrefix)

  /** Parses a manifest from a Vite-generated JSON string. */
  def fromString(json: String, uriPrefix: String = "scalajs"): ViteManifest =
    val parser = new JsonParser(json)
    val topVal = parser.parseValue()
    parser.skipWs()
    if parser.pos < json.length then
      throw new IllegalArgumentException(
        s"Unexpected trailing content in manifest JSON at offset ${ parser.pos }"
      )
    topVal match
      case JsonValue.Obj(fields) =>
        val rawEntries = fields.iterator.flatMap {
          case (key, JsonValue.Obj(entryFields)) => Some(key -> parseEntry(entryFields))
          case _                                 => None
        }.toMap
        val aliases = rawEntries.iterator.flatMap {
          case (key, entry) if entry.isEntry && !key.startsWith(s"$uriPrefix:") =>
            val basename = key.split('/').last
            val withExt  = if basename.contains('.') then basename else s"$basename.js"
            val aliasKey = s"$uriPrefix:$withExt"
            if rawEntries.contains(aliasKey) then None else Some(aliasKey -> entry)
          case _ => None
        }.toMap
        new ViteManifest((rawEntries ++ aliases).to(immutable.Map), uriPrefix)
      case other =>
        throw new IllegalArgumentException(
          s"Vite manifest must be a top-level JSON object, got ${ other.kind }"
        )

  private def parseEntry(fields: Map[String, JsonValue]): Entry =
    val file    = fields.get("file") collect { case JsonValue.Str(s) => s } getOrElse ""
    val imports = fields.get("imports") collect {
      case JsonValue.Arr(items) => items.collect { case JsonValue.Str(s) => s }
    } getOrElse Nil
    val css = fields.get("css") collect {
      case JsonValue.Arr(items) => items.collect { case JsonValue.Str(s) => s }
    } getOrElse Nil
    val isEntry = fields.get("isEntry") collect { case JsonValue.Bool(b) => b } getOrElse false
    Entry(file, imports, css, isEntry)

  private sealed trait JsonValue:
    def kind: String
  private object JsonValue:
    final case class Obj(fields: Map[String, JsonValue]) extends JsonValue { def kind = "object" }
    final case class Arr(items: List[JsonValue])         extends JsonValue { def kind = "array"  }
    final case class Str(value: String)                  extends JsonValue { def kind = "string" }
    final case class Num(value: Double)                  extends JsonValue { def kind = "number" }
    final case class Bool(value: Boolean)                extends JsonValue { def kind = "bool"   }
    case object Null                                     extends JsonValue { def kind = "null"   }

  private final class JsonParser(src: String):
    var pos: Int = 0

    def skipWs(): Unit =
      while pos < src.length && { val c = src.charAt(pos); c == ' ' || c == '\t' || c == '\n' || c == '\r' } do pos += 1

    def parseValue(): JsonValue =
      skipWs()
      if pos >= src.length then throw new IllegalArgumentException("unexpected end of input")
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
      val fields = scala.collection.mutable.LinkedHashMap.empty[String, JsonValue]
      if !peekChar('}') then
        parsePair(fields)
        skipWs()
        while peekChar(',') do
          pos += 1; skipWs(); parsePair(fields); skipWs()
      expect('}')
      JsonValue.Obj(fields.toMap)

    private def parsePair(fields: scala.collection.mutable.LinkedHashMap[String, JsonValue]): Unit =
      skipWs()
      val key = parseString()
      skipWs(); expect(':')
      fields(key) = parseValue()

    private def parseArray(): JsonValue.Arr =
      expect('[')
      skipWs()
      val items = scala.collection.mutable.ListBuffer.empty[JsonValue]
      if !peekChar(']') then
        items += parseValue(); skipWs()
        while peekChar(',') do pos += 1; items += parseValue(); skipWs()
      expect(']')
      JsonValue.Arr(items.toList)

    private def parseString(): String =
      expect('"')
      val buf  = new StringBuilder
      var done = false
      while !done do
        if pos >= src.length then fail("unterminated string")
        val c = src.charAt(pos); pos += 1
        c match
          case '"'  => done = true
          case '\\' =>
            if pos >= src.length then fail("unterminated escape")
            val esc = src.charAt(pos); pos += 1
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
                buf += Integer.parseInt(src.substring(pos, pos + 4), 16).toChar; pos += 4
              case other => fail(s"invalid escape '\\$other'")
          case _ => buf += c
      buf.toString

    private def parseBool(): Boolean =
      if src.startsWith("true", pos) then { pos += 4; true }
      else if src.startsWith("false", pos) then { pos += 5; false }
      else fail("expected true/false")

    private def parseNull(): JsonValue =
      if src.startsWith("null", pos) then { pos += 4; JsonValue.Null }
      else fail("expected null")

    private def parseNumber(): JsonValue.Num =
      val start = pos
      if pos < src.length && src.charAt(pos) == '-' then pos += 1
      while pos < src.length && {
          val c = src.charAt(pos); (c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-'
        }
      do pos += 1
      JsonValue.Num(src.substring(start, pos).toDouble)

    private def peekChar(c: Char): Boolean = { skipWs(); pos < src.length && src.charAt(pos) == c }
    private def expect(c: Char):   Unit    = {
      skipWs(); if pos >= src.length || src.charAt(pos) != c then fail(s"expected '$c'"); pos += 1
    }
    private def fail(msg: String): Nothing = throw new IllegalArgumentException(s"$msg at offset $pos")
