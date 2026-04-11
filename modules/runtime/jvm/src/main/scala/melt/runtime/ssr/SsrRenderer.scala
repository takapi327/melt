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
  *         - `Template.fromResource(...)` — loads a user-owned HTML
  *           template with `%melt.X%` placeholders
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

  // §12.3.9 head dedup — the last call wins for title and for each
  // meta-tag name. These are NOT written into headBuf directly; they are
  // assembled into the final head string in result().
  private var titleContent: Option[String]                   = None
  private val metaTagMap:   mutable.LinkedHashMap[String, String] =
    mutable.LinkedHashMap.empty

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

    /** Sets the page title. Escapes `content` via [[Escape.html]].
      *
      * '''Dedup''': last call wins. Multiple components calling
      * `renderer.head.title(...)` all compete for the single `<title>`
      * slot, and the final value is the one from the latest call (after
      * merges). This matches Svelte 5's semantics.
      *
      * Called by the `<melt:head>` visitor when it encounters a
      * `<title>{expr}</title>` element.
      */
    def title(content: Any): Unit =
      val escaped = Escape.html(content)
      titleContent = Some(escaped)

    /** Sets a `<meta name="...">` entry. Subsequent calls with the same
      * `name` overwrite the previous `content`.
      *
      * Both `name` and `content` are HTML-escaped — `name` as an
      * attribute value and `content` as an attribute value — before
      * being stored.
      */
    def meta(name: String, content: Any): Unit =
      val escapedName    = Escape.attr(name)
      val escapedContent = Escape.attr(content)
      metaTagMap.update(escapedName, escapedContent)

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

  /** Merges a child component's [[RenderResult]] into this renderer's state.
    *
    * Dedup semantics (§12.3.9):
    *   - `title`: child's title wins if present (last-call-wins)
    *   - `metaTags`: each name is replaced by the child's value
    *
    * Other fields (body, head, css, components) are concatenated /
    * union-merged as usual.
    */
  def merge(child: RenderResult): Unit =
    trackSize(child.body)
    trackSize(child.head)
    bodyBuf ++= child.body
    headBuf ++= child.head
    child.title.foreach(t => titleContent = Some(t))
    child.metaTags.foreach { case (name, content) =>
      metaTagMap.update(name, content)
    }
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

  /** Runs `body` inside an error boundary — if it throws, `fallback` is
    * invoked with the exception and its return value is pushed into the
    * body buffer at the point where the failing region would have gone
    * (§12.3.7).
    *
    * Svelte 5 exposes `<svelte:boundary>` for the same purpose. Melt's
    * Phase C implementation is intentionally simple: the body lambda
    * runs immediately against this renderer, and any exception thrown
    * — including `MeltRenderException` from the Phase A depth / size
    * guards — is caught and replaced by the fallback HTML.
    *
    * Implementation notes:
    *
    *   - The boundary does NOT roll back already-emitted body content
    *     from a partially-rendered fragment. Callers should treat the
    *     fallback as an addition following whatever was emitted before
    *     the failure point.
    *   - The fallback string is pushed verbatim via [[push]], which
    *     means it is subject to the same size guard. It is the caller's
    *     responsibility to keep the fallback small (typically a one-
    *     paragraph error message).
    *   - The fallback result bypasses HTML escaping — pass either a
    *     static string or something you have already escaped.
    *
    * Example:
    * {{{
    *   renderer.boundary(e => s"<p>Failed: \${ Escape.html(e.getMessage) }</p>") { r =>
    *     r.push("<section>")
    *     // ... rendering that might throw ...
    *     r.push("</section>")
    *   }
    * }}}
    */
  def boundary(
    fallback: Throwable => String
  )(
    body: SsrRenderer => Unit
  ): Unit =
    try body(this)
    catch
      case t: Throwable =>
        val html =
          try fallback(t)
          catch case _: Throwable => "" // never let the fallback itself blow up
        // If the output is already over the size limit (which is likely
        // if body failed with MeltRenderException), the fallback would
        // itself blow up on trackSize. Write directly to the buffer to
        // keep the fallback visible without re-arming the guard. We
        // still nudge outputBytes so subsequent push() calls remain
        // consistent.
        if html.nonEmpty then
          bodyBuf ++= html
          outputBytes += html.length.toLong * 2L

  /** Emits a spread attribute `Map` to the body buffer, applying all
    * Phase A + Phase B security rules (§12.1.2 + §12.1.4):
    *
    *   - Drop keys whose name fails [[AttrNameValidator]]
    *   - Drop keys matching `on*` (event handlers must never be inlined
    *     in SSR — they only have a meaning in the hydrated DOM)
    *   - Drop keys beginning with `$$` (reserved for melt-internal props
    *     like `$$slots` / `$$children` — matches Svelte 5)
    *   - Drop values that are functions (`scala.Function*`) — these are
    *     event handlers passed as plain props and have no string form
    *   - Drop `null` / `None` values (nothing to serialise)
    *   - Unwrap `Some(x)` so that users can forward `Option`-valued
    *     props naturally
    *   - Handle `Boolean`: if `false`, drop; if `true`, emit bare
    *     attribute (HTML boolean attribute convention)
    *   - Route URL-typed attribute values through [[Escape.url]]
    *   - All other values go through [[Escape.attr]]
    *
    * Warnings are emitted via [[MeltWarnings]] so that a watching
    * developer sees dropped keys without the server crashing.
    */
  def spreadAttrs(tag: String, attrs: Map[String, Any]): Unit =
    attrs.foreach { case (name, rawValue) =>
      if !AttrNameValidator.isValid(name) then
        MeltWarnings.warn(s"Dropped attribute with invalid name: ${ truncate(name, 40) }")
      else if isEventHandler(name) then
        MeltWarnings.warn(s"Dropped event handler attribute from spread: $name")
      else if name.startsWith("$$") then
        // Reserved internal prop — silently skip. Svelte 5 parity.
        ()
      else
        val unwrapped = unwrapOption(rawValue)
        unwrapped match
          case null =>
            () // drop silently
          case f if isFunction(f) =>
            MeltWarnings.warn(s"Dropped function-valued spread attribute: $name")
          case false =>
            () // HTML: false boolean attr → omit entirely
          case true =>
            // HTML boolean attr → emit bare (no `="..."`)
            push(s" $name")
          case v if UrlAttributes.isUrlAttribute(tag, name) =>
            push(s""" $name="${ Escape.url(v) }"""")
          case v =>
            push(s""" $name="${ Escape.attr(v) }"""")
    }

  /** Unwraps at most a single layer of `Some(x)` so that users can pass
    * `Option[T]`-valued props through spread without surprises. Nested
    * `Some(Some(x))` is handled by a single unwrap — further unwrapping
    * is left to `Escape.*` which already handles `Option` recursively.
    */
  private def unwrapOption(value: Any): Any = value match
    case null        => null
    case None        => null
    case Some(inner) => inner
    case other       => other

  private def isFunction(value: Any): Boolean = value match
    case _: scala.Function0[_] => true
    case _: scala.Function1[_, _] => true
    case _: scala.Function2[_, _, _] => true
    case _: scala.Function3[_, _, _, _] => true
    case _: scala.Function4[_, _, _, _, _] => true
    case _: scala.Function5[_, _, _, _, _, _] => true
    case _                           => false

  /** Finalises the renderer into an immutable [[RenderResult]].
    *
    * The assembled `head` string contains, in order:
    *
    *   1. Deduplicated `<meta name="..." content="...">` tags
    *   2. Free-form head content previously appended via `head.push(...)`
    *   3. Dedupped `<title>...</title>` (if set)
    *   4. Collected `<style id="melt-...">` blocks
    *
    * Title placement after the free-form head content means that if a
    * component inserts its own `<title>` literally via
    * `head.push("<title>Foo</title>")`, the dedupped title still has the
    * final say because it is emitted afterwards (HTML5 keeps the last
    * `<title>`). In practice generated code always calls
    * `head.title(...)`, not `head.push`, so this fallback is rarely
    * exercised.
    */
  def result(): RenderResult =
    val metaHtml = metaTagMap.map { case (name, content) =>
      s"""<meta name="$name" content="$content">"""
    }.mkString("\n")

    val titleHtml = titleContent match
      case Some(t) => s"<title>$t</title>"
      case None    => ""

    val cssHtml = cssSet.map { entry =>
      s"""<style id="${ entry.scopeId }">${ entry.code }</style>"""
    }.mkString("\n")

    val headParts = List(metaHtml, headBuf.toString, titleHtml, cssHtml)
      .filter(_.nonEmpty)

    RenderResult(
      body       = bodyBuf.toString,
      head       = headParts.mkString("\n"),
      title      = titleContent,
      metaTags   = metaTagMap.toMap,
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
