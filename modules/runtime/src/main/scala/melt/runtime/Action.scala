/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import org.scalajs.dom

/** A reusable element action attached via `use:actionName={param}`.
  *
  * An action receives an element and an optional parameter, performs setup,
  * and returns a cleanup function.
  *
  * {{{
  * val tooltip = Action[String] { (el, text) =>
  *   el.setAttribute("title", text)
  *   () => el.removeAttribute("title")
  * }
  * // In template: <div use:tooltip={"Help text"}>...</div>
  * }}}
  */
trait Action[P]:
  def apply(el: dom.Element, param: P): () => Unit

object Action:
  /** Creates an action from a setup function that returns a cleanup function. */
  def apply[P](f: (dom.Element, P) => (() => Unit)): Action[P] =
    new Action[P]:
      def apply(el: dom.Element, param: P): () => Unit = f(el, param)

  /** Creates a parameterless action. */
  def simple(f: dom.Element => (() => Unit)): Action[Unit] =
    new Action[Unit]:
      def apply(el: dom.Element, param: Unit): () => Unit = f(el)

// ── Built-in actions ──────────────────────────────────────────────────────

/** Focuses the element on mount. */
val autoFocus: Action[Unit] = Action.simple { el =>
  el.asInstanceOf[dom.html.Element].focus()
  () => ()
}

/** Calls the handler when a click occurs outside the element. */
val clickOutside: Action[() => Unit] = Action[(() => Unit)] { (el, handler) =>
  val listener: scalajs.js.Function1[dom.Event, Unit] =
    (e: dom.Event) => if !el.contains(e.target.asInstanceOf[dom.Node]) then handler()
  dom.document.addEventListener("click", listener)
  () => dom.document.removeEventListener("click", listener)
}

/** Traps focus within the element (Tab/Shift+Tab wrapping). */
val trapFocus: Action[Unit] = Action.simple { el =>
  val htmlEl = el.asInstanceOf[dom.html.Element]
  val listener: scalajs.js.Function1[dom.Event, Unit] = (e: dom.Event) =>
    val ke = e.asInstanceOf[dom.KeyboardEvent]
    if ke.key == "Tab" then
      val focusable = el.querySelectorAll(
        "a[href], button, textarea, input, select, [tabindex]:not([tabindex=\"-1\"])"
      )
      if focusable.length > 0 then
        val first = focusable(0).asInstanceOf[dom.html.Element]
        val last  = focusable(focusable.length - 1).asInstanceOf[dom.html.Element]
        if ke.shiftKey && dom.document.activeElement == first then
          ke.preventDefault()
          last.focus()
        else if !ke.shiftKey && dom.document.activeElement == last then
          ke.preventDefault()
          first.focus()
  htmlEl.addEventListener("keydown", listener)
  () => htmlEl.removeEventListener("keydown", listener)
}
