/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.ssr

import scala.collection.mutable

import melt.runtime.{ Escape, MeltWarnings, UrlAttributes, AttrNameValidator }

/** SSR rendering engine used by `meltc`-generated code.
  *
  * @note '''Internal API.''' This class is part of the protocol between
  *       `meltc`-generated components and the melt runtime. Do not
  *       instantiate or call its methods from hand-written user code.
  *
  *       User code should only interact with:
  *         - `Component.render(props): RenderResult` — the entry point
  *           exposed by each generated component
  *         - `Layout.document(result, ...)` — assembles a full HTML document
  *         - `TrustedHtml` / `Escape.*` — explicit opt-in escalation paths
  *
  *       Methods on `SsrRenderer` may change without notice between
  *       versions.
  *
  * @note Not thread-safe. `meltc`-generated code creates a fresh instance
  *       per `render()` call; never share an instance across requests.
  */
final class SsrRenderer(val config: SsrRenderer.Config = SsrRenderer.Config.default):

  private val bodyBuf        = new StringBuilder
  private val headBuf        = new StringBuilder
  private val cssSet         = mutable.LinkedHashSet.empty[CssEntry]
  private val usedComponents = mutable.LinkedHashSet.empty[String]

  private var componentDepth: Int  = 0
  private var outputBytes:    Long = 0L

  /** Appends `html` to the `<body>` buffer and checks the output-size limit. */
  def push(html: String): Unit =
    trackSize(html)
    bodyBuf ++= html

  /** Head-targeted helpers. Exposed as a nested object so generated code can
    * write `renderer.head.push(...)` to disambiguate from the body buffer.
    */
  object head:
    def push(html: String): Unit =
      trackSize(html)
      headBuf ++= html

    /** Escapes and emits a `<title>` into the head buffer. Used by the
      * `<melt:head>` visitor, which allows `{expr}` inside `<title>`.
      */
    def title(content: Any): Unit =
      val escaped = s"<title>${ Escape.html(content) }</title>"
      trackSize(escaped)
      headBuf ++= escaped

  /** CSS registration. Deduplicates by `CssEntry` equality (scopeId + code).
    *
    * `scopeId` is always a compile-time string literal emitted by
    * `SsrCodeGen` — this contract is enforced on the compiler side and not
    * re-checked here (see `§12.3.12`).
    */
  object css:
    def add(scopeId: String, code: String): Unit =
      val entry = CssEntry(scopeId, code)
      if !cssSet.contains(entry) then
        trackSize(scopeId)
        trackSize(code)
        cssSet += entry

  /** Records a used component `moduleID` for future Hydration chunk
    * resolution.
    */
  def trackComponent(name: String): Unit =
    usedComponents += name

  /** Merges a child component's [[RenderResult]] into this renderer's state. */
  def merge(child: RenderResult): Unit =
    trackSize(child.body)
    trackSize(child.head)
    bodyBuf ++= child.body
    headBuf ++= child.head
    child.css.foreach { entry =>
      if !cssSet.contains(entry) then
        trackSize(entry.scopeId)
        trackSize(entry.code)
        cssSet += entry
    }
    usedComponents ++= child.components

  /** Increments the component nesting counter and throws
    * [[MeltRenderException]] if the configured limit is exceeded.
    */
  def enterComponent(name: String): Unit =
    componentDepth += 1
    if componentDepth > config.maxComponentDepth then
      throw new MeltRenderException(
        s"Component nesting depth exceeded ${ config.maxComponentDepth } at '$name'. " +
          "This usually indicates infinite recursion in component references. " +
          "If intentional, increase the limit via MELT_MAX_COMPONENT_DEPTH env var."
      )

  /** Decrements the component nesting counter. */
  def exitComponent(): Unit = componentDepth -= 1

  /** Emits a spread attribute `Map` to the body buffer, applying all Phase A
    * security rules:
    *
    *   - Drop keys whose name fails [[AttrNameValidator]]
    *   - Drop keys matching `on*` (event handlers must never be inlined in SSR)
    *   - Drop `null` / `None` values
    *   - Escape URL-typed attributes via [[Escape.url]]
    *   - Escape other values via [[Escape.attr]]
    */
  def spreadAttrs(tag: String, attrs: Map[String, Any]): Unit =
    attrs.foreach { case (name, value) =>
      if !AttrNameValidator.isValid(name) then
        MeltWarnings.warn(s"Dropped attribute with invalid name: ${ truncate(name, 40) }")
      else if isEventHandler(name) then
        MeltWarnings.warn(s"Dropped event handler attribute from spread: $name")
      else if value == null || value == None then () // drop silently
      else if UrlAttributes.isUrlAttribute(tag, name) then
        push(s""" $name="${ Escape.url(value) }"""")
      else
        push(s""" $name="${ Escape.attr(value) }"""")
    }

  /** Finalises the renderer into an immutable [[RenderResult]]. */
  def result(): RenderResult =
    val cssHtml = cssSet.map { entry =>
      s"""<style id="${ entry.scopeId }">${ entry.code }</style>"""
    }.mkString("\n")

    RenderResult(
      body       = bodyBuf.toString,
      head       = headBuf.toString + cssHtml,
      css        = cssSet.toSet,
      components = usedComponents.toSet
    )

  // ── Internal helpers ───────────────────────────────────────────────────

  /** Tracks output size (UTF-16 char × 2 approximation — see design doc
    * §12.2.2 for the rationale and caveats) and raises
    * [[MeltRenderException]] if the limit is exceeded.
    */
  private def trackSize(s: String): Unit =
    if s == null then ()
    else
      outputBytes += s.length.toLong * 2L
      if outputBytes > config.maxOutputBytes then
        throw new MeltRenderException(
          s"Output size exceeded ${ config.maxOutputBytes } bytes " +
            s"(current: $outputBytes). " +
            "This usually indicates unbounded list rendering or an infinite loop. " +
            "If intentional, increase the limit via MELT_MAX_OUTPUT_BYTES env var."
        )

  private def isEventHandler(name: String): Boolean =
    val lower = name.toLowerCase
    lower.length > 2 && lower.startsWith("on")

  private def truncate(s: String, max: Int): String =
    if s.length <= max then s else s.substring(0, max) + "..."

object SsrRenderer:

  /** Per-renderer configuration. */
  final case class Config(
    maxComponentDepth: Int  = Config.defaultMaxComponentDepth,
    maxOutputBytes:    Long = Config.defaultMaxOutputBytes
  )

  object Config:

    /** Default maximum component nesting depth.
      *
      * Resolution order:
      *   1. Environment variable `MELT_MAX_COMPONENT_DEPTH`
      *   2. System property   `melt.maxComponentDepth`
      *   3. `1000`
      *
      * Non-positive or non-numeric values fall back to `1000`.
      */
    lazy val defaultMaxComponentDepth: Int =
      sys.env
        .get("MELT_MAX_COMPONENT_DEPTH")
        .orElse(sys.props.get("melt.maxComponentDepth"))
        .flatMap(_.toIntOption)
        .filter(_ > 0)
        .getOrElse(1000)

    /** Default maximum total output size (UTF-16 char × 2 approximation).
      *
      * Resolution order:
      *   1. Environment variable `MELT_MAX_OUTPUT_BYTES`
      *   2. System property   `melt.maxOutputBytes`
      *   3. `16 MB` (16 · 1024 · 1024)
      */
    lazy val defaultMaxOutputBytes: Long =
      sys.env
        .get("MELT_MAX_OUTPUT_BYTES")
        .orElse(sys.props.get("melt.maxOutputBytes"))
        .flatMap(_.toLongOption)
        .filter(_ > 0)
        .getOrElse(16L * 1024 * 1024)

    val default: Config = Config()
