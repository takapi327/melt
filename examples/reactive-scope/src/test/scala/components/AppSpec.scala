/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package components

import melt.runtime.*
import melt.testkit.*

class AppSpec extends MeltSuite:

  // ── Initial render ───────────────────────────────────────────────────────

  test("renders heading") {
    val c = mount(App())
    assertEquals(c.text("h1"), "ReactiveScope")
  }

  test("renders all 4 sections") {
    val c        = mount(App())
    val sections = c.findAll("section")
    assertEquals(sections.length, 4)
  }

  test("renders Start and Stop buttons for panels 1-3") {
    val c       = mount(App())
    val buttons = c.findAll("button")
    // 3 panels × 2 buttons = 6
    assertEquals(buttons.length, 6)
  }

  // ── Panel 1: make (ticker1) ──────────────────────────────────────────────

  test("panel 1 Start button enables the scope") {
    val c = mount(App())
    // Initially stopped
    assert(c.queryByText("stopped").isDefined)
    // Click Start (first button)
    c.findAll("button")
      .head
      .dispatchEvent(
        new org.scalajs.dom.MouseEvent("click", new org.scalajs.dom.MouseEventInit { bubbles = true })
      )
    assert(c.queryByText("running").isDefined)
    c.unmount()
  }

  test("panel 1 Stop button disables the scope") {
    val c    = mount(App())
    val btns = c.findAll("button")
    // Start
    btns.head.dispatchEvent(
      new org.scalajs.dom.MouseEvent("click", new org.scalajs.dom.MouseEventInit { bubbles = true })
    )
    assert(c.queryByText("running").isDefined)
    // Stop
    btns(1).dispatchEvent(
      new org.scalajs.dom.MouseEvent("click", new org.scalajs.dom.MouseEventInit { bubbles = true })
    )
    assert(c.queryByText("stopped").isDefined)
    c.unmount()
  }

  test("panel 1 Start button is disabled while running") {
    val c    = mount(App())
    val btns = c.findAll("button")
    btns.head.dispatchEvent(
      new org.scalajs.dom.MouseEvent("click", new org.scalajs.dom.MouseEventInit { bubbles = true })
    )
    assert(btns.head.hasAttribute("disabled"), "Start should be disabled while running")
    c.unmount()
  }

  test("panel 1 Stop button is disabled while stopped") {
    val c    = mount(App())
    val stop = c.findAll("button")(1)
    assert(stop.hasAttribute("disabled"), "Stop should be disabled when not running")
  }

  // ── Panel 2: resource (keydown listener) ─────────────────────────────────

  test("panel 2 shows 'listening' badge after Start") {
    val c    = mount(App())
    val btns = c.findAll("button")
    // Panel 2 Start is index 2
    btns(2).dispatchEvent(
      new org.scalajs.dom.MouseEvent("click", new org.scalajs.dom.MouseEventInit { bubbles = true })
    )
    assert(c.queryByText("listening").isDefined)
    c.unmount()
  }

  test("panel 2 shows 'stopped' after Stop") {
    val c    = mount(App())
    val btns = c.findAll("button")
    btns(2).dispatchEvent(
      new org.scalajs.dom.MouseEvent("click", new org.scalajs.dom.MouseEventInit { bubbles = true })
    )
    btns(3).dispatchEvent(
      new org.scalajs.dom.MouseEvent("click", new org.scalajs.dom.MouseEventInit { bubbles = true })
    )
    // After stop, "listening" badge should be gone
    assert(c.queryByText("listening").isEmpty)
    c.unmount()
  }

  test("panel 2 displays last pressed key while listening") {
    val c    = mount(App())
    val btns = c.findAll("button")
    btns(2).dispatchEvent(
      new org.scalajs.dom.MouseEvent("click", new org.scalajs.dom.MouseEventInit { bubbles = true })
    )
    // Simulate keydown on window
    org.scalajs.dom.window.dispatchEvent(
      new org.scalajs.dom.KeyboardEvent(
        "keydown",
        new org.scalajs.dom.KeyboardEventInit { key = "a"; bubbles = true }
      )
    )
    assert(c.queryByText("a", exact = false).isDefined, "last key should be displayed")
    c.unmount()
  }

  test("panel 2 stops receiving keys after Stop") {
    val c    = mount(App())
    val btns = c.findAll("button")
    // Start
    btns(2).dispatchEvent(
      new org.scalajs.dom.MouseEvent("click", new org.scalajs.dom.MouseEventInit { bubbles = true })
    )
    // Stop — removes listener
    btns(3).dispatchEvent(
      new org.scalajs.dom.MouseEvent("click", new org.scalajs.dom.MouseEventInit { bubbles = true })
    )
    // Keydown after stop should not update display
    org.scalajs.dom.window.dispatchEvent(
      new org.scalajs.dom.KeyboardEvent(
        "keydown",
        new org.scalajs.dom.KeyboardEventInit { key = "z"; bubbles = true }
      )
    )
    // lastKey should be reset to "—"
    assert(c.queryByText("z").isEmpty, "key should not be captured after stop")
    c.unmount()
  }

  // ── Panel 3: for-comprehension composition ────────────────────────────────

  test("panel 3 Start activates composed scope") {
    val c    = mount(App())
    val btns = c.findAll("button")
    btns(4).dispatchEvent(
      new org.scalajs.dom.MouseEvent("click", new org.scalajs.dom.MouseEventInit { bubbles = true })
    )
    // Should have two "running" badges (panel 1 if started + panel 3)
    // or at least one "running"
    assert(c.findAllByText("running", exact = false).nonEmpty)
    c.unmount()
  }

  test("panel 3 Stop releases all composed resources") {
    val c    = mount(App())
    val btns = c.findAll("button")
    // Start panel 3
    btns(4).dispatchEvent(
      new org.scalajs.dom.MouseEvent("click", new org.scalajs.dom.MouseEventInit { bubbles = true })
    )
    // Stop panel 3
    btns(5).dispatchEvent(
      new org.scalajs.dom.MouseEvent("click", new org.scalajs.dom.MouseEventInit { bubbles = true })
    )
    // After stop, ticker3 should be reset to "0 s"
    assert(c.queryByText("0 s", exact = false).isDefined)
    c.unmount()
  }

  // ── Panel 4: onMount integration ─────────────────────────────────────────

  test("panel 4 shows 'auto' badge") {
    val c = mount(App())
    assert(c.queryByText("auto").isDefined)
  }

  test("panel 4 shows elapsed seconds display") {
    val c = mount(App())
    assert(c.queryByText("s elapsed", exact = false).isDefined)
  }

  // ── Unmount cleans up all scopes ─────────────────────────────────────────

  test("unmount removes component") {
    val c = mount(App())
    assert(c.exists("h1"))
    c.unmount()
    assert(!c.exists("h1"))
  }
