/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.testkit

import scala.scalajs.js

/** Debugging utilities for Melt components.
  *
  * These helpers are intended for use inside [[MeltSuite]] tests or the browser
  * console during development. They output to the browser / jsdom console.
  *
  * {{{
  * class MySpec extends MeltSuite:
  *   test("debug signal graph") {
  *     val c = mount(Counter.create(Counter.Props("Test", 0)))
  *     DevTools.debugSignalGraph()    // prints reactive dependency info
  *     DevTools.debugEffectCount()   // prints how many effects are tracked
  *   }
  * }}}
  */
object DevTools:

  /** Prints the reactive signal dependency graph to the browser / jsdom console.
    *
    * In the current implementation this outputs a summary of all known `Var`
    * and `Signal` instances that have been registered via [[trackSignal]].
    * For a richer graph, instrument your component scripts with [[trackSignal]].
    */
  def debugSignalGraph(): Unit =
    js.Dynamic.global.console.group("[melt devtools] Signal graph")
    if _signals.isEmpty then
      js.Dynamic.global.console.log("No signals registered. Call DevTools.trackSignal(label, signal) to register.")
    else
      _signals.foreach { case (label, info) =>
        js.Dynamic.global.console.log(s"  $label: $info")
      }
    js.Dynamic.global.console.groupEnd()

  /** Prints the total number of reactive effects that have been executed so far
    * (since the last [[resetEffectCount]] call) to the browser / jsdom console.
    */
  def debugEffectCount(): Unit =
    js.Dynamic.global.console.log(s"[melt devtools] Effects executed: $_effectCount")

  /** Resets the effect execution counter back to zero.
    *
    * Call this at the start of a test to measure only the effects triggered
    * during that test.
    */
  def resetEffectCount(): Unit =
    _effectCount = 0

  /** Returns the current effect execution count without printing it. */
  def effectCount: Int = _effectCount

  /** Increments the effect counter by one.
    *
    * Instrument a reactive effect in your component or test to track executions:
    * {{{
    * effect(myVar) { v =>
    *   DevTools.recordEffect()
    *   doSomething(v)
    * }
    * }}}
    */
  def recordEffect(): Unit =
    _effectCount += 1

  /** Registers a labelled signal description for [[debugSignalGraph]] output.
    *
    * {{{
    * val count = Var(0)
    * DevTools.trackSignal("count", count.now().toString)
    * }}}
    */
  def trackSignal(label: String, description: String): Unit =
    _signals(label) = description

  /** Clears all registered signal descriptions. */
  def clearSignals(): Unit =
    _signals.clear()

  // ── Internal state ────────────────────────────────────────────────────────

  private var _effectCount: Int = 0

  import scala.collection.mutable
  private val _signals: mutable.LinkedHashMap[String, String] =
    mutable.LinkedHashMap.empty
