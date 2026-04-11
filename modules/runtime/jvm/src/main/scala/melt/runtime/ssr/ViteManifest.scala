/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.ssr

import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path, Paths }

import scala.collection.immutable
import scala.io.Source

/** Reads a Vite-generated `.vite/manifest.json` and resolves chunks for
  * a given component `moduleID`.
  *
  * Vite's manifest keys are source-file paths. When using
  * `@scala-js/vite-plugin-scalajs`, each public Scala.js module appears
  * under a key prefixed with `scalajs:`, e.g. `scalajs:counter.js`.
  *
  * This class performs the same recursive dependency walk as SvelteKit's
  * `find_deps` helper: given a module id, look up the matching entry,
  * then recursively resolve its `imports` so that preload / script tags
  * can be emitted in the correct order (shared chunks first, owning
  * chunk last).
  *
  * == Thread safety ==
  *
  * `ViteManifest` is immutable and thread-safe. Load it once at
  * application startup via `ViteManifest.load(path)` and share the
  * instance across all requests (see `docs/meltc-ssr-design.md` §12.3.3).
  */
final class ViteManifest private (
  private val entries:   immutable.Map[String, ViteManifest.Entry],
  private val uriPrefix: String = "scalajs"
):

  /** Returns the recursive list of JS chunk files required to load the
    * given component `moduleID`, in dependency order (shared chunks
    * first, owning chunk last). Duplicates are removed.
    */
  def chunksFor(moduleId: String): List[String] =
    val key = s"$uriPrefix:$moduleId.js"
    resolve(key, Set.empty)

  /** Returns the recursive list of CSS files required for the given
    * component `moduleID`, in dependency order.
    */
  def cssFor(moduleId: String): List[String] =
    val key = s"$uriPrefix:$moduleId.js"
    resolveCss(key, Set.empty)

  /** Internal accessor for tests. */
  private[ssr] def lookup(key: String): Option[ViteManifest.Entry] =
    entries.get(key)

  private def resolve(key: String, visited: Set[String]): List[String] =
    if visited.contains(key) then Nil
    else
      entries.get(key) match
        case Some(entry) =>
          val next = visited + key
          val deps = entry.imports.flatMap(k => resolve(k, next))
          // Dedupe preserving first-seen order.
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

  /** One entry in the Vite manifest.
    *
    * Only the fields Melt needs are modelled; any extra keys present in
    * the JSON are ignored.
    */
  final case class Entry(
    file:    String,
    imports: List[String] = Nil,
    css:     List[String] = Nil,
    isEntry: Boolean      = false
  )

  /** An empty manifest, useful for SSR-only pages or tests. */
  val empty: ViteManifest = new ViteManifest(immutable.Map.empty)

  /** Builds a manifest from an in-memory `entries` map — primarily for
    * tests and custom bundlers.
    */
  def fromEntries(entries: Map[String, Entry], uriPrefix: String = "scalajs"): ViteManifest =
    new ViteManifest(entries.to(immutable.Map), uriPrefix)

  /** Loads the manifest from a filesystem path. Typically
    * `dist/.vite/manifest.json` after `vite build`.
    */
  def load(path: String, uriPrefix: String = "scalajs"): ViteManifest =
    loadFromPath(Paths.get(path), uriPrefix)

  /** Loads the manifest from an NIO `Path`. */
  def loadFromPath(path: Path, uriPrefix: String = "scalajs"): ViteManifest =
    if !Files.exists(path) then
      throw new FileNotFoundException(
        s"Vite manifest not found at '$path'. Did you run `vite build`?"
      )
    val json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
    fromString(json, uriPrefix)

  /** Parses a manifest from a JSON string. Used by [[loadFromPath]] and
    * by tests.
    *
    * The parser accepts only the specific subset of JSON that Vite
    * produces (top-level object of entry objects) and raises
    * [[IllegalArgumentException]] on anything unexpected.
    */
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
        val entries = fields.iterator.flatMap {
          case (key, JsonValue.Obj(entryFields)) =>
            Some(key -> parseEntry(entryFields))
          case (key, _) =>
            // Ignore non-object entries — Vite occasionally emits
            // alternate shapes that we don't model.
            None
        }.toMap
        new ViteManifest(entries.to(immutable.Map), uriPrefix)

      case other =>
        throw new IllegalArgumentException(
          s"Vite manifest must be a top-level JSON object, got ${ other.kind }"
        )

  private def parseEntry(fields: Map[String, JsonValue]): Entry =
    val file = fields.get("file") match
      case Some(JsonValue.Str(s)) => s
      case _                      => ""
    val imports = fields.get("imports") match
      case Some(JsonValue.Arr(items)) =>
        items.collect { case JsonValue.Str(s) => s }
      case _ => Nil
    val css = fields.get("css") match
      case Some(JsonValue.Arr(items)) =>
        items.collect { case JsonValue.Str(s) => s }
      case _ => Nil
    val isEntry = fields.get("isEntry") match
      case Some(JsonValue.Bool(b)) => b
      case _                       => false
    Entry(file, imports, css, isEntry)

  // ── Minimal JSON parser ────────────────────────────────────────────────
  //
  // Vite manifests are small (tens of KB at most) and structurally
  // constrained, so we hand-roll a parser that covers the exact shape
  // we need. This avoids adding a JSON library dependency to
  // melt-runtime.jvm, which the design doc (§2.3) wants to stay
  // external-dependency-free.

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
      while pos < src.length && isWs(src.charAt(pos)) do pos += 1

    private def isWs(c: Char): Boolean =
      c == ' ' || c == '\t' || c == '\n' || c == '\r'

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
          pos += 1
          skipWs()
          parsePair(fields)
          skipWs()
      expect('}')
      JsonValue.Obj(fields.toMap)

    private def parsePair(
      fields: scala.collection.mutable.LinkedHashMap[String, JsonValue]
    ): Unit =
      skipWs()
      val key = parseString()
      skipWs()
      expect(':')
      val value = parseValue()
      fields(key) = value

    private def parseArray(): JsonValue.Arr =
      expect('[')
      skipWs()
      val items = scala.collection.mutable.ListBuffer.empty[JsonValue]
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
