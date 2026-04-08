/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import scala.scalajs.js

import org.scalajs.dom

import melt.runtime.transition.{ TransitionConfig, TransitionEngine }

/** Tests for the four transition lifecycle events dispatched by [[TransitionEngine]]:
  *
  *   - `introstart` — fired immediately when an enter animation begins
  *   - `introend`   — fired when an enter animation completes
  *   - `outrostart` — fired immediately when a leave animation begins
  *   - `outroend`   — fired when a leave animation completes
  *
  * All tests use a ''synchronous config'': no `css`, no `tick`, `delay = 0`.
  * In that code path [[TransitionEngine.run]] calls `finish()` synchronously
  * (no RAF / CSS keyframe / `setTimeout` involved), so events fire in the
  * same call stack and can be asserted immediately.
  *
  * @see [[TransitionEngine]]
  * @see docs/transition-events-design.md
  */
class TransitionEventSpec extends munit.FunSuite:

  /** Install a minimal `window.matchMedia` polyfill before [[TransitionEngine]] is
    * first accessed.
    *
    * jsdom does not implement `window.matchMedia`, but [[TransitionEngine]] reads
    * `dom.window.matchMedia("(prefers-reduced-motion: reduce)")` at object
    * initialisation time.  Without this polyfill the first test that touches
    * `TransitionEngine` would throw `TypeError: window.matchMedia is not a function`.
    *
    * The polyfill returns a stub `MediaQueryList` with `matches = false`
    * (no reduced-motion preference) and no-op listener methods.
    */
  locally {
    js.Dynamic.global.window.matchMedia = js.Any.fromFunction1 { (_: String) =>
      js.Dynamic.literal(
        matches             = false,
        media               = "",
        onchange            = null,
        addListener         = js.Any.fromFunction1((_: js.Any) => ()),
        removeListener      = js.Any.fromFunction1((_: js.Any) => ()),
        addEventListener    = js.Any.fromFunction2((_: String, _: js.Any) => ()),
        removeEventListener = js.Any.fromFunction2((_: String, _: js.Any) => ()),
        dispatchEvent       = js.Any.fromFunction1((_: js.Any) => true)
      )
    }
  }

  /** Minimal config with no css, no tick, delay=0 — finish() called synchronously. */
  private val syncConfig = TransitionConfig()

  /** Create a fresh element and attach listeners for the requested event names.
    * Returns the event log and the element.
    */
  private def withListeners(
    names: String*
  ): (scala.collection.mutable.ListBuffer[String], dom.Element) =
    val el  = dom.document.createElement("div")
    val log = scala.collection.mutable.ListBuffer.empty[String]
    names.foreach { n =>
      el.addEventListener(n, (_: dom.Event) => { log += n; () })
    }
    (log, el)

  // ── intro (enter) events ──────────────────────────────────────────────────

  test("intro fires introstart then introend") {
    val (events, el) = withListeners("introstart", "introend")
    TransitionEngine.run(el, syncConfig, intro = true)
    assertEquals(events.toList, List("introstart", "introend"))
  }

  test("intro does not fire outro events") {
    val (events, el) = withListeners("introstart", "introend", "outrostart", "outroend")
    TransitionEngine.run(el, syncConfig, intro = true)
    assertEquals(events.toList, List("introstart", "introend"))
  }

  // ── outro (leave) events ──────────────────────────────────────────────────

  test("outro fires outrostart then outroend") {
    val (events, el) = withListeners("outrostart", "outroend")
    TransitionEngine.run(el, syncConfig, intro = false)
    assertEquals(events.toList, List("outrostart", "outroend"))
  }

  test("outro does not fire intro events") {
    val (events, el) = withListeners("introstart", "introend", "outrostart", "outroend")
    TransitionEngine.run(el, syncConfig, intro = false)
    assertEquals(events.toList, List("outrostart", "outroend"))
  }

  // ── event bubbling ────────────────────────────────────────────────────────

  test("introstart and introend bubble to parent") {
    val parent = dom.document.createElement("div")
    val child  = dom.document.createElement("div")
    parent.appendChild(child)

    val bubbled = scala.collection.mutable.ListBuffer.empty[String]
    parent.addEventListener("introstart", (_: dom.Event) => { bubbled += "introstart"; () })
    parent.addEventListener("introend",   (_: dom.Event) => { bubbled += "introend";   () })

    TransitionEngine.run(child, syncConfig, intro = true)
    assertEquals(bubbled.toList, List("introstart", "introend"))
  }

  test("outrostart and outroend bubble to parent") {
    val parent = dom.document.createElement("div")
    val child  = dom.document.createElement("div")
    parent.appendChild(child)

    val bubbled = scala.collection.mutable.ListBuffer.empty[String]
    parent.addEventListener("outrostart", (_: dom.Event) => { bubbled += "outrostart"; () })
    parent.addEventListener("outroend",   (_: dom.Event) => { bubbled += "outroend";   () })

    TransitionEngine.run(child, syncConfig, intro = false)
    assertEquals(bubbled.toList, List("outrostart", "outroend"))
  }

  // ── emitEvents = false ────────────────────────────────────────────────────

  test("emitEvents=false suppresses all four events on intro") {
    val (events, el) = withListeners("introstart", "introend", "outrostart", "outroend")
    TransitionEngine.run(el, syncConfig, intro = true, emitEvents = false)
    assertEquals(events.toList, List())
  }

  test("emitEvents=false suppresses all four events on outro") {
    val (events, el) = withListeners("introstart", "introend", "outrostart", "outroend")
    TransitionEngine.run(el, syncConfig, intro = false, emitEvents = false)
    assertEquals(events.toList, List())
  }

  // ── onDone callback ordering ──────────────────────────────────────────────

  test("onDone fires after introend") {
    val el    = dom.document.createElement("div")
    val order = scala.collection.mutable.ListBuffer.empty[String]
    el.addEventListener("introend", (_: dom.Event) => { order += "introend"; () })
    TransitionEngine.run(el, syncConfig, intro = true, onDone = () => { order += "onDone"; () })
    assertEquals(order.toList, List("introend", "onDone"))
  }

  test("onDone fires after outroend") {
    val el    = dom.document.createElement("div")
    val order = scala.collection.mutable.ListBuffer.empty[String]
    el.addEventListener("outroend", (_: dom.Event) => { order += "outroend"; () })
    TransitionEngine.run(el, syncConfig, intro = false, onDone = () => { order += "onDone"; () })
    assertEquals(order.toList, List("outroend", "onDone"))
  }

  test("onDone fires even when emitEvents=false") {
    val el      = dom.document.createElement("div")
    var called  = false
    TransitionEngine.run(el, syncConfig, intro = true, emitEvents = false, onDone = () => called = true)
    assert(called)
  }

  // ── sequential runs ───────────────────────────────────────────────────────

  test("running intro then outro on same element fires all four events in order") {
    val (events, el) = withListeners("introstart", "introend", "outrostart", "outroend")
    TransitionEngine.run(el, syncConfig, intro = true)
    TransitionEngine.run(el, syncConfig, intro = false)
    assertEquals(events.toList, List("introstart", "introend", "outrostart", "outroend"))
  }
