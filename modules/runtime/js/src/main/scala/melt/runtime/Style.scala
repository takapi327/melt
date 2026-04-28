/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import scala.collection.mutable

import org.scalajs.dom

/** Injects component-scoped CSS into the document `<head>`.
  *
  * Each component calls `Style.inject(scopeId, css)` once on first render.
  * Subsequent calls with the same `scopeId` are no-ops, so the same styles
  * are not duplicated when a component is mounted multiple times.
  *
  * The injected `<style>` element is tagged with `data-melt-scope` for
  * easy identification in dev-tools.
  */
object Style:

  private val injected = mutable.Set.empty[String]

  /** Injects `css` into `<head>` under the given `scopeId` (idempotent).
    *
    * Checks `getElementById(scopeId)` first so that SSR-injected
    * `<style id="melt-xxx">` elements are reused instead of duplicated.
    */
  def inject(scopeId: String, css: String): Unit =
    if !injected.contains(scopeId) then
      injected += scopeId
      if dom.document.getElementById(scopeId) == null then
        val style = dom.document.createElement("style")
        style.id = scopeId
        style.setAttribute("data-melt-scope", scopeId)
        style.textContent = css
        dom.document.head.appendChild(style)
      ()
