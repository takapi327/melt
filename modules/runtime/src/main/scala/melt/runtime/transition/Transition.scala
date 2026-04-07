/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.transition

import org.scalajs.dom

/** Parameters forwarded from a template transition directive expression.
  *
  * All fields have defaults that match Svelte's built-in transition defaults.
  *
  * {{{
  * // In template:  transition:fly={TransitionParams(y = 200, duration = 400)}
  * }}}
  */
case class TransitionParams(
  /** Animation duration in milliseconds. */
  duration: Int = 300,
  /** Delay before the animation starts, in milliseconds. */
  delay: Int = 0,
  /** Easing function mapping linear progress [0,1] to a curved value. */
  easing: Double => Double = Easing.cubicOut,
  /** Horizontal offset in pixels for `fly`. */
  x: Double = 0,
  /** Vertical offset in pixels for `fly`. */
  y: Double = 0,
  /** Starting/ending opacity for `fade` (unused by most transitions). */
  opacity: Double = 0,
  /** Starting scale factor for `scale` (0 = invisible, 1 = normal size). */
  start: Double = 0,
  /** Blur radius in pixels for `blur`. */
  amount: Double = 5,
  /** Slide direction for `slide`: `"y"` (default, height) or `"x"` (width). */
  axis: String = "y",
  /** Animation speed in px/ms for `draw`. Overrides `duration` when set. */
  speed: Option[Double] = None
)

object TransitionParams:
  /** Default parameters instance used when no expression is provided. */
  val default: TransitionParams = TransitionParams()

/** Configuration returned by a [[Transition]] function.
  *
  * Exactly one of `css` or `tick` should be provided; if neither is given the
  * transition is a no-op.
  */
case class TransitionConfig(
  /** Delay before the animation starts, in milliseconds. */
  delay: Int = 0,
  /** Duration of the animation in milliseconds. */
  duration: Int = 300,
  /** Easing function applied during animation. */
  easing: Double => Double = Easing.cubicOut,
  /** CSS string generator `(t, u) => cssText` where `t ∈ [0,1]` is the
    * "presence" value and `u = 1 - t`.
    * Used for CSS `@keyframes`-based animations (most built-ins).
    */
  css: Option[(Double, Double) => String] = None,
  /** Per-frame callback `(t, u) => Unit`.
    * Used for imperative animations that need DOM measurement at runtime.
    */
  tick: Option[(Double, Double) => Unit] = None
)

/** Direction in which a transition is playing. */
enum Direction:
  /** Element is entering the DOM. */
  case In
  /** Element is leaving the DOM. */
  case Out
  /** Both enter and leave (used for `transition:` directive). */
  case Both

/** A transition function that returns [[TransitionConfig]] for a DOM element.
  *
  * Implement this trait to create custom transitions:
  * {{{
  * val typewriter: Transition = (node, params, direction) =>
  *   val text = node.textContent
  *   TransitionConfig(
  *     duration = text.length * 50,
  *     tick = Some { (t, _) =>
  *       node.textContent = text.take((text.length * t).toInt)
  *     }
  *   )
  * }}}
  */
trait Transition:
  def apply(
    node:      dom.Element,
    params:    TransitionParams,
    direction: Direction
  ): TransitionConfig

object Transition:
  /** Creates a [[Transition]] from a lambda. */
  def apply(
    f: (dom.Element, TransitionParams, Direction) => TransitionConfig
  ): Transition =
    (node, params, direction) => f(node, params, direction)
