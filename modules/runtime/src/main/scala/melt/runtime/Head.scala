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
    existingSelector(child) match
      case Some(selector) =>
        val existing = Option(dom.document.head.querySelector(selector))
        existing.fold(dom.document.head.appendChild(child))(
          dom.document.head.replaceChild(child, _)
        )
      case None => dom.document.head.appendChild(child)
    Cleanup.register(() => {
      if dom.document.head.contains(child) then
        dom.document.head.removeChild(child)
        ()
    })

  /** Returns a CSS selector that uniquely identifies an element of the same
    * "kind" as `child` within `document.head`, or [[None]] if no deduplication
    * is needed (i.e. the element should always be appended).
    *
    * Matching rules:
    * - `<title>`                         → `Some("title")`
    * - `<meta name="…">`                 → `Some(meta[name="…"])`
    * - `<meta property="…">`             → `Some(meta[property="…"])`  (Open Graph etc.)
    * - `<meta http-equiv="…">`           → `Some(meta[http-equiv="…"])`
    * - `<link rel="…">`                  → `Some(link[rel="…"])`
    * - everything else                   → `None` (append)
    */
  private def existingSelector(child: dom.Element): Option[String] =
    child.tagName.toLowerCase match
      case "title" => Some("title")
      case "meta"  =>
        Option(child.getAttribute("name"))
          .map(n => s"""meta[name="$n"]""")
          .orElse(Option(child.getAttribute("property")).map(p => s"""meta[property="$p"]"""))
          .orElse(Option(child.getAttribute("http-equiv")).map(h => s"""meta[http-equiv="$h"]"""))
      case "link" =>
        Option(child.getAttribute("rel")).map(r => s"""link[rel="$r"]""")
      case _ => None
