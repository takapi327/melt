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
