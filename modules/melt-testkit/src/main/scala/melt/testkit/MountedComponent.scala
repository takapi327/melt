/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.testkit

import scala.scalajs.js

import org.scalajs.dom

import melt.runtime.Lifecycle

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

final class MountedComponent(
  private val container: dom.Element,
  private val _isScoped: Boolean = false
):

  /** Tracks whether [[unmount]] has been called. After unmounting all queries return empty results. */
  private var _unmounted: Boolean = false

  /** Provides realistic browser-level user interaction simulation.
    *
    * Fires the full event sequence a real browser would produce, making it
    * more accurate than the low-level [[click]] / [[input]] methods.
    *
    * {{{
    * c.userEvent.typeText("input[name=email]", "alice@example.com")
    * c.userEvent.click("button[type=submit]")
    * }}}
    */
  val userEvent: UserEvent = new UserEvent(container, () => _unmounted)

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

  /** Returns all elements whose normalised `textContent` matches `text`.
    *
    * Only the innermost matching element is kept — when a parent and a child
    * both satisfy the text condition the child is preferred, matching the
    * behaviour of `@testing-library/dom`.
    *
    * {{{
    * val items = c.findAllByText("Melt Counter")
    * }}}
    */
  def findAllByText(text: String, exact: Boolean = true): List[dom.Element] =
    if _unmounted then return Nil
    val normalised = MountedComponent.normalize(text)
    val all        = container.querySelectorAll("*")
    val candidates = (0 until all.length)
      .map(all(_))
      .filter { el =>
        val t = MountedComponent.normalize(el.textContent)
        if exact then t == normalised else t.contains(normalised)
      }
      .toList
    // Keep only the innermost: discard any element that contains another match as a descendant.
    candidates.filter(el => !candidates.exists(other => (el ne other) && el.contains(other)))

  /** Returns the first element whose `textContent` matches `text`, or `None`.
    *
    * When multiple elements match the innermost element is returned.
    * Returns `None` if no element matches or after [[unmount]].
    */
  def queryByText(text: String, exact: Boolean = true): Option[dom.Element] =
    findAllByText(text, exact).headOption

  /** Returns the single element whose `textContent` matches `text`.
    *
    * Throws [[NoSuchElementException]] if no element matches.
    * Throws [[IllegalArgumentException]] if more than one innermost element
    * matches — use [[findAllByText]] in that case.
    */
  def getByText(text: String, exact: Boolean = true): dom.Element =
    findAllByText(text, exact) match
      case List(el) => el
      case Nil      => throw new NoSuchElementException(s"No element with text '$text'")
      case els      =>
        throw new IllegalArgumentException(
          s"Found ${ els.length } elements with text '$text'. Use findAllByText instead."
        )

  /** Returns all elements whose normalised `textContent` matches `pattern`. */
  def findAllByText(pattern: scala.util.matching.Regex): List[dom.Element] =
    if _unmounted then return Nil
    val all        = container.querySelectorAll("*")
    val candidates = (0 until all.length)
      .map(all(_))
      .filter(el => pattern.findFirstIn(MountedComponent.normalize(el.textContent)).isDefined)
      .toList
    candidates.filter(el => !candidates.exists(other => (el ne other) && el.contains(other)))

  /** Returns the first element whose `textContent` matches `pattern`, or `None`. */
  def queryByText(pattern: scala.util.matching.Regex): Option[dom.Element] =
    findAllByText(pattern).headOption

  /** Returns the single element whose `textContent` matches `pattern`.
    *
    * Throws [[NoSuchElementException]] if no element matches.
    * Throws [[IllegalArgumentException]] if more than one innermost element matches.
    */
  def getByText(pattern: scala.util.matching.Regex): dom.Element =
    findAllByText(pattern) match
      case List(el) => el
      case Nil      => throw new NoSuchElementException(s"No element matching pattern '$pattern'")
      case els      =>
        throw new IllegalArgumentException(
          s"Found ${ els.length } elements matching pattern '$pattern'. Use findAllByText instead."
        )

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
      case els      =>
        throw new IllegalArgumentException(
          s"Found ${ els.length } elements with placeholder '$text'. Use findAllByPlaceholderText instead."
        )

  // ── getByDisplayValue ──────────────────────────────────────────────────────

  /** Returns all form elements whose current display value matches `text`.
    *
    * The **current value** (the JavaScript `.value` property, not the HTML `value`
    * attribute) is compared, so this reflects user edits and programmatic changes.
    *
    * Searched elements:
    *   - `<input>` — text-like types only (`text`, `email`, `password`, `tel`, `url`,
    *     `number`, `search`); `checkbox`, `radio`, `file`, `color`, `hidden`,
    *     `button`, `submit`, `reset`, and `image` are excluded.
    *   - `<textarea>` — full text content
    *   - `<select>` — the `value` of the currently selected option
    *
    * {{{
    * val nameInput = c.getByDisplayValue("Alice")
    * }}}
    */
  def findAllByDisplayValue(text: String, exact: Boolean = true): List[dom.Element] =
    if _unmounted then return Nil
    val selector =
      "input:not([type=checkbox]):not([type=radio]):not([type=file])" +
        ":not([type=color]):not([type=hidden]):not([type=image])" +
        ":not([type=button]):not([type=submit]):not([type=reset])" +
        ", textarea, select"
    val nl         = container.querySelectorAll(selector)
    val normalised = MountedComponent.normalize(text)
    (0 until nl.length)
      .map(nl(_))
      .filter { el =>
        val v = MountedComponent.normalize(displayValue(el))
        if exact then v == normalised else v.contains(normalised)
      }
      .toList

  /** Returns the first form element whose current display value matches `text`,
    * or `None` if no element is found or after [[unmount]].
    */
  def queryByDisplayValue(text: String, exact: Boolean = true): Option[dom.Element] =
    findAllByDisplayValue(text, exact).headOption

  /** Returns the single form element whose current display value matches `text`.
    *
    * Throws [[NoSuchElementException]] if no element matches.
    * Throws [[IllegalArgumentException]] if more than one element matches —
    * use [[findAllByDisplayValue]] in that case.
    */
  def getByDisplayValue(text: String, exact: Boolean = true): dom.Element =
    findAllByDisplayValue(text, exact) match
      case List(el) => el
      case Nil      => throw new NoSuchElementException(s"No element with display value '$text'")
      case els      =>
        throw new IllegalArgumentException(
          s"Found ${ els.length } elements with display value '$text'. Use findAllByDisplayValue instead."
        )

  private def displayValue(el: dom.Element): String =
    el.tagName.toLowerCase match
      case "input"    => el.asInstanceOf[dom.html.Input].value
      case "textarea" => el.asInstanceOf[dom.html.TextArea].value
      case "select"   => el.asInstanceOf[dom.html.Select].value
      case _          => ""

  // ── getByAltText ───────────────────────────────────────────────────────────

  /** Returns all elements whose `alt` attribute matches `text`.
    *
    * Only `<img>`, `<area>`, and `<input type="image">` elements are searched,
    * as these are the only elements for which `alt` carries accessible meaning.
    * When `exact = true` (default) the full normalised alt text must match;
    * when `exact = false` a substring match is performed.
    *
    * {{{
    * val images = c.findAllByAltText("user avatar")
    * }}}
    */
  def findAllByAltText(text: String, exact: Boolean = true): List[dom.Element] =
    if _unmounted then return Nil
    val nl         = container.querySelectorAll("img[alt], area[alt], input[type=image][alt]")
    val normalised = MountedComponent.normalize(text)
    (0 until nl.length)
      .map(nl(_))
      .filter { el =>
        val alt = MountedComponent.normalize(el.getAttribute("alt"))
        if exact then alt == normalised else alt.contains(normalised)
      }
      .toList

  /** Returns the first element whose `alt` attribute matches `text`,
    * or `None` if no element is found or after [[unmount]].
    */
  def queryByAltText(text: String, exact: Boolean = true): Option[dom.Element] =
    findAllByAltText(text, exact).headOption

  /** Returns the single element whose `alt` attribute matches `text`.
    *
    * Throws [[NoSuchElementException]] if no element matches.
    * Throws [[IllegalArgumentException]] if more than one element matches —
    * use [[findAllByAltText]] in that case.
    */
  def getByAltText(text: String, exact: Boolean = true): dom.Element =
    findAllByAltText(text, exact) match
      case List(el) => el
      case Nil      => throw new NoSuchElementException(s"No element with alt '$text'")
      case els      =>
        throw new IllegalArgumentException(
          s"Found ${ els.length } elements with alt '$text'. Use findAllByAltText instead."
        )

  // ── getByTitle ─────────────────────────────────────────────────────────────

  /** Returns all elements whose `title` attribute or SVG `<title>` child matches `text`.
    *
    * Two sources are searched in order and merged:
    *   1. Any element with a `title` attribute whose value matches `text`.
    *   2. SVG `<title>` child elements whose `textContent` matches `text` —
    *      the **parent** element (e.g. `<svg>`, `<circle>`) is returned, mirroring
    *      the behaviour of `@testing-library/dom`.
    *
    * When `exact = true` (default) the full normalised text must match;
    * when `exact = false` a substring match is performed.
    *
    * {{{
    * val icon = c.getByTitle("Close")          // <button title="Close">
    * val chart = c.getByTitle("Sales chart")   // <svg><title>Sales chart</title></svg>
    * }}}
    */
  def findAllByTitle(text: String, exact: Boolean = true): List[dom.Element] =
    if _unmounted then return Nil
    val normalised = MountedComponent.normalize(text)

    // 1. title 属性を持つ要素
    val byAttr =
      val nl = container.querySelectorAll("[title]")
      (0 until nl.length)
        .map(nl(_))
        .filter { el =>
          val t = MountedComponent.normalize(el.getAttribute("title"))
          if exact then t == normalised else t.contains(normalised)
        }
        .toList

    // 2. SVG <title> 子要素を持つ要素（親を返す）
    val bySvgTitle =
      val nl = container.querySelectorAll("title")
      (0 until nl.length)
        .map(nl(_))
        .filter { titleEl =>
          val t = MountedComponent.normalize(titleEl.textContent)
          if exact then t == normalised else t.contains(normalised)
        }
        .flatMap { titleEl =>
          titleEl.parentNode match
            case parent: dom.Element => Some(parent)
            case _                   => None
        }
        .toList

    (byAttr ++ bySvgTitle).distinct

  /** Returns the first element whose `title` attribute or SVG `<title>` child matches `text`,
    * or `None` if no element is found or after [[unmount]].
    */
  def queryByTitle(text: String, exact: Boolean = true): Option[dom.Element] =
    findAllByTitle(text, exact).headOption

  /** Returns the single element whose `title` attribute or SVG `<title>` child matches `text`.
    *
    * Throws [[NoSuchElementException]] if no element matches.
    * Throws [[IllegalArgumentException]] if more than one element matches —
    * use [[findAllByTitle]] in that case.
    */
  def getByTitle(text: String, exact: Boolean = true): dom.Element =
    findAllByTitle(text, exact) match
      case List(el) => el
      case Nil      => throw new NoSuchElementException(s"No element with title '$text'")
      case els      =>
        throw new IllegalArgumentException(
          s"Found ${ els.length } elements with title '$text'. Use findAllByTitle instead."
        )

  // ── getByRole ──────────────────────────────────────────────────────────────

  /** Returns all elements with the given ARIA `role` within the component.
    *
    * Both explicit (`role="..."`) and implicit roles (derived from the HTML tag)
    * are considered. Returns an empty list after [[unmount]].
    *
    * {{{
    * val buttons = c.getAllByRole("button")
    * assertEquals(buttons.length, 3)
    * }}}
    */
  /** Returns all elements with the given ARIA `role` within the component.
    *
    * Both explicit (`role="..."`) and implicit roles (derived from the HTML tag)
    * are considered. When `level` is given only heading elements at that level are
    * returned (e.g. `level = Some(1)` matches only `<h1>`). Returns an empty list
    * after [[unmount]].
    *
    * {{{
    * val buttons  = c.getAllByRole("button")
    * val h1s      = c.getAllByRole("heading", level = Some(1))
    * }}}
    */
  /** Returns all elements with the given ARIA `role` within the component.
    *
    * Both explicit (`role="..."`) and implicit roles (derived from the HTML tag)
    * are considered.
    *
    * Optional filters:
    *   - `level` — restricts heading elements by level (e.g. `Some(1)` matches only `<h1>`)
    *   - `name`  — restricts by accessible name (aria-labelledby → aria-label → native → title)
    *
    * Returns an empty list after [[unmount]].
    *
    * {{{
    * val buttons    = c.getAllByRole("button")
    * val h1s        = c.getAllByRole("heading", level = Some(1))
    * val submitBtn  = c.getAllByRole("button", name = Some("送信"))
    * }}}
    */
  def getAllByRole(
    role:  String,
    level: Option[Int] = None,
    name:  Option[String] = None
  ): List[dom.Element] =
    if _unmounted then return Nil
    val all = container.querySelectorAll("*")
    (0 until all.length)
      .map(all(_))
      .filter { el =>
        AriaUtils.resolveRole(el).contains(role) &&
        level.forall(l => AriaUtils.headingLevel(el).contains(l)) &&
        name.forall { n =>
          MountedComponent.normalize(AccessibleName.compute(el, container)) ==
            MountedComponent.normalize(n)
        }
      }
      .toList

  /** Returns the first element with the given ARIA `role`, or `None`.
    * Returns `None` after [[unmount]].
    */
  def queryByRole(
    role:  String,
    level: Option[Int] = None,
    name:  Option[String] = None
  ): Option[dom.Element] =
    getAllByRole(role, level, name).headOption

  /** Returns the single element with the given ARIA `role`.
    *
    * Throws [[NoSuchElementException]] if no element matches.
    * Throws [[IllegalArgumentException]] if more than one element matches —
    * use [[getAllByRole]] in that case.
    */
  def getByRole(
    role:  String,
    level: Option[Int] = None,
    name:  Option[String] = None
  ): dom.Element =
    val parts  = Seq(level.map(l => s"level=$l"), name.map(n => s"name=$n")).flatten
    val suffix = if parts.isEmpty then "" else s" (${ parts.mkString(", ") })"
    getAllByRole(role, level, name) match
      case List(el) => el
      case Nil      => throw new NoSuchElementException(s"No element with role '$role'$suffix")
      case els      =>
        throw new IllegalArgumentException(
          s"Found ${ els.length } elements with role '$role'$suffix. Use getAllByRole instead."
        )

  // ── getByLabelText ─────────────────────────────────────────────────────────

  /** Returns the form element associated with a label whose text matches `text`.
    *
    * Association is resolved in the following priority order:
    *   1. `aria-label` attribute on the element itself
    *   2. `<label for="id">` pointing to the element's `id`
    *   3. A wrapping `<label>` that contains the element as a descendant
    *   4. `aria-labelledby` referencing one or more elements by id
    *
    * Returns `None` if no associated element is found or after [[unmount]].
    */
  def queryByLabelText(text: String, exact: Boolean = true): Option[dom.Element] =
    if _unmounted then return None
    val n = MountedComponent.normalize(text)
    findByAriaLabel(n, exact)
      .orElse(findByLabelFor(n, exact))
      .orElse(findByWrappingLabel(n, exact))
      .orElse(findByAriaLabelledBy(n, exact))

  /** Returns the form element associated with a label whose text matches `text`.
    *
    * Throws [[NoSuchElementException]] if no associated element is found.
    */
  def getByLabelText(text: String, exact: Boolean = true): dom.Element =
    queryByLabelText(text, exact).getOrElse(
      throw new NoSuchElementException(s"No element labeled '$text'")
    )

  // label helpers

  private def findByAriaLabel(text: String, exact: Boolean): Option[dom.Element] =
    val all = container.querySelectorAll("[aria-label]")
    (0 until all.length).map(all(_)).find { el =>
      val label = MountedComponent.normalize(el.getAttribute("aria-label"))
      if exact then label == text else label.contains(text)
    }

  private def findByLabelFor(text: String, exact: Boolean): Option[dom.Element] =
    val labels = container.querySelectorAll("label[for]")
    (0 until labels.length)
      .map(labels(_))
      .find { label =>
        val t = MountedComponent.normalize(label.textContent)
        if exact then t == text else t.contains(text)
      }
      .flatMap { label =>
        val forId = label.getAttribute("for")
        if forId == null || forId.isEmpty then None
        else Option(container.querySelector(s"#$forId"))
      }

  private def findByWrappingLabel(text: String, exact: Boolean): Option[dom.Element] =
    val labels = container.querySelectorAll("label")
    (0 until labels.length)
      .map(labels(_))
      .find { label =>
        // Use only direct text nodes to avoid including the child input's value.
        val t = MountedComponent.normalize(directTextContent(label))
        if exact then t == text else t.contains(text)
      }
      .flatMap { label =>
        Option(label.querySelector("input, select, textarea"))
      }

  private def findByAriaLabelledBy(text: String, exact: Boolean): Option[dom.Element] =
    val all = container.querySelectorAll("[aria-labelledby]")
    (0 until all.length).map(all(_)).find { el =>
      val ids = el.getAttribute("aria-labelledby").split("\\s+").filter(_.nonEmpty)
      if ids.isEmpty then false
      else
        val labelText = MountedComponent.normalize(
          ids
            .flatMap(id => Option(container.querySelector(s"#$id")).map(_.textContent))
            .mkString(" ")
        )
        if exact then labelText == text else labelText.contains(text)
    }

  /** Collects only the direct text-node children of `el`, ignoring element children. */
  private def directTextContent(el: dom.Element): String =
    val nodes    = el.childNodes
    val TextNode = 3 // Node.TEXT_NODE
    (0 until nodes.length)
      .map(nodes(_))
      .filter(_.nodeType == TextNode)
      .map(_.textContent)
      .mkString

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
    if !_unmounted then js.Dynamic.global.console.log(PrettyDom(container))

  /** Prints the first element matching `selector` to the console.
    * Prints a "not found" message if no element matches.
    */
  def debug(selector: String): Unit =
    if _unmounted then return
    val el = container.querySelector(selector)
    if el == null then js.Dynamic.global.console.log(s"[melt-testkit] No element matches '$selector'")
    else js.Dynamic.global.console.log(PrettyDom(el))

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
    if _isScoped then return // within() scope — unmount is a no-op
    if !_unmounted then
      _unmounted = true
      Lifecycle.destroyTree(container)
      if container.parentNode != null then container.parentNode.removeChild(container)

  /** Returns a new [[MountedComponent]] whose queries are scoped to `element`.
    *
    * Useful when the same text or role appears in multiple places and you want
    * to restrict the search to a specific subtree.
    *
    * Calling [[unmount]] on the returned instance is a no-op — only the original
    * mounted component can be unmounted.
    *
    * {{{
    * val row   = c.getAllByRole("row")(1)
    * val scope = c.within(row)
    * assertEquals(scope.getAllByRole("cell").length, 3)
    * }}}
    */
  def within(element: dom.Element): MountedComponent =
    new MountedComponent(element, _isScoped = true)
