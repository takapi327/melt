/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package components

import melt.testkit.*

/** Tests for the Counter component, demonstrating the melt-testkit API.
  *
  * Run with: sbt "counter/test"
  */
class CounterSpec extends MeltSuite:

  // ── Initial render ───────────────────────────────────────────────────────

  test("renders heading") {
    val c = mount(Counter.create())
    assertEquals(c.text("h1"), "Melt Counter")
  }

  test("renders initial count of zero") {
    val c = mount(Counter.create())
    assertEquals(c.text("p"), "Count: 0")
  }

  test("renders initial doubled value") {
    val c = mount(Counter.create())
    // The second <p> shows doubled count
    val allPs = c.findAll("p")
    assert(allPs.nonEmpty, "should have at least one <p>")
    val doubledText = allPs(1).textContent
    assertEquals(doubledText, "Doubled: 0")
  }

  test("exists returns true for present elements") {
    val c = mount(Counter.create())
    assert(c.exists("h1"), "h1 should exist")
    assert(c.exists("button"), "button should exist")
    assert(!c.exists(".nonexistent"), ".nonexistent should not exist")
  }

  test("findAll returns all matching elements") {
    val c       = mount(Counter.create())
    val buttons = c.findAll("button")
    // Counter has three buttons: +1, -1, Reset All
    assertEquals(buttons.length, 3)
  }

  test("attr returns element attribute") {
    val c           = mount(Counter.create())
    val placeholder = c.attr("input", "placeholder")
    assertEquals(placeholder, Some("Your name"))
  }

  // ── Increment / decrement ────────────────────────────────────────────────

  test("clicking +1 button increments count") {
    val c = mount(Counter.create())
    c.click("button")
    assertEquals(c.text("p"), "Count: 1")
  }

  test("clicking +1 twice gives count 2") {
    val c = mount(Counter.create())
    c.click("button")
    c.click("button")
    assertEquals(c.text("p"), "Count: 2")
  }

  test("clicking -1 button decrements count") {
    val c = mount(Counter.create())
    // Increment first so we can decrement
    c.click("button")
    // The second button is -1
    val buttons = c.findAll("button")
    buttons(1).dispatchEvent(
      new org.scalajs.dom.MouseEvent("click", new org.scalajs.dom.MouseEventInit { bubbles = true })
    )
    assertEquals(c.text("p"), "Count: 0")
  }

  // ── Reset ────────────────────────────────────────────────────────────────

  test("Reset All button resets count to zero") {
    val c = mount(Counter.create())
    c.click("button")
    c.click("button")
    // Third button is Reset All
    val buttons = c.findAll("button")
    buttons(2).dispatchEvent(
      new org.scalajs.dom.MouseEvent("click", new org.scalajs.dom.MouseEventInit { bubbles = true })
    )
    assertEquals(c.text("p"), "Count: 0")
  }

  // ── Reactive bind:value ──────────────────────────────────────────────────

  test("typing in input updates greeting") {
    val c = mount(Counter.create())
    c.input("input", "Melt")
    // The last <p> shows "Hello, {name}!"
    val allPs    = c.findAll("p")
    val greeting = allPs.last.textContent
    assertEquals(greeting, "Hello, Melt!")
  }

  // ── Unmount ──────────────────────────────────────────────────────────────

  test("unmount removes component from DOM") {
    val c = mount(Counter.create())
    assert(c.exists("h1"), "h1 should exist before unmount")
    c.unmount()
    // After unmount the container is detached; queries fall through to not found
    assert(!c.exists("h1"), "h1 should be gone after unmount")
  }

  // ── getByText ────────────────────────────────────────────────────────────

  test("getByText finds element by exact text") {
    val c  = mount(Counter.create())
    val el = c.getByText("Melt Counter")
    assertEquals(el.tagName.toLowerCase, "h1")
  }

  test("queryByText returns Some for existing text") {
    val c = mount(Counter.create())
    assert(c.queryByText("Melt Counter").isDefined)
  }

  test("queryByText returns None for missing text") {
    val c = mount(Counter.create())
    assertEquals(c.queryByText("Does not exist"), None)
  }

  test("findAllByText with exact = false does substring match") {
    val c       = mount(Counter.create())
    val results = c.findAllByText("Count", exact = false)
    assert(results.nonEmpty, "should find elements containing 'Count'")
  }

  test("getByText with Regex finds element") {
    val c  = mount(Counter.create())
    val el = c.getByText("Melt Counter".r)
    assertEquals(el.tagName.toLowerCase, "h1")
  }

  test("queryByText with Regex returns None when no match") {
    val c = mount(Counter.create())
    assertEquals(c.queryByText("xyz123".r), None)
  }

  test("getByText throws NoSuchElementException when not found") {
    val c = mount(Counter.create())
    intercept[NoSuchElementException] {
      c.getByText("Not present")
    }
  }

  // ── getByPlaceholderText ─────────────────────────────────────────────────

  test("getByPlaceholderText finds input by exact placeholder") {
    val c  = mount(Counter.create())
    val el = c.getByPlaceholderText("Your name")
    assertEquals(el.tagName.toLowerCase, "input")
  }

  test("queryByPlaceholderText returns Some for existing placeholder") {
    val c = mount(Counter.create())
    assert(c.queryByPlaceholderText("Your name").isDefined, "should find input")
  }

  test("queryByPlaceholderText returns None for missing placeholder") {
    val c = mount(Counter.create())
    assertEquals(c.queryByPlaceholderText("Nonexistent"), None)
  }

  test("findAllByPlaceholderText with exact = false does substring match") {
    val c       = mount(Counter.create())
    val results = c.findAllByPlaceholderText("name", exact = false)
    assertEquals(results.length, 1)
  }

  test("getByPlaceholderText throws when element is not found") {
    val c = mount(Counter.create())
    intercept[NoSuchElementException] {
      c.getByPlaceholderText("Does not exist")
    }
  }

  // ── getByRole ────────────────────────────────────────────────────────────

  test("getAllByRole returns all buttons") {
    val c       = mount(Counter.create())
    val buttons = c.getAllByRole("button")
    assertEquals(buttons.length, 3)
  }

  test("getAllByRole returns the heading") {
    val c        = mount(Counter.create())
    val headings = c.getAllByRole("heading")
    assertEquals(headings.length, 1)
    assertEquals(headings.head.textContent, "Melt Counter")
  }

  test("queryByRole returns Some for existing role") {
    val c = mount(Counter.create())
    assert(c.queryByRole("heading").isDefined)
  }

  test("queryByRole returns None for absent role") {
    val c = mount(Counter.create())
    assertEquals(c.queryByRole("dialog"), None)
  }

  test("getAllByRole returns textbox for input") {
    val c      = mount(Counter.create())
    val inputs = c.getAllByRole("textbox")
    assertEquals(inputs.length, 1)
  }

  test("getAllByRole finds input as textbox") {
    val c      = mount(Counter.create())
    val inputs = c.getAllByRole("textbox")
    // The counter's <input> has no type (defaults to text → textbox)
    assertEquals(inputs.length, 1)
  }

  test("getAllByRole returns empty for color/file input (no role)") {
    // color and file inputs have no ARIA role — verified via getAllByRole
    val c = mount(Counter.create())
    // Counter has no color/file inputs; list role should be empty too
    val dialogs = c.getAllByRole("dialog")
    assertEquals(dialogs.length, 0)
  }

  test("getByRole throws when no element matches") {
    val c = mount(Counter.create())
    intercept[NoSuchElementException] {
      c.getByRole("dialog")
    }
  }

  test("getByRole throws when multiple elements match") {
    val c = mount(Counter.create())
    intercept[IllegalArgumentException] {
      c.getByRole("button") // Counter has 3 buttons
    }
  }

  // ── getByLabelText ───────────────────────────────────────────────────────

  test("getByLabelText finds element via aria-label") {
    val c = mount(Counter.create())
    // The input has no label in the Counter, so we test queryByLabelText returns None
    assertEquals(c.queryByLabelText("Your name"), None)
  }

  test("queryByLabelText returns None when label does not exist") {
    val c = mount(Counter.create())
    assertEquals(c.queryByLabelText("Nonexistent label"), None)
  }

  // ── waitFor ──────────────────────────────────────────────────────────────

  test("waitFor succeeds when assertion already holds") {
    val c = mount(Counter.create())
    waitFor { () =>
      assertEquals(c.text("h1"), "Melt Counter")
    }
  }

  test("waitFor succeeds after a reactive state change") {
    val c = mount(Counter.create())
    c.click("button") // +1
    waitFor { () =>
      assertEquals(c.text("p"), "Count: 1")
    }
  }

  test("waitFor times out when assertion never holds") {
    val c = mount(Counter.create())
    // waitFor should produce a failed Future; .failed converts it to a successful Future[Throwable]
    // so this test passes iff waitFor fails, and fails iff waitFor unexpectedly succeeds.
    waitFor(() => assertEquals(c.text("h1"), "Wrong text"), timeout = 100).failed
      .map(_ => ())
  }

  // ── debug() ──────────────────────────────────────────────────────────────

  test("debug prints component DOM without throwing") {
    val c = mount(Counter.create())
    c.debug()               // full DOM
    c.debug("h1")           // specific element
    c.debug(".nonexistent") // missing element — prints message, does not throw
  }

  // ── getByRole level / name options ───────────────────────────────────────

  private def headingFixture(): org.scalajs.dom.Element =
    val wrapper = org.scalajs.dom.document.createElement("div")
    Seq("h1" -> "Page Title", "h2" -> "Section One", "h2" -> "Section Two", "h3" -> "Sub").foreach {
      case (tag, text) =>
        val el = org.scalajs.dom.document.createElement(tag)
        el.textContent = text
        wrapper.appendChild(el)
    }
    val btn = org.scalajs.dom.document.createElement("button")
    btn.textContent = "送信"
    wrapper.appendChild(btn)
    wrapper

  test("getAllByRole with level returns only matching heading elements") {
    val c = mount(headingFixture())
    assertEquals(c.getAllByRole("heading", level = Some(1)).length, 1)
    assertEquals(c.getAllByRole("heading", level = Some(2)).length, 2)
    assertEquals(c.getAllByRole("heading", level = Some(3)).length, 1)
  }

  test("getByRole with level finds the single h1") {
    val c  = mount(headingFixture())
    val el = c.getByRole("heading", level = Some(1))
    assertEquals(el.textContent, "Page Title")
  }

  test("getByRole with level throws when not found") {
    val c = mount(headingFixture())
    intercept[NoSuchElementException] {
      c.getByRole("heading", level = Some(6))
    }
  }

  test("getByRole with name finds button by accessible name") {
    val c  = mount(headingFixture())
    val el = c.getByRole("button", name = Some("送信"))
    assertEquals(el.tagName.toLowerCase, "button")
  }

  test("queryByRole with name returns None when name does not match") {
    val c = mount(headingFixture())
    assertEquals(c.queryByRole("button", name = Some("削除")), None)
  }

  test("getByRole with level and name together") {
    val c  = mount(headingFixture())
    val el = c.getByRole("heading", level = Some(2), name = Some("Section One"))
    assertEquals(el.textContent, "Section One")
  }

  // ── within ───────────────────────────────────────────────────────────────

  private def listFixture(): org.scalajs.dom.Element =
    val ul = org.scalajs.dom.document.createElement("ul")
    Seq("Alice", "Bob", "Carol").foreach { name =>
      val li  = org.scalajs.dom.document.createElement("li")
      val btn = org.scalajs.dom.document.createElement("button")
      btn.textContent = s"Delete $name"
      li.textContent  = name
      li.appendChild(btn)
      ul.appendChild(li)
    }
    ul

  test("within scopes queries to the given element") {
    val c     = mount(listFixture())
    val items = c.getAllByRole("listitem")
    assertEquals(items.length, 3)
    // scope to the first <li> — should find only "Delete Alice" button
    val firstItem = c.within(items.head)
    val btn       = firstItem.getByRole("button")
    assert(btn.textContent.contains("Alice"))
  }

  test("within queryByText returns None for text outside the scope") {
    val c     = mount(listFixture())
    val items = c.getAllByRole("listitem")
    val scope = c.within(items.head) // scoped to Alice's <li>
    assertEquals(scope.queryByText("Bob"), None)
  }

  test("within unmount is a no-op") {
    val c     = mount(listFixture())
    val items = c.getAllByRole("listitem")
    val scope = c.within(items.head)
    scope.unmount() // should not remove the element
    assert(c.exists("li"), "original list items should still exist")
    assert(scope.queryByRole("button").isDefined, "scoped query still works after no-op unmount")
  }

  // ── getByDisplayValue ────────────────────────────────────────────────────

  private def displayValueFixture(): org.scalajs.dom.Element =
    val wrapper = org.scalajs.dom.document.createElement("div")

    val input = org.scalajs.dom.document.createElement("input").asInstanceOf[org.scalajs.dom.html.Input]
    input.value = "Alice"
    wrapper.appendChild(input)

    val textarea = org.scalajs.dom.document.createElement("textarea").asInstanceOf[org.scalajs.dom.html.TextArea]
    textarea.value = "Hello world"
    wrapper.appendChild(textarea)

    val select = org.scalajs.dom.document.createElement("select").asInstanceOf[org.scalajs.dom.html.Select]
    Seq("opt1" -> "Option 1", "opt2" -> "Option 2").foreach {
      case (v, t) =>
        val opt = org.scalajs.dom.document.createElement("option").asInstanceOf[org.scalajs.dom.html.Option]
        opt.value       = v
        opt.textContent = t
        select.appendChild(opt)
    }
    select.value = "opt2"
    wrapper.appendChild(select)

    wrapper

  test("getByDisplayValue finds input by current value") {
    val c  = mount(displayValueFixture())
    val el = c.getByDisplayValue("Alice")
    assertEquals(el.tagName.toLowerCase, "input")
  }

  test("getByDisplayValue finds textarea by current value") {
    val c  = mount(displayValueFixture())
    val el = c.getByDisplayValue("Hello world")
    assertEquals(el.tagName.toLowerCase, "textarea")
  }

  test("getByDisplayValue finds select by selected option value") {
    val c  = mount(displayValueFixture())
    val el = c.getByDisplayValue("opt2")
    assertEquals(el.tagName.toLowerCase, "select")
  }

  test("queryByDisplayValue returns None when value not present") {
    val c = mount(displayValueFixture())
    assertEquals(c.queryByDisplayValue("nonexistent"), None)
  }

  test("findAllByDisplayValue with exact = false does substring match") {
    val c       = mount(displayValueFixture())
    val results = c.findAllByDisplayValue("Hello", exact = false)
    assertEquals(results.length, 1)
    assertEquals(results.head.tagName.toLowerCase, "textarea")
  }

  test("getByDisplayValue throws NoSuchElementException when not found") {
    val c = mount(displayValueFixture())
    intercept[NoSuchElementException] {
      c.getByDisplayValue("not there")
    }
  }

  // ── getByTitle ───────────────────────────────────────────────────────────

  /** Builds a DOM fragment with title-attribute elements and an SVG with a <title> child. */
  private def titleFixture(): org.scalajs.dom.Element =
    val wrapper = org.scalajs.dom.document.createElement("div")

    val btn = org.scalajs.dom.document.createElement("button")
    btn.setAttribute("title", "Close dialog")
    btn.textContent = "X"
    wrapper.appendChild(btn)

    val abbr = org.scalajs.dom.document.createElement("abbr")
    abbr.setAttribute("title", "HyperText Markup Language")
    abbr.textContent = "HTML"
    wrapper.appendChild(abbr)

    // SVG with <title> child element
    val svg      = org.scalajs.dom.document.createElementNS("http://www.w3.org/2000/svg", "svg")
    val svgTitle = org.scalajs.dom.document.createElementNS("http://www.w3.org/2000/svg", "title")
    svgTitle.textContent = "Sales chart"
    svg.appendChild(svgTitle)
    wrapper.appendChild(svg)

    // Nested SVG element with its own <title>
    val circle      = org.scalajs.dom.document.createElementNS("http://www.w3.org/2000/svg", "circle")
    val circleTitle = org.scalajs.dom.document.createElementNS("http://www.w3.org/2000/svg", "title")
    circleTitle.textContent = "Data point"
    circle.appendChild(circleTitle)
    svg.appendChild(circle)

    wrapper

  test("getByTitle finds element by title attribute") {
    val c  = mount(titleFixture())
    val el = c.getByTitle("Close dialog")
    assertEquals(el.tagName.toLowerCase, "button")
  }

  test("queryByTitle returns Some for existing title attribute") {
    val c = mount(titleFixture())
    assert(c.queryByTitle("HyperText Markup Language").isDefined)
  }

  test("queryByTitle returns None for missing title") {
    val c = mount(titleFixture())
    assertEquals(c.queryByTitle("nonexistent"), None)
  }

  test("findAllByTitle with exact = false does substring match") {
    val c       = mount(titleFixture())
    val results = c.findAllByTitle("dialog", exact = false)
    assertEquals(results.length, 1)
    assertEquals(results.head.tagName.toLowerCase, "button")
  }

  test("getByTitle finds SVG element via child <title> element") {
    val c  = mount(titleFixture())
    val el = c.getByTitle("Sales chart")
    assertEquals(el.tagName.toLowerCase, "svg")
  }

  test("getByTitle finds nested SVG element via child <title> element") {
    val c  = mount(titleFixture())
    val el = c.getByTitle("Data point")
    assertEquals(el.tagName.toLowerCase, "circle")
  }

  test("getByTitle throws NoSuchElementException when not found") {
    val c = mount(titleFixture())
    intercept[NoSuchElementException] {
      c.getByTitle("not present")
    }
  }

  test("getByTitle throws IllegalArgumentException when multiple match") {
    val c = mount(titleFixture())
    // "chart" matches "Sales chart" via SVG title; exact=false also picks up "Data point" containing "a"
    intercept[IllegalArgumentException] {
      c.getByTitle("a", exact = false)
    }
  }

  // ── getByAltText ─────────────────────────────────────────────────────────

  /** Builds a simple DOM fragment containing img / area / input[type=image] elements. */
  private def altTextFixture(): org.scalajs.dom.Element =
    val wrapper = org.scalajs.dom.document.createElement("div")

    val img = org.scalajs.dom.document.createElement("img")
    img.setAttribute("alt", "user avatar")
    wrapper.appendChild(img)

    val img2 = org.scalajs.dom.document.createElement("img")
    img2.setAttribute("alt", "logo icon")
    wrapper.appendChild(img2)

    val imgEmpty = org.scalajs.dom.document.createElement("img")
    imgEmpty.setAttribute("alt", "")
    wrapper.appendChild(imgEmpty)

    val area = org.scalajs.dom.document.createElement("area")
    area.setAttribute("alt", "clickable area")
    wrapper.appendChild(area)

    val inputImg = org.scalajs.dom.document.createElement("input")
    inputImg.setAttribute("type", "image")
    inputImg.setAttribute("alt", "submit image")
    wrapper.appendChild(inputImg)

    wrapper

  test("getByAltText finds img by exact alt text") {
    val c  = mount(altTextFixture())
    val el = c.getByAltText("user avatar")
    assertEquals(el.tagName.toLowerCase, "img")
  }

  test("queryByAltText returns Some for existing alt text") {
    val c = mount(altTextFixture())
    assert(c.queryByAltText("logo icon").isDefined)
  }

  test("queryByAltText returns None for missing alt text") {
    val c = mount(altTextFixture())
    assertEquals(c.queryByAltText("nonexistent"), None)
  }

  test("findAllByAltText with exact = false does substring match") {
    val c       = mount(altTextFixture())
    val results = c.findAllByAltText("icon", exact = false)
    assertEquals(results.length, 1)
    assertEquals(results.head.getAttribute("alt"), "logo icon")
  }

  test("getByAltText finds area element by alt text") {
    val c  = mount(altTextFixture())
    val el = c.getByAltText("clickable area")
    assertEquals(el.tagName.toLowerCase, "area")
  }

  test("getByAltText finds input[type=image] by alt text") {
    val c  = mount(altTextFixture())
    val el = c.getByAltText("submit image")
    assertEquals(el.tagName.toLowerCase, "input")
  }

  test("getByAltText throws NoSuchElementException when not found") {
    val c = mount(altTextFixture())
    intercept[NoSuchElementException] {
      c.getByAltText("not present")
    }
  }

  test("getByAltText throws IllegalArgumentException when multiple match") {
    val c = mount(altTextFixture())
    // Both "user avatar" and "logo icon" contain "a" — use substring match
    intercept[IllegalArgumentException] {
      c.getByAltText("a", exact = false)
    }
  }

  // ── userEvent ────────────────────────────────────────────────────────────

  private def userEventFixture(): org.scalajs.dom.Element =
    val wrapper = org.scalajs.dom.document.createElement("div")

    val input = org.scalajs.dom.document.createElement("input").asInstanceOf[org.scalajs.dom.html.Input]
    input.setAttribute("placeholder", "type here")
    wrapper.appendChild(input)

    val select = org.scalajs.dom.document.createElement("select").asInstanceOf[org.scalajs.dom.html.Select]
    Seq("a" -> "Alpha", "b" -> "Beta", "c" -> "Gamma").foreach {
      case (v, t) =>
        val opt = org.scalajs.dom.document.createElement("option").asInstanceOf[org.scalajs.dom.html.Option]
        opt.value       = v
        opt.textContent = t
        select.appendChild(opt)
    }
    wrapper.appendChild(select)

    val btn = org.scalajs.dom.document.createElement("button")
    btn.textContent = "Submit"
    wrapper.appendChild(btn)

    val disabledBtn = org.scalajs.dom.document.createElement("button")
    disabledBtn.setAttribute("disabled", "")
    disabledBtn.textContent = "Disabled"
    wrapper.appendChild(disabledBtn)

    wrapper

  test("userEvent.typeText fills input character by character") {
    val c = mount(userEventFixture())
    c.userEvent.typeText("input", "Hello")
    assertEquals(c.getByPlaceholderText("type here").asInstanceOf[org.scalajs.dom.html.Input].value, "Hello")
  }

  test("userEvent.typeText dispatches input event for each character") {
    val c     = mount(userEventFixture())
    var count = 0
    c.findAll("input").head.addEventListener("input", (_: org.scalajs.dom.Event) => count += 1)
    c.userEvent.typeText("input", "Hi")
    assertEquals(count, 2)
  }

  test("userEvent.clear empties input value and fires input event") {
    val c   = mount(userEventFixture())
    val inp = c.getByPlaceholderText("type here").asInstanceOf[org.scalajs.dom.html.Input]
    inp.value = "pre-filled"
    var fired = false
    inp.addEventListener("input", (_: org.scalajs.dom.Event) => fired = true)
    c.userEvent.clear("input")
    assertEquals(inp.value, "")
    assert(fired, "input event should have been fired")
  }

  test("userEvent.selectOption sets select value and fires change event") {
    val c     = mount(userEventFixture())
    var fired = false
    c.findAll("select").head.addEventListener("change", (_: org.scalajs.dom.Event) => fired = true)
    c.userEvent.selectOption("select", "b")
    assertEquals(c.findAll("select").head.asInstanceOf[org.scalajs.dom.html.Select].value, "b")
    assert(fired, "change event should have been fired")
  }

  test("userEvent.click fires full mouse event sequence") {
    val c      = mount(userEventFixture())
    var events = List.empty[String]
    val btn    = c.getByText("Submit")
    Seq("pointerdown", "mousedown", "pointerup", "mouseup", "click").foreach { t =>
      btn.addEventListener(t, (_: org.scalajs.dom.Event) => events = events :+ t)
    }
    c.userEvent.click("button")
    assertEquals(events, List("pointerdown", "mousedown", "pointerup", "mouseup", "click"))
  }

  test("userEvent.click does not fire events on disabled button") {
    val c     = mount(userEventFixture())
    var fired = false
    c.findAll("button").last.addEventListener("click", (_: org.scalajs.dom.Event) => fired = true)
    c.userEvent.click("button[disabled]")
    assert(!fired, "disabled button should not receive click events")
  }

  test("userEvent.keyboard Enter triggers click on focused button") {
    val c       = mount(userEventFixture())
    var clicked = false
    c.getByText("Submit").addEventListener("click", (_: org.scalajs.dom.Event) => clicked = true)
    c.userEvent.click("button") // focus the button first
    c.userEvent.keyboard("Enter")
    assert(clicked, "Enter should trigger click on focused button")
  }

  test("userEvent throws after unmount") {
    val c = mount(userEventFixture())
    c.unmount()
    intercept[IllegalStateException] {
      c.userEvent.click("button")
    }
  }

  // ── DevTools ─────────────────────────────────────────────────────────────

  test("DevTools.effectCount tracks recorded effects") {
    DevTools.resetEffectCount()
    DevTools.recordEffect()
    DevTools.recordEffect()
    assertEquals(DevTools.effectCount, 2)
    DevTools.resetEffectCount()
    assertEquals(DevTools.effectCount, 0)
  }

  test("DevTools.trackSignal registers signal descriptions") {
    DevTools.clearSignals()
    DevTools.trackSignal("count", "Var[Int](0)")
    DevTools.trackSignal("doubled", "Signal[Int](0)")
    DevTools.debugSignalGraph() // should not throw
    DevTools.clearSignals()
  }
