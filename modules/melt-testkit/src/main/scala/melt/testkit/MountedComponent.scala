/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.testkit

import scala.scalajs.js

import org.scalajs.dom


/** A handle to a mounted Melt component, providing query and interaction methods.
  *
  * Obtain an instance via [[MeltSuite.mount]]. All selector queries are scoped
  * to the component's container element so tests remain isolated from each other.
  *
  * {{{
  * class CounterSpec extends MeltSuite:
  *   test("increments on click") {
  *     val c = mount(Counter.create(Counter.Props("Test", 0)))
  *     c.click("button")
  *     assertEquals(c.text("p"), "Count: 1")
  *   }
  * }}}
  */
private[testkit] object MountedComponent:
  def normalize(text: String): String = text.trim.replaceAll("\\s+", " ")

final class MountedComponent(private val container: dom.html.Div):

  /** Tracks whether [[unmount]] has been called. After unmounting all queries return empty results. */
  private var _unmounted: Boolean = false

  /** Returns the text content of the first element matching `selector`,
    * or the empty string if no element is found or after [[unmount]].
    */
  def text(selector: String): String =
    if _unmounted then return ""
    val el = container.querySelector(selector)
    if el == null then "" else el.textContent

  /** Returns the value of the attribute `name` on the first element matching
    * `selector`, or `None` if the element or attribute is absent or after [[unmount]].
    */
  def attr(selector: String, name: String): Option[String] =
    if _unmounted then return None
    val el = container.querySelector(selector)
    if el == null then None
    else Option(el.getAttribute(name))

  /** Simulates a user click on the first element matching `selector`.
    *
    * Dispatches a bubbling `click` event. Throws if no element is found or after [[unmount]].
    */
  def click(selector: String): Unit =
    if _unmounted then throw new IllegalStateException("Component has been unmounted")
    val el = container.querySelector(selector)
    if el == null then throw new NoSuchElementException(s"No element matches '$selector'")
    el.dispatchEvent(new dom.MouseEvent("click", new dom.MouseEventInit { bubbles = true }))

  /** Simulates a user typing `value` into the first input matching `selector`.
    *
    * Sets `element.value` and dispatches a bubbling `input` event so reactive
    * bindings (`bind:value`) pick up the change. Throws if no element is found or after [[unmount]].
    */
  def input(selector: String, value: String): Unit =
    if _unmounted then throw new IllegalStateException("Component has been unmounted")
    val el = container.querySelector(selector)
    if el == null then throw new NoSuchElementException(s"No element matches '$selector'")
    el.asInstanceOf[dom.html.Input].value = value
    el.dispatchEvent(new dom.Event("input", new dom.EventInit { bubbles = true }))

  /** Returns `true` if at least one element matches `selector` within the component.
    * Always returns `false` after [[unmount]].
    */
  def exists(selector: String): Boolean =
    if _unmounted then false
    else container.querySelector(selector) != null

  /** Returns all elements matching `selector` within the component.
    * Returns an empty list after [[unmount]].
    */
  def findAll(selector: String): List[dom.Element] =
    if _unmounted then Nil
    else
      val nl = container.querySelectorAll(selector)
      (0 until nl.length).map(nl(_)).toList

  /** Returns all elements whose `placeholder` attribute matches `text`.
    *
    * Only `<input>` and `<textarea>` elements are searched.
    * When `exact = true` (default) the full normalised placeholder must match;
    * when `exact = false` a substring match is performed.
    *
    * {{{
    * val inputs = c.findAllByPlaceholderText("email")
    * }}}
    */
  def findAllByPlaceholderText(text: String, exact: Boolean = true): List[dom.Element] =
    if _unmounted then return Nil
    val nl         = container.querySelectorAll("input[placeholder], textarea[placeholder]")
    val normalised = MountedComponent.normalize(text)
    (0 until nl.length)
      .map(nl(_))
      .filter { el =>
        val ph = MountedComponent.normalize(el.getAttribute("placeholder"))
        if exact then ph == normalised else ph.contains(normalised)
      }
      .toList

  /** Returns the first element whose `placeholder` attribute matches `text`,
    * or `None` if no element is found or after [[unmount]].
    */
  def queryByPlaceholderText(text: String, exact: Boolean = true): Option[dom.Element] =
    findAllByPlaceholderText(text, exact).headOption

  /** Returns the single element whose `placeholder` attribute matches `text`.
    *
    * Throws [[NoSuchElementException]] if no element matches.
    * Throws [[IllegalArgumentException]] if more than one element matches —
    * use [[findAllByPlaceholderText]] in that case.
    */
  def getByPlaceholderText(text: String, exact: Boolean = true): dom.Element =
    findAllByPlaceholderText(text, exact) match
      case List(el) => el
      case Nil      => throw new NoSuchElementException(s"No element with placeholder '$text'")
      case els =>
        throw new IllegalArgumentException(
          s"Found ${els.length} elements with placeholder '$text'. Use findAllByPlaceholderText instead."
        )

  /** Prints the component's current DOM structure to the console.
    *
    * Uses `prettyDOM` from `@testing-library/dom` to produce an indented,
    * human-readable HTML representation. Useful when a test fails and you
    * want to inspect what is actually rendered.
    *
    * {{{
    * test("debug failing test") {
    *   val c = mount(Counter.create())
    *   c.debug()          // prints the full component DOM
    *   c.debug("button")  // prints only the first matching element
    * }
    * }}}
    */
  def debug(): Unit =
    js.Dynamic.global.console.log(PrettyDom(container))

  /** Prints the first element matching `selector` to the console.
    * Prints a "not found" message if no element matches.
    */
  def debug(selector: String): Unit =
    val el = container.querySelector(selector)
    if el == null then
      js.Dynamic.global.console.log(s"[melt-testkit] No element matches '$selector'")
    else
      js.Dynamic.global.console.log(PrettyDom(el))

  /** Removes the component's container element from the DOM.
    *
    * After calling this method, all query methods return empty/false results and
    * interaction methods throw [[IllegalStateException]].
    *
    * Any reactive subscriptions set up during `create()` will no longer update
    * the removed element; since Scala.js is garbage-collected, they will be
    * reclaimed when no other reference holds them.
    */
  def unmount(): Unit =
    if !_unmounted then
      _unmounted = true
      if container.parentNode != null then container.parentNode.removeChild(container)
