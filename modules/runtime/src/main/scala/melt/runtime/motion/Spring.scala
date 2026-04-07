/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.motion

import scala.scalajs.js

import org.scalajs.dom

/** Animates a numeric value using spring physics.
  *
  * Unlike [[Tween]], a Spring may overshoot the target (producing a natural
  * bounce) and the duration is not fixed — it runs until the system settles.
  *
  * {{{
  * val x = Spring(0.0, stiffness = 0.3, damping = 0.6)
  * x.subscribe(v => el.style.transform = s"translateX(${v}px)")
  * x.set(200.0)   // spring-bounces toward 200
  * }}}
  *
  * @param initial   Initial value.
  * @param stiffness Spring constant (0–1). Higher = snappier.
  * @param damping   Damping ratio (0–1). Higher = less bounce, settles faster.
  * @param precision Movement threshold below which the spring is considered settled.
  */
class Spring(
  initial:   Double,
  stiffness: Double = 0.15,
  damping:   Double = 0.8,
  precision: Double = 0.01
):
  private var _current:  Double = initial
  private var _target:   Double = initial
  private var _velocity: Double = 0.0
  private var _lastTime: Double = 0.0
  private var _rafId:    Int    = 0
  private val _listeners = scala.collection.mutable.ListBuffer.empty[Double => Unit]

  /** The current spring value. Updated each animation frame. */
  def current: Double = _current

  /** Sets a new target and (re)starts the spring animation. */
  def set(target: Double): Unit =
    _target = target

    if _rafId != 0 then dom.window.cancelAnimationFrame(_rafId)

    // _lastTime is initialised from the first RAF callback so it uses the same
    // DOMHighResTimeStamp origin as subsequent frames (NOT js.Date.now()).
    def loop(now: Double): Unit =
      // Cap dt to 64ms to prevent large jumps after tab switches
      val dt    = math.min((now - _lastTime) / 1000.0, 0.064)
      _lastTime = now

      // Euler integration of spring differential equation
      val force  = (_target - _current) * stiffness
      _velocity  = (_velocity + force) * (1.0 - damping)
      _current  += _velocity

      _notify(_current)

      val settled = math.abs(_velocity) < precision && math.abs(_target - _current) < precision
      if !settled then
        _rafId = dom.window.requestAnimationFrame(loop _).toInt
      else
        _current  = _target
        _velocity = 0.0
        _notify(_current)
        _rafId = 0

    _rafId = dom.window.requestAnimationFrame { (now: Double) =>
      _lastTime = now   // initialise with the first RAF timestamp
      loop(now)
    }.toInt

  /** Immediately sets the value to `value` without animation.
    * Resets velocity to zero.
    */
  def jump(value: Double): Unit =
    if _rafId != 0 then
      dom.window.cancelAnimationFrame(_rafId)
      _rafId = 0
    _current  = value
    _target   = value
    _velocity = 0.0
    _notify(value)

  /** Subscribes to value changes.  The callback is called immediately with the
    * current value, then each frame while the spring is active.
    * Returns an unsubscribe function.
    */
  def subscribe(fn: Double => Unit): () => Unit =
    _listeners += fn
    fn(_current)
    () => { _listeners -= fn; () }

  private def _notify(value: Double): Unit =
    _listeners.foreach(_(value))

object Spring:
  /** Convenience constructor. */
  def apply(
    initial:   Double,
    stiffness: Double = 0.15,
    damping:   Double = 0.8,
    precision: Double = 0.01
  ): Spring = new Spring(initial, stiffness, damping, precision)
