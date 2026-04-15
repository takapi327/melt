/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import org.scalajs.dom
import melt.runtime.dom.Conversions

/** Focuses the element on mount. */
val autoFocus: Action[Unit] = Action.simple { el =>
  Conversions.unwrap(el).asInstanceOf[dom.html.Element].focus()
  () => ()
}

/** Calls the handler when a click occurs outside the element. */
val clickOutside: Action[() => Unit] = Action[(() => Unit)] { (el, handler) =>
  val rawEl = Conversions.unwrap(el)
  val listener: scalajs.js.Function1[dom.Event, Unit] =
    (e: dom.Event) => if !rawEl.contains(e.target.asInstanceOf[dom.Node]) then handler()
  dom.document.addEventListener("click", listener)
  () => dom.document.removeEventListener("click", listener)
}

/** Traps focus within the element (Tab/Shift+Tab wrapping). */
val trapFocus: Action[Unit] = Action.simple { el =>
  val rawEl = Conversions.unwrap(el)
  val htmlEl = rawEl.asInstanceOf[dom.html.Element]
  val listener: scalajs.js.Function1[dom.Event, Unit] = (e: dom.Event) =>
    val ke = e.asInstanceOf[dom.KeyboardEvent]
    if ke.key == "Tab" then
      val focusable = rawEl.querySelectorAll(
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
