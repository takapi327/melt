/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import org.scalajs.dom

/** Walks SSR-rendered DOM siblings during claim hydration.
  *
  * Created by [[Hydrating.withCursor]] at the start of each hydration
  * boundary and pushed/popped on each [[Hydrating.withChildren]] call.
  *
  * A cursor with `current == null` acts as a sentinel: all claim methods
  * return `null`, forcing [[Hydrating]] to fall back to fresh
  * `createElement`/`createTextNode` calls (used inside dynamic-section
  * render lambdas where the SSR content has been cleared).
  */
final class HydrationCursor(private[this] var _current: dom.Node | Null):

  def current: dom.Node | Null = _current

  /** Skips whitespace text nodes and comment nodes that are NOT dyn-markers,
    * returning the first "significant" node at or after `_current`.
    * Does NOT advance `_current`.
    */
  private def peekSignificant(): dom.Node | Null =
    var n = _current
    while n != null && (
        n.nodeType == dom.Node.COMMENT_NODE ||
          (n.nodeType == dom.Node.TEXT_NODE && n.textContent.trim.isEmpty)
      )
    do n = n.nextSibling
    n

  /** Claims the next `dom.Element` whose tag matches `tag` (case-insensitive).
    *
    * Skips whitespace text nodes and non-dyn comment nodes.
    * Returns `null` without advancing if the next significant node is not
    * a matching element (graceful miss — [[Hydrating]] falls back to
    * `createElement`).
    */
  def nextElement(tag: String): dom.Element | Null =
    peekSignificant() match
      case el: dom.Element if el.tagName.equalsIgnoreCase(tag) =>
        _current = el.nextSibling
        el
      case _ =>
        null

  /** Claims the next `dom.Text` node.
    *
    * Skips comment nodes (including dyn-markers).
    * Returns `null` without advancing if the next node is not a text node.
    */
  def nextText(): dom.Text | Null =
    var n = _current
    while n != null && n.nodeType == dom.Node.COMMENT_NODE do n = n.nextSibling
    n match
      case t: dom.Text =>
        _current = t.nextSibling
        t
      case _ =>
        null

  /** Locates the opening `<!--[melt:dyn-->` comment, removes all nodes
    * between it and the closing `<!--]melt:dyn-->`, and returns the
    * closing comment as the new reactive anchor.
    *
    * The closing `<!--]melt:dyn-->` comment remains in the DOM so that
    * [[melt.runtime.Bind]] methods can insert updated DOM before it.
    *
    * Returns `null` if no opening dyn-marker is found at the current
    * position (graceful miss — caller falls back to `createComment` +
    * `appendChild`).
    */
  def consumeDyn(parent: dom.Element): dom.Comment | Null =
    var n = _current
    while n != null && !(n.nodeType == dom.Node.COMMENT_NODE && n
        .asInstanceOf[dom.Comment]
        .data == "[melt:dyn")
    do n = n.nextSibling
    n match
      case open: dom.Comment if open.data == "[melt:dyn" =>
        // Remove all nodes between open and close dyn markers, tracking nested
        // [melt:dyn / ]melt:dyn pairs so that inner closing markers are not
        // mistaken for the outer closing marker.
        // Use each node's actual parentNode for removal — the `parent` argument
        // may be a freshly-created element (e.g. _root in a child hydrate entry)
        // that is not yet attached to the SSR DOM, so calling parent.removeChild
        // on SSR nodes would throw NotFoundError.
        var toRemove = open.nextSibling
        var close: dom.Comment | Null = null
        var depth = 0
        while toRemove != null && close == null do
          toRemove match
            case c: dom.Comment if c.data == "[melt:dyn" =>
              depth += 1
              val next = c.nextSibling
              Option(c.parentNode).foreach(_.removeChild(c))
              toRemove = next
            case c: dom.Comment if c.data == "]melt:dyn" =>
              if depth == 0 then
                close = c
              else
                depth -= 1
                val next = c.nextSibling
                Option(c.parentNode).foreach(_.removeChild(c))
                toRemove = next
            case other =>
              val next = other.nextSibling
              Option(other.parentNode).foreach(_.removeChild(other))
              toRemove = next
        // Also remove the opening marker
        Option(open.parentNode).foreach(_.removeChild(open))
        // Advance cursor past the closing marker
        _current = close match
          case null => null
          case cl   => cl.nextSibling
        close
      case _ =>
        null
