/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.testkit

import scala.scalajs.js

import org.scalajs.dom

/** Simulates realistic browser user interactions on a component's DOM.
  *
  * Obtained via [[MountedComponent.userEvent]]. All methods fire the same
  * sequence of events that a real browser would produce, making them more
  * suitable for testing event-handler logic than the lower-level
  * [[MountedComponent.click]] / [[MountedComponent.input]] methods.
  *
  * {{{
  * val c = mount(Form.create())
  * c.userEvent.typeText("input[name=email]", "alice@example.com")
  * c.userEvent.click("button[type=submit]")
  * }}}
  */
final class UserEvent(
  private val container:   dom.Element,
  private val isUnmounted: () => Boolean
):

  // ── click ─────────────────────────────────────────────────────────────────

  /** Simulates a full click sequence on the first element matching `selector`.
    *
    * Event sequence: `pointerdown` → `mousedown` → `pointerup` → `mouseup` → `click`
    *
    * No events are dispatched if the element has the `disabled` attribute.
    * Throws [[NoSuchElementException]] if no element matches or after unmount.
    */
  def click(selector: String): Unit =
    val el = require(selector)
    if el.hasAttribute("disabled") then return
    focus(el)
    fire(el, pointerEvent("pointerdown"))
    fire(el, mouseEvent("mousedown"))
    fire(el, pointerEvent("pointerup"))
    fire(el, mouseEvent("mouseup"))
    fire(el, mouseEvent("click"))

  // ── typeText ──────────────────────────────────────────────────────────────

  /** Simulates typing `text` into the first element matching `selector`.
    *
    * Each character produces: `keydown` → `keypress` → value update → `input` → `keyup`.
    * The element's `.value` is updated incrementally, one character at a time,
    * so intermediate `input` events reflect partial values.
    *
    * Throws [[NoSuchElementException]] if no element matches or after unmount.
    */
  def typeText(selector: String, text: String): Unit =
    val el = require(selector)
    focus(el)
    text.foreach { char =>
      fire(el, keyboardEvent("keydown",  char))
      fire(el, keyboardEvent("keypress", char))
      appendValue(el, char)
      fire(el, new dom.Event("input", new dom.EventInit { bubbles = true }))
      fire(el, keyboardEvent("keyup", char))
    }

  // ── clear ─────────────────────────────────────────────────────────────────

  /** Clears the current value of the first element matching `selector`.
    *
    * Focuses the element, sets `.value` to `""`, and dispatches an `input` event.
    * Throws [[NoSuchElementException]] if no element matches or after unmount.
    */
  def clear(selector: String): Unit =
    val el = require(selector)
    focus(el)
    setValue(el, "")
    fire(el, new dom.Event("input", new dom.EventInit { bubbles = true }))

  // ── selectOption ──────────────────────────────────────────────────────────

  /** Selects the option with the given `value` in the `<select>` matching `selector`.
    *
    * Sets `.value` to `value` and dispatches a `change` event.
    * Throws [[NoSuchElementException]] if no element matches or after unmount.
    */
  def selectOption(selector: String, value: String): Unit =
    val el = require(selector)
    focus(el)
    el.asInstanceOf[dom.html.Select].value = value
    fire(el, new dom.Event("change", new dom.EventInit { bubbles = true }))

  // ── tab ───────────────────────────────────────────────────────────────────

  /** Simulates pressing the Tab key to move focus to the next focusable element.
    *
    * Dispatches `keydown` / `keyup` for Tab on the currently focused element and
    * attempts to move focus forward. Focus movement relies on the browser's (jsdom's)
    * built-in tabIndex ordering.
    */
  def tab(): Unit =
    if isUnmounted() then throw new IllegalStateException("Component has been unmounted")
    val active = dom.document.activeElement
    if active != null then
      fire(active, keyboardEvent("keydown", '\t'))
    // Move focus to the next focusable element within the container
    val focusable = focusableElements()
    val idx       = focusable.indexOf(active)
    val next      = if idx >= 0 && idx + 1 < focusable.length then Some(focusable(idx + 1))
                    else focusable.headOption
    next.foreach { el =>
      el.asInstanceOf[js.Dynamic].focus()
    }
    if active != null then
      fire(active, keyboardEvent("keyup", '\t'))

  // ── keyboard ──────────────────────────────────────────────────────────────

  /** Presses a special key (identified by its `KeyboardEvent.key` name) on the
    * currently focused element.
    *
    * Common key names: `"Enter"`, `"Escape"`, `"Backspace"`, `"Delete"`,
    * `"ArrowUp"`, `"ArrowDown"`, `"ArrowLeft"`, `"ArrowRight"`, `"Tab"`.
    *
    * Dispatches `keydown` → `keyup`. For `"Enter"` on a `<button>`, a `click`
    * event is also dispatched (matching browser behaviour).
    */
  def keyboard(key: String): Unit =
    if isUnmounted() then throw new IllegalStateException("Component has been unmounted")
    val active = dom.document.activeElement
    val target = if active != null && container.contains(active) then active else container
    fire(target, specialKeyEvent("keydown", key))
    // Enter on button triggers click
    if key == "Enter" then
      target.tagName.toLowerCase match
        case "button" => fire(target, mouseEvent("click"))
        case _        =>
    fire(target, specialKeyEvent("keyup", key))

  // ── private helpers ───────────────────────────────────────────────────────

  private def require(selector: String): dom.Element =
    if isUnmounted() then throw new IllegalStateException("Component has been unmounted")
    val el = container.querySelector(selector)
    if el == null then throw new NoSuchElementException(s"No element matches '$selector'")
    el

  private def focus(el: dom.Element): Unit =
    el.asInstanceOf[js.Dynamic].focus()

  private def fire(el: dom.EventTarget, event: dom.Event): Unit =
    el.dispatchEvent(event)

  private def mouseEvent(eventType: String): dom.MouseEvent =
    new dom.MouseEvent(eventType, new dom.MouseEventInit {
      bubbles    = true
      cancelable = true
    })

  private def pointerEvent(eventType: String): dom.Event =
    new dom.Event(eventType, new dom.EventInit {
      bubbles    = true
      cancelable = true
    })

  private def keyboardEvent(eventType: String, char: Char): dom.KeyboardEvent =
    new dom.KeyboardEvent(eventType, new dom.KeyboardEventInit {
      bubbles    = true
      cancelable = true
      key        = char.toString
      charCode   = char.toInt
      keyCode    = char.toInt
    })

  private def specialKeyEvent(eventType: String, keyName: String): dom.KeyboardEvent =
    new dom.KeyboardEvent(eventType, new dom.KeyboardEventInit {
      bubbles    = true
      cancelable = true
      key        = keyName
    })

  private def appendValue(el: dom.Element, char: Char): Unit =
    el.tagName.toLowerCase match
      case "input"    =>
        val inp = el.asInstanceOf[dom.html.Input]
        inp.value = inp.value + char.toString
      case "textarea" =>
        val ta = el.asInstanceOf[dom.html.TextArea]
        ta.value = ta.value + char.toString
      case _ =>

  private def setValue(el: dom.Element, value: String): Unit =
    el.tagName.toLowerCase match
      case "input"    => el.asInstanceOf[dom.html.Input].value    = value
      case "textarea" => el.asInstanceOf[dom.html.TextArea].value = value
      case "select"   => el.asInstanceOf[dom.html.Select].value   = value
      case _          =>

  private def focusableElements(): List[dom.Element] =
    val nl = container.querySelectorAll(
      "a[href], button:not([disabled]), input:not([disabled]), " +
        "select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex='-1'])"
    )
    (0 until nl.length).map(nl(_)).toList
