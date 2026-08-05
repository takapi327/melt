/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.render

import melt.runtime.Escape

/** The immutable outcome of an SSR render.
  *
  * `RenderResult` is deliberately a plain `case class` so it is immutable
  * and thread-safe: it can be returned from a request handler, cached,
  * inspected, or serialised freely.
  *
  * @param body       HTML emitted to `<body>` (the component tree itself)
  * @param head       HTML emitted to `<head>` — `<melt:head>` free-form
  *                   content (non-`title` / non-`<meta name=...>` tags)
  *                   plus the collected `<style id="melt-…">` blocks.
  *                   Title / meta tags are merged separately (see below).
  * @param title      Deduplicated `<title>` content — the last component
  *                   to call `renderer.head.title(x)` wins. Use this as
  *                   the `%melt.title%` placeholder value in
  *                   [[meltkit.Template.render]] when the caller
  *                   does not provide an explicit title.
  * @param metaTags   Deduplicated `<meta name="...">` entries keyed by
  *                   name. Already folded into [[head]] in the canonical
  *                   order (name → content). Retained here for inspection
  *                   and potential merging by a parent renderer.
  * @param css        Unique CSS entries collected during rendering
  * @param components Component `moduleID`s used during rendering
  *                   (for future Hydration chunk resolution in Phase C)
  * @param hydrationProps
  *                   JSON-encoded Props per `moduleID`, as emitted by
  *                   `PropsCodec` during SSR. Templates inject these
  *                   as `<script type="application/json"
  *                   data-melt-props="...">` tags so the SPA hydration
  *                   entry can decode them back into the component's
  *                   `Props` type and call `apply(decoded)` instead of
  *                   falling back to defaults.
  */
final case class RenderResult(
  body:           String,
  head:           String,
  title:          Option[String]      = None,
  metaTags:       Map[String, String] = Map.empty,
  css:            Set[CssEntry]       = Set.empty,
  components:     Set[String]         = Set.empty,
  hydrationProps: Map[String, String] = Map.empty,
  imports:        List[String]        = Nil
)

object RenderResult:
  val empty: RenderResult = RenderResult("", "")

extension (result: RenderResult)

  /** Wraps this SSR result in a complete, **self-contained** HTML document —
    * `<!DOCTYPE html>` through `</html>` — with the component-scoped CSS inlined.
    *
    * Unlike [[meltkit.Template.render]], this references **no** client bundle,
    * Vite manifest, or hydration script: the output is pure server-rendered HTML.
    * It is the building block for the hydration-independent SSR path used by
    * server-only apps (auth screens, admin pages) that never ship a Scala.js
    * client. See `ctx.renderPage`.
    *
    * Head composition mirrors [[meltkit.Template.renderInternal]]: free-form
    * `<melt:head>` content and global CSS links ([[head]]) come first, then the
    * scoped `<style id="…">` blocks ([[css]], sorted by `scopeId`), then the
    * caller's `head` extras — so scoped styles win over global ones at equal
    * specificity. The document `<title>` prefers the component-supplied
    * [[title]] (last `renderer.head.title(...)` wins) and falls back to `title`.
    *
    * @param title fallback `<title>` used only when the component set none
    * @param lang  the `<html lang="…">` value
    * @param head  extra raw HTML injected at the end of `<head>` (meta tags,
    *              a CSS reset, etc.); the caller is responsible for its safety
    */
  def toHtmlDocument(title: String = "", lang: String = "en", head: String = ""): String =
    val cssHtml = result.css.toList
      .sortBy(_.scopeId)
      .map(e => s"""<style id="${ e.scopeId }">${ e.code }</style>""")
      .mkString("\n")
    val headContent = List(result.head, cssHtml, head).filter(_.nonEmpty).mkString("\n")
    val pageTitle   = result.title.getOrElse(title)
    s"""<!DOCTYPE html>
<html lang="${ Escape.attr(lang) }">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>${ Escape.html(pageTitle) }</title>
$headContent
</head>
<body>${ result.body }</body>
</html>"""

/** A single scoped CSS block discovered during rendering. */
final case class CssEntry(scopeId: String, code: String)
