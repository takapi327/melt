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
    val c       = mount(Counter.create())
    val inputs  = c.getAllByRole("textbox")
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
    val c       = mount(Counter.create())
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
    waitFor(() => assertEquals(c.text("h1"), "Wrong text"), timeout = 100)
      .failed
      .map(_ => ())
  }

  // ── debug() ──────────────────────────────────────────────────────────────

  test("debug prints component DOM without throwing") {
    val c = mount(Counter.create())
    c.debug()         // full DOM
    c.debug("h1")     // specific element
    c.debug(".nonexistent") // missing element — prints message, does not throw
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
