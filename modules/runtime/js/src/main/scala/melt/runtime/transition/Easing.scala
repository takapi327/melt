/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.transition

/** Standard easing functions for use with [[TransitionConfig]].
  *
  * Each function maps a linear progress value `t ∈ [0, 1]` to a curved value
  * in the same range, controlling the perceived speed of an animation.
  *
  * {{{
  * TransitionParams(easing = Easing.cubicInOut)
  * }}}
  */
object Easing:

  /** Constant speed — no easing. */
  def linear(t: Double): Double = t

  /** Accelerates from zero. */
  def cubicIn(t: Double): Double = t * t * t

  /** Decelerates to zero. Default for most built-in transitions. */
  def cubicOut(t: Double): Double =
    val f = t - 1; f * f * f + 1

  /** Accelerates then decelerates. */
  def cubicInOut(t: Double): Double =
    if t < 0.5 then 4 * t * t * t
    else
      val f = 2 * t - 2; 0.5 * f * f * f + 1

  /** Slow start, then fast. */
  def quadIn(t: Double): Double = t * t

  /** Fast start, then slow. */
  def quadOut(t: Double): Double = 1 - (1 - t) * (1 - t)

  /** Smooth start and end. */
  def quadInOut(t: Double): Double =
    if t < 0.5 then 2 * t * t
    else 1 - math.pow(-2 * t + 2, 2) / 2

  /** Aggressive acceleration. */
  def quartIn(t: Double): Double = t * t * t * t

  /** Aggressive deceleration. */
  def quartOut(t: Double): Double = 1 - math.pow(t - 1, 4)

  /** Aggressive smooth. */
  def quartInOut(t: Double): Double =
    if t < 0.5 then 8 * t * t * t * t
    else 1 - math.pow(-2 * t + 2, 4) / 2

  /** Gentle sine start. */
  def sineIn(t: Double): Double = 1 - math.cos(t * math.Pi / 2)

  /** Gentle sine end. */
  def sineOut(t: Double): Double = math.sin(t * math.Pi / 2)

  /** Gentle sine start and end. */
  def sineInOut(t: Double): Double = -(math.cos(math.Pi * t) - 1) / 2

  /** Overshoots slightly, then snaps back. */
  def backIn(t: Double): Double =
    val c1 = 1.70158
    val c3 = c1 + 1
    c3 * t * t * t - c1 * t * t

  /** Snaps past the target, then settles. */
  def backOut(t: Double): Double =
    val c1 = 1.70158
    val c3 = c1 + 1
    1 + c3 * math.pow(t - 1, 3) + c1 * math.pow(t - 1, 2)

  /** Elastic spring effect at the end. */
  def elasticOut(t: Double): Double =
    if t == 0 || t == 1 then t
    else math.pow(2, -10 * t) * math.sin((t * 10 - 0.75) * (2 * math.Pi / 3)) + 1

  /** Elastic spring effect at the start. */
  def elasticIn(t: Double): Double =
    if t == 0 || t == 1 then t
    else -(math.pow(2, 10 * t - 10) * math.sin((t * 10 - 10.75) * (2 * math.Pi / 3)))

  /** Bounces at the end (like a dropped ball). */
  def bounceOut(t: Double): Double =
    val n1 = 7.5625
    val d1 = 2.75
    if t < 1.0 / d1 then n1 * t * t
    else if t < 2.0 / d1 then
      val t2 = t - 1.5 / d1; n1 * t2 * t2 + 0.75
    else if t < 2.5 / d1 then
      val t2 = t - 2.25 / d1; n1 * t2 * t2 + 0.9375
    else
      val t2 = t - 2.625 / d1; n1 * t2 * t2 + 0.984375

  /** Circular ease in — starts slow. */
  def circIn(t: Double): Double = 1 - math.sqrt(1 - math.pow(t, 2))

  /** Circular ease out — ends slow. */
  def circOut(t: Double): Double = math.sqrt(1 - math.pow(t - 1, 2))

  /** Circular ease in and out — slow start and end. */
  def circInOut(t: Double): Double =
    if t < 0.5 then (1 - math.sqrt(1 - math.pow(2 * t, 2))) / 2
    else (math.sqrt(1 - math.pow(-2 * t + 2, 2)) + 1) / 2

  /** Back easing in and out — overshoots at both ends. */
  def backInOut(t: Double): Double =
    val c1 = 1.70158
    val c2 = c1 * 1.525
    if t < 0.5 then (math.pow(2 * t, 2) * ((c2 + 1) * 2 * t - c2)) / 2
    else (math.pow(2 * t - 2, 2) * ((c2 + 1) * (2 * t - 2) + c2) + 2) / 2

  /** Exponential acceleration — slow start then very fast. */
  def expoIn(t: Double): Double =
    if t == 0 then 0 else math.pow(2, 10 * t - 10)

  /** Exponential deceleration — very fast start then slow. */
  def expoOut(t: Double): Double =
    if t == 1 then 1 else 1 - math.pow(2, -10 * t)

  /** Exponential smooth — fast acceleration and deceleration. */
  def expoInOut(t: Double): Double =
    if t == 0 then 0
    else if t == 1 then 1
    else if t < 0.5 then math.pow(2, 20 * t - 10) / 2
    else (2 - math.pow(2, -20 * t + 10)) / 2

  /** Quintic acceleration. */
  def quintIn(t: Double): Double = t * t * t * t * t

  /** Quintic deceleration. */
  def quintOut(t: Double): Double = 1 - math.pow(t - 1, 5)

  /** Quintic smooth start and end. */
  def quintInOut(t: Double): Double =
    if t < 0.5 then 16 * t * t * t * t * t
    else 1 - math.pow(-2 * t + 2, 5) / 2

  /** Elastic spring effect at the start and end. */
  def elasticInOut(t: Double): Double =
    val c5 = (2 * math.Pi) / 4.5
    if t == 0 then 0
    else if t == 1 then 1
    else if t < 0.5 then -(math.pow(2, 20 * t - 10) * math.sin((20 * t - 11.125) * c5)) / 2
    else (math.pow(2, -20 * t + 10) * math.sin((20 * t - 11.125) * c5)) / 2 + 1

  /** Bounce at the start. */
  def bounceIn(t: Double): Double = 1 - bounceOut(1 - t)

  /** Bounce at both start and end. */
  def bounceInOut(t: Double): Double =
    if t < 0.5 then (1 - bounceOut(1 - 2 * t)) / 2
    else (1 + bounceOut(2 * t - 1)) / 2
