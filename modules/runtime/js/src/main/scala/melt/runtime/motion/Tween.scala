/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.motion

import scala.scalajs.js

import org.scalajs.dom

import melt.runtime.transition.Easing

/** Interpolates a numeric value toward a target using an easing function.
  *
  * Calling [[set]] starts a `requestAnimationFrame` loop that updates [[current]]
  * each frame and notifies [[subscribe]] listeners until the target is reached.
  *
  * {{{
  * val progress = Tween(0.0, duration = 1000)
  * progress.subscribe(v => progressEl.style.width = s"${v}%")
  * progress.set(100.0)   // animates 0 → 100 over 1 second
  * }}}
  */
class Tween(
  initial:  Double,
  duration: Int              = 400,
  easing:   Double => Double = Easing.cubicOut
):
  private var _current: Double = initial
  private var _target:  Double = initial
  private var _rafId:   Int    = 0
  private val _listeners = scala.collection.mutable.ListBuffer.empty[Double => Unit]

  /** The current interpolated value. Updated each animation frame. */
  def current: Double = _current

  /** Animates [[current]] toward `target` over `dur` milliseconds. */
  def set(target: Double, dur: Int = duration): Unit =
    _target = target
    val from = _current

    if _rafId != 0 then dom.window.cancelAnimationFrame(_rafId)

    // startTime is captured from the first RAF callback so it uses the same
    // DOMHighResTimeStamp origin as subsequent frames (NOT js.Date.now()).
    def loop(startTime: Double)(now: Double): Unit =
      val elapsed  = now - startTime
      val progress = math.min(elapsed / dur, 1.0)
      _current = from + (target - from) * easing(progress)
      _notify(_current)
      if progress < 1.0 then _rafId = dom.window.requestAnimationFrame(loop(startTime) _).toInt
      else
        _current = target
        _notify(_current)
        _rafId = 0

    _rafId = dom.window.requestAnimationFrame((now: Double) => loop(now)(now)).toInt

  /** Immediately jumps to `value` without animation. */
  def jump(value: Double): Unit =
    if _rafId != 0 then
      dom.window.cancelAnimationFrame(_rafId)
      _rafId = 0
    _current = value
    _target  = value
    _notify(value)

  /** Subscribes to value changes.  The callback is called immediately with
    * the current value, then each frame while animating.
    * Returns an unsubscribe function.
    */
  def subscribe(fn: Double => Unit): () => Unit =
    _listeners += fn
    fn(_current)
    val unsub: () => Unit = () => { _listeners -= fn; () }
    melt.runtime.Cleanup.register(unsub)
    unsub

  private def _notify(value: Double): Unit =
    _listeners.foreach(_(value))

object Tween:
  /** Convenience constructor. */
  def apply(
    initial:  Double,
    duration: Int = 400,
    easing:   Double => Double = Easing.cubicOut
  ): Tween = new Tween(initial, duration, easing)
