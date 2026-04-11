/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.ssr

/** The immutable outcome of an SSR render.
  *
  * `RenderResult` is deliberately a plain `case class` so it is immutable
  * and thread-safe: it can be returned from a request handler, cached,
  * inspected, or serialised freely.
  *
  * @param body       HTML emitted to `<body>` (the component tree itself)
  * @param head       HTML emitted to `<head>` (`<melt:head>` content plus
  *                   the collected `<style id="melt-…">` blocks)
  * @param css        Unique CSS entries collected during rendering
  * @param components Component `moduleID`s used during rendering
  *                   (for future Hydration chunk resolution in Phase C)
  */
final case class RenderResult(
  body:       String,
  head:       String,
  css:        Set[CssEntry],
  components: Set[String]
)

object RenderResult:
  val empty: RenderResult = RenderResult("", "", Set.empty, Set.empty)

/** A single scoped CSS block discovered during rendering. */
final case class CssEntry(scopeId: String, code: String)
