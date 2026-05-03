/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.render

import scala.collection.mutable

import melt.runtime.{ AttrNameValidator, Escape, MeltWarnings, UrlAttributes }

/** SSR rendering engine used by `meltc`-generated code.
  *
  * @note '''Internal API.''' This class is part of the protocol between
  *       `meltc`-generated components and the melt runtime. Do not
  *       instantiate or call its methods from hand-written user code.
  *
  *       User code should only interact with:
  *         - `Component.render(props): RenderResult` — the entry point
  *           exposed by each generated component
  *         - `Http4sAdapter(app, clientDistDir, manifest)` — loads the
  *           HTML template and wires SSR rendering via `ctx.melt()`
  *         - `TrustedHtml` / `Escape.*` — explicit opt-in escalation paths
  *
  *       Methods on `ServerRenderer` may change without notice between
  *       versions.
  *
  * @note Not thread-safe. `meltc`-generated code creates a fresh instance
  *       per `render()` call; never share an instance across requests.
  */
final class ServerRenderer(val config: ServerRenderer.Config = ServerRenderer.Config.default):

  private val bodyBuf        = new StringBuilder
  private val headBuf        = new StringBuilder
  private val cssSet         = mutable.LinkedHashSet.empty[CssEntry]
  private val usedComponents = mutable.LinkedHashSet.empty[String]

  private val hydrationPropsMap: mutable.LinkedHashMap[String, String] =
    mutable.LinkedHashMap.empty

  private var titleContent: Option[String]                        = None
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

    /** Sets the page title. Stores the HTML-escaped value so that
      * `result.title` always contains an escaped string ready for direct
      * substitution into `%melt.title%`.
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
      titleContent = Some(Escape.html(content))

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
    * re-checked here.
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

  /** Records the JSON-encoded Props for a component instance. Called
    * by `meltc`-generated SSR code immediately after `trackComponent`
    * when the component declares a `props="..."` attribute.
    *
    * The last call wins if the same `moduleID` is tracked more than
    * once in a single render (matches the existing component dedup
    * semantics). Multi-instance support is a future extension that
    * will keyed instances by DOM position.
    */
  def trackHydrationProps(moduleId: String, json: String): Unit =
    trackSize(json)
    hydrationPropsMap.update(moduleId, json)

  /** Merges a child component's [[RenderResult]] into this renderer's state.
    *
    * Dedup semantics:
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
    child.metaTags.foreach {
      case (name, content) =>
        metaTagMap.update(name, content)
    }
    child.css.foreach { entry =>
      if !cssSet.contains(entry) then
        trackSize(entry.scopeId)
        trackSize(entry.code)
        cssSet += entry
    }
    usedComponents ++= child.components
    child.hydrationProps.foreach {
      case (moduleId, json) =>
        hydrationPropsMap.update(moduleId, json)
    }

  /** Merges a child component's metadata (head, CSS, components, hydration
    * props) into this renderer **without** appending `child.body` to the body
    * buffer.
    *
    * Use this when the body is already being written via an intermediate
    * `StringBuilder` (e.g. inside a conditional `if/else` expression) to avoid
    * pushing the body twice.  The caller is responsible for appending
    * `child.body` to its own string accumulator.
    */
  def mergeMeta(child: RenderResult): Unit =
    trackSize(child.head)
    headBuf ++= child.head
    child.title.foreach(t => titleContent = Some(t))
    child.metaTags.foreach {
      case (name, content) =>
        metaTagMap.update(name, content)
    }
    child.css.foreach { entry =>
      if !cssSet.contains(entry) then
        trackSize(entry.scopeId)
        trackSize(entry.code)
        cssSet += entry
    }
    usedComponents ++= child.components
    child.hydrationProps.foreach {
      case (moduleId, json) =>
        hydrationPropsMap.update(moduleId, json)
    }

  /** Increments the component nesting counter and throws
    * [[RenderException]] if the configured limit is exceeded.
    */
  def enterComponent(name: String): Unit =
    componentDepth += 1
    if componentDepth > config.maxComponentDepth then
      throw new RenderException(
        s"Component nesting depth exceeded ${ config.maxComponentDepth } at '$name'. " +
          "This usually indicates infinite recursion in component references. " +
          "If intentional, increase the limit via MELT_MAX_COMPONENT_DEPTH env var."
      )

  /** Decrements the component nesting counter. */
  def exitComponent(): Unit = componentDepth -= 1

  /** Runs `body` inside an error boundary — if it throws, `fallback` is
    * invoked with the exception and its return value is pushed into the
    * body buffer at the point where the failing region would have gone.
    *
    * Svelte 5 exposes `<svelte:boundary>` for the same purpose. Melt's
    * Phase C implementation is intentionally simple: the body lambda
    * runs immediately against this renderer, and any exception thrown
    * — including `RenderException` from the Phase A depth / size
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
    body: ServerRenderer => Unit
  ): Unit =
    try body(this)
    catch
      case t: Throwable =>
        val html =
          try fallback(t)
          catch case _: Throwable => ""
        if html.nonEmpty then
          bodyBuf ++= html
          outputBytes += html.length.toLong * 2L

  /** Emits a spread attribute `Map` to the body buffer, applying all
    * Phase A + Phase B security rules:
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
    attrs.foreach {
      case (name, rawValue) =>
        if !AttrNameValidator.isValid(name) then
          MeltWarnings.warn(s"Dropped attribute with invalid name: ${ truncate(name, 40) }")
        else if isEventHandler(name) then MeltWarnings.warn(s"Dropped event handler attribute from spread: $name")
        else if name.startsWith("$$") then ()
        else
          val unwrapped = unwrapOption(rawValue)
          unwrapped match
            case null =>
              ()
            case f if isFunction(f) =>
              MeltWarnings.warn(s"Dropped function-valued spread attribute: $name")
            case t if isTuple(t) =>
              MeltWarnings.warn(
                s"Dropped Tuple/Named Tuple spread attribute '$name': " +
                  "field names are erased at runtime and cannot be expanded into individual attributes. " +
                  "Use individual prop bindings instead."
              )
            case false =>
              ()
            case true =>
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
    case _: scala.Function0[?]                                                                    => true
    case _: scala.Function1[?, ?]                                                                 => true
    case _: scala.Function2[?, ?, ?]                                                              => true
    case _: scala.Function3[?, ?, ?, ?]                                                           => true
    case _: scala.Function4[?, ?, ?, ?, ?]                                                        => true
    case _: scala.Function5[?, ?, ?, ?, ?, ?]                                                     => true
    case _: scala.Function6[?, ?, ?, ?, ?, ?, ?]                                                  => true
    case _: scala.Function7[?, ?, ?, ?, ?, ?, ?, ?]                                               => true
    case _: scala.Function8[?, ?, ?, ?, ?, ?, ?, ?, ?]                                            => true
    case _: scala.Function9[?, ?, ?, ?, ?, ?, ?, ?, ?, ?]                                         => true
    case _: scala.Function10[?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?]                                     => true
    case _: scala.Function11[?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?]                                  => true
    case _: scala.Function12[?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?]                               => true
    case _: scala.Function13[?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?]                            => true
    case _: scala.Function14[?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?]                         => true
    case _: scala.Function15[?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?]                      => true
    case _: scala.Function16[?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?]                   => true
    case _: scala.Function17[?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?]                => true
    case _: scala.Function18[?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?]             => true
    case _: scala.Function19[?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?]          => true
    case _: scala.Function20[?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?]       => true
    case _: scala.Function21[?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?]    => true
    case _: scala.Function22[?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?] => true
    case _: scala.runtime.FunctionXXL => true // arity 23+ (Scala 3)
    case _                            => false

  private def isTuple(value: Any): Boolean = value match
    case _: scala.Tuple => true
    case _              => false

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
    val metaHtml = metaTagMap
      .map {
        case (name, content) =>
          s"""<meta name="$name" content="$content">"""
      }
      .mkString("\n")

    val titleHtml = titleContent match
      case Some(t) => s"<title>$t</title>"
      case None    => ""

    val cssHtml = cssSet
      .map { entry =>
        s"""<style id="${ entry.scopeId }">${ entry.code }</style>"""
      }
      .mkString("\n")

    val headParts = List(metaHtml, headBuf.toString, titleHtml, cssHtml)
      .filter(_.nonEmpty)

    RenderResult(
      body           = bodyBuf.toString,
      head           = headParts.mkString("\n"),
      title          = titleContent,
      metaTags       = metaTagMap.toMap,
      css            = cssSet.toSet,
      components     = usedComponents.toSet,
      hydrationProps = hydrationPropsMap.toMap
    )

  /** Tracks output size (UTF-16 char × 2 approximation) and raises
    * [[RenderException]] if the limit is exceeded.
    */
  private def trackSize(s: String): Unit =
    if s == null then ()
    else
      outputBytes += s.length.toLong * 2L
      if outputBytes > config.maxOutputBytes then
        throw new RenderException(
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

object ServerRenderer:

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
