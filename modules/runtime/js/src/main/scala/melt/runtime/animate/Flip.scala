/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.animate

import scala.scalajs.js

import org.scalajs.dom

import melt.runtime.transition.Easing

/** FLIP (First–Last–Invert–Play) animation for keyed list reordering.
  *
  * `Flip` implements [[AnimateFn]] so it can be used via the generic
  * `animate:flip` directive.  `Bind.each` calls [[AnimateEngine.snapshot]]
  * before each DOM mutation and [[AnimateEngine.run]] with the result of
  * `Flip(node, info, params)` after the mutation.
  *
  * {{{
  * // In template:
  * // <div animate:flip>...</div>
  * // <div animate:flip={AnimateParams(duration = 500)}>...</div>
  * }}}
  */
object Flip extends AnimateFn:

  /** Implements [[AnimateFn]]: computes translate/scale CSS from old/new positions. */
  def apply(node: dom.Element, info: AnimateInfo, params: AnimateParams): AnimateConfig =
    val dx = info.from.left - info.to.left
    val dy = info.from.top - info.to.top
    AnimateConfig(
      delay    = params.delay,
      duration = params.duration,
      easing   = params.easing,
      css      = Some { (t, u) =>
        s"transform: translate(${ u * dx }px, ${ u * dy }px)"
      }
    )

  /** A captured bounding-box snapshot of a DOM element. */
  case class Rect(top: Double, left: Double, width: Double, height: Double)

  /** Records the current bounding rect of every element in `els`.
    *
    * Call this **before** the DOM mutation.
    *
    * @param els the elements whose positions should be captured
    * @return a map from element to its [[Rect]] at the time of the call
    */
  def snapshot(els: Iterable[dom.Element]): Map[dom.Element, Rect] =
    els.map { el =>
      val r = el.getBoundingClientRect()
      el -> Rect(r.top, r.left, r.width, r.height)
    }.toMap

  /** Animates each element in `els` from its old position (in `before`) to its
    * current (post-mutation) position using CSS `transform` and `transition`.
    *
    * Elements that are not in `before` (newly added) are skipped.
    * Elements with zero delta are also skipped for efficiency.
    *
    * @param els      the elements to animate (after DOM mutation)
    * @param before   snapshot returned by [[snapshot]] before the mutation
    * @param duration animation duration in milliseconds
    * @param easing   easing function (used to select a CSS timing function)
    */
  def play(
    els:      Iterable[dom.Element],
    before:   Map[dom.Element, Rect],
    duration: Int = 300,
    easing:   Double => Double = Easing.cubicOut
  ): Unit =
    val timingFn = cssTiming(easing)
    els.foreach { el =>
      before.get(el).foreach { oldRect =>
        val newRect = el.getBoundingClientRect()
        val dx      = oldRect.left - newRect.left
        val dy      = oldRect.top - newRect.top
        if dx != 0.0 || dy != 0.0 then
          val htmlEl = el.asInstanceOf[dom.html.Element]
          // Invert: apply inverse transform so the element appears at the old position
          htmlEl.style.setProperty("transform", s"translate(${ dx }px, ${ dy }px)")
          htmlEl.style.setProperty("transition", "none")
          // Force a reflow so the browser registers the start state
          val _ = htmlEl.offsetHeight
          // Play: animate to the natural (new) position
          htmlEl.style.setProperty("transition", s"transform ${ duration }ms $timingFn")
          htmlEl.style.setProperty("transform", "")
          // Clean up transition property after animation.
          // listenerRef holds the handler so it can remove itself; it is assigned
          // after the lambda is constructed to allow the self-reference.
          var done = false
          var listenerRef: Option[js.Function1[dom.Event, Unit]] = None
          val listener:    js.Function1[dom.Event, Unit]         = (_: dom.Event) =>
            if !done then
              done = true
              htmlEl.style.removeProperty("transition")
              listenerRef.foreach(el.removeEventListener("transitionend", _))
          listenerRef = Some(listener)
          el.addEventListener("transitionend", listener)
          // Fallback cleanup in case transitionend doesn't fire
          dom.window.setTimeout(
            () =>
              if !done then
                done = true; htmlEl.style.removeProperty("transition"),
            (duration + 100).toDouble
          )
      }
    }

  /** Maps a Scala easing function reference to an equivalent CSS timing string. */
  private def cssTiming(easing: Double => Double): String =
    if easing eq Easing.cubicOut then "cubic-bezier(0.33,1,0.68,1)"
    else if easing eq Easing.cubicInOut then "cubic-bezier(0.65,0,0.35,1)"
    else if easing eq Easing.cubicIn then "cubic-bezier(0.32,0,0.67,0)"
    else if easing eq Easing.linear then "linear"
    else "linear"
