/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.animate

import org.scalajs.dom

import melt.runtime.transition.{ Easing, TransitionConfig, TransitionEngine }

/** Position snapshot passed to an [[AnimateFn]].
  *
  * @param from bounding rect captured **before** the DOM mutation
  * @param to   bounding rect of the element's **new** position (after mutation)
  */
case class AnimateInfo(from: dom.DOMRect, to: dom.DOMRect)

/** Configuration returned by an [[AnimateFn]].
  *
  * Mirrors [[melt.runtime.transition.TransitionConfig]] for animate-directive use.
  * Exactly one of `css` or `tick` should be provided.
  */
case class AnimateConfig(
  /** Delay before the animation starts, in milliseconds. */
  delay: Int = 0,
  /** Duration of the animation in milliseconds. */
  duration: Int = 300,
  /** Easing function applied during animation. */
  easing: Double => Double = Easing.cubicOut,
  /** CSS string generator `(t, u) => cssText` where `t ∈ [0,1]` moves from the
    * inverted (old) position toward the natural (new) position.
    */
  css: Option[(Double, Double) => String] = None,
  /** Per-frame callback `(t, u) => Unit`. */
  tick: Option[(Double, Double) => Unit] = None
)

/** Standard parameters accepted by built-in and custom animate functions. */
case class AnimateParams(
  /** Animation duration in milliseconds. */
  duration: Int = 300,
  /** Delay before the animation starts, in milliseconds. */
  delay: Int = 0,
  /** Easing function. */
  easing: Double => Double = Easing.cubicOut
)

object AnimateParams:
  /** Default instance used when no expression is provided in the template. */
  val default: AnimateParams = AnimateParams()

/** A custom animate function for the `animate:` directive.
  *
  * Implement this trait to create custom position-change animations:
  * {{{
  * val slideX: AnimateFn = (node, info, params) =>
  *   val dx = info.from.left - info.to.left
  *   AnimateConfig(
  *     duration = params.duration,
  *     css = Some { (t, u) => s"transform: translateX(${u * dx}px)" }
  *   )
  *
  * // In template:
  * // <div animate:slideX>...</div>
  * }}}
  */
trait AnimateFn:
  def apply(node: dom.Element, info: AnimateInfo, params: AnimateParams): AnimateConfig

object AnimateFn:
  /** Creates an [[AnimateFn]] from a lambda. */
  def apply(
    f: (dom.Element, AnimateInfo, AnimateParams) => AnimateConfig
  ): AnimateFn = (node, info, params) => f(node, info, params)

/** Runs [[AnimateConfig]]s produced by [[AnimateFn]]s.
  *
  * Delegates to [[TransitionEngine]] so the animate path shares the same
  * CSS-keyframe / `requestAnimationFrame` infrastructure as transitions.
  */
object AnimateEngine:

  /** Records the current bounding rect of every element in `els`.
    * Call this **before** the DOM mutation.
    */
  def snapshot(els: Iterable[dom.Element]): Map[dom.Element, dom.DOMRect] =
    els.map(el => el -> el.getBoundingClientRect()).toMap

  /** Executes the animation described by `config` on `el`.
    *
    * Runs with `intro = true` (t: 0 → 1, where 0 is the inverted old position and
    * 1 is the natural new position) and `emitEvents = false` because a position-change
    * animation is neither an enter nor a leave — dispatching transition lifecycle events
    * would be semantically incorrect and confusing to listeners.
    */
  def run(el: dom.Element, config: AnimateConfig): Unit =
    val transConfig = TransitionConfig(
      delay    = config.delay,
      duration = config.duration,
      easing   = config.easing,
      css      = config.css,
      tick     = config.tick
    )
    TransitionEngine.run(el, transConfig, intro = true, emitEvents = false)
