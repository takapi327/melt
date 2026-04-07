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
  * Usage pattern in `Bind.each` or similar:
  * {{{
  * val before = Flip.snapshot(nodes)   // 1. record positions before DOM update
  * // … perform DOM reorder …
  * Flip.play(nodes, before)            // 2. animate from old to new positions
  * }}}
  *
  * The `animate:flip` directive in templates causes the code generator to
  * mark list-item elements so that `Bind.each` automatically calls `snapshot`
  * and `play` around each rebuild.
  */
object Flip:

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
        val dy      = oldRect.top  - newRect.top
        if dx != 0.0 || dy != 0.0 then
          val htmlEl = el.asInstanceOf[dom.html.Element]
          // Invert: apply inverse transform so the element appears at the old position
          htmlEl.style.setProperty("transform", s"translate(${dx}px, ${dy}px)")
          htmlEl.style.setProperty("transition", "none")
          // Force a reflow so the browser registers the start state
          val _ = htmlEl.offsetHeight
          // Play: animate to the natural (new) position
          htmlEl.style.setProperty("transition", s"transform ${duration}ms $timingFn")
          htmlEl.style.setProperty("transform", "")
          // Clean up transition property after animation
          var done     = false
          var listener: js.Function1[dom.Event, Unit] = null
          listener = (_: dom.Event) =>
            if !done then
              done = true
              htmlEl.style.removeProperty("transition")
              el.removeEventListener("transitionend", listener)
          el.addEventListener("transitionend", listener)
          // Fallback cleanup in case transitionend doesn't fire
          dom.window.setTimeout(
            () => { if !done then { done = true; htmlEl.style.removeProperty("transition") } },
            (duration + 100).toDouble
          )
      }
    }

  /** Maps a Scala easing function reference to an equivalent CSS timing string. */
  private def cssTiming(easing: Double => Double): String =
    if easing eq Easing.cubicOut        then "cubic-bezier(0.33,1,0.68,1)"
    else if easing eq Easing.cubicInOut then "cubic-bezier(0.65,0,0.35,1)"
    else if easing eq Easing.cubicIn    then "cubic-bezier(0.32,0,0.67,0)"
    else if easing eq Easing.linear     then "linear"
    else "linear"
