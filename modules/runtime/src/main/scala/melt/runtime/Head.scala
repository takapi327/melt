/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import org.scalajs.dom

/** Manages insertion and removal of elements into `document.head`.
  *
  * Each call to [[appendChild]] upserts the given element into `document.head`:
  * if an equivalent element already exists (matched by [[existingSelector]]) it is
  * replaced in-place; otherwise the element is appended.  A cleanup is registered
  * to remove the element when the component is destroyed.
  *
  * Reactive children (e.g. `<title>{pageTitle}</title>`) work naturally because
  * the child element is constructed by the normal code-gen path — [[Bind.text]] /
  * [[Bind.attr]] subscriptions are already registered in the current [[Cleanup]]
  * scope before [[appendChild]] is called.
  */
object Head:

  /** Upserts `child` into `document.head`.
    *
    * - If an equivalent element already exists it is replaced in-place.
    * - Otherwise `child` is appended.
    * - A cleanup is registered to remove `child` when the component is destroyed.
    */
  def appendChild(child: dom.Element): Unit =
    val selector = existingSelector(child)
    if selector != null then
      val existing = dom.document.head.querySelector(selector)
      if existing != null then dom.document.head.replaceChild(child, existing)
      else dom.document.head.appendChild(child)
    else dom.document.head.appendChild(child)
    Cleanup.register(() => {
      if dom.document.head.contains(child) then
        dom.document.head.removeChild(child)
        ()
    })

  /** Returns a CSS selector that uniquely identifies an element of the same
    * "kind" as `child` within `document.head`, or `null` if no deduplication
    * is needed (i.e. the element should always be appended).
    *
    * Matching rules:
    * - `<title>`                         → `"title"`
    * - `<meta name="…">`                 → `meta[name="…"]`
    * - `<meta property="…">`             → `meta[property="…"]`  (Open Graph etc.)
    * - `<meta http-equiv="…">`           → `meta[http-equiv="…"]`
    * - `<link rel="…">`                  → `link[rel="…"]`
    * - everything else                   → `null` (append)
    */
  private def existingSelector(child: dom.Element): String | Null =
    child.tagName.toLowerCase match
      case "title" => "title"
      case "meta" =>
        val name      = child.getAttribute("name")
        val property  = child.getAttribute("property")
        val httpEquiv = child.getAttribute("http-equiv")
        if name != null      then s"""meta[name="$name"]"""
        else if property != null  then s"""meta[property="$property"]"""
        else if httpEquiv != null then s"""meta[http-equiv="$httpEquiv"]"""
        else null
      case "link" =>
        val rel = child.getAttribute("rel")
        if rel != null then s"""link[rel="$rel"]""" else null
      case _ => null
