/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.render

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

/** A single scoped CSS block discovered during rendering. */
final case class CssEntry(scopeId: String, code: String)
