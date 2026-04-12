/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.transition

import scala.scalajs.js

import org.scalajs.dom

/** A keyed transition that resolves its [[TransitionConfig]] asynchronously.
  *
  * `send` and `receive` transitions register their elements by key in a shared
  * table.  After a microtask tick the counterpart is looked up; if found the
  * two elements animate as if the content flew from one location to the other
  * (FLIP-style translate + scale + opacity).  If no counterpart is found the
  * optional `fallback` transition is used instead.
  */
trait KeyedTransition:
  /** Register `node` under `key` and return a `Promise` that resolves to the
    * [[TransitionConfig]] once the counterpart has been located (or the
    * fallback has been chosen). */
  def apply(key: Any, node: dom.Element, params: TransitionParams): js.Promise[TransitionConfig]

/** Creates a `(send, receive)` pair of [[KeyedTransition]]s for cross-fade
  * animations between elements that share the same logical `key`.
  *
  * Typical usage (todo-list):
  * {{{
  * val (send, receive) = Crossfade()
  *
  * // In template — items moving from pending to done list:
  * // out:send={key = item.id}  in:receive={key = item.id}
  * }}}
  *
  * @param duration  Maps distance in pixels to animation duration in ms.
  *                  Default: `d => (math.sqrt(d) * 30).toInt` (Svelte default).
  * @param delay     Delay before the animation starts, in ms.
  * @param easing    Easing function applied to the animation.
  * @param fallback  Transition to use when no counterpart is found for a key.
  */
object Crossfade:

  def apply(
    duration: Double => Int = d => (math.sqrt(d) * 30).toInt,
    delay:    Int = 0,
    easing:   Double => Double = Easing.cubicOut,
    fallback: Option[Transition] = None
  ): (KeyedTransition, KeyedTransition) =

    // Shared pending tables: key → DOMRect measured on the sending side
    val toReceive = scala.collection.mutable.Map.empty[Any, dom.DOMRect]
    val toSend    = scala.collection.mutable.Map.empty[Any, dom.DOMRect]

    def makeSide(
      items:        scala.collection.mutable.Map[Any, dom.DOMRect],
      counterparts: scala.collection.mutable.Map[Any, dom.DOMRect],
      intro:        Boolean
    ): KeyedTransition =
      new KeyedTransition:
        def apply(key: Any, node: dom.Element, params: TransitionParams): js.Promise[TransitionConfig] =
          // Register our current position
          items(key) = node.getBoundingClientRect()

          // After a microtask, look up the counterpart
          js.Promise.resolve[Unit](()).`then` { _ =>
            counterparts.remove(key) match
              case Some(fromRect) =>
                // Counterpart found → compute FLIP-style crossfade config
                js.Promise.resolve[TransitionConfig](
                  computeConfig(fromRect, node, params, intro, duration, delay, easing)
                )
              case None =>
                // No counterpart → use fallback or no-op
                items.remove(key)
                val dir    = if intro then Direction.In else Direction.Out
                val config = fallback
                  .map(_(node, params, dir))
                  .getOrElse(TransitionConfig(delay = delay, duration = 0))
                js.Promise.resolve[TransitionConfig](config)
          }

    val send    = makeSide(toSend, toReceive, intro = false)
    val receive = makeSide(toReceive, toSend, intro = true)
    (send, receive)

  private def computeConfig(
    from:     dom.DOMRect,
    node:     dom.Element,
    params:   TransitionParams,
    intro:    Boolean,
    duration: Double => Int,
    delay:    Int,
    easing:   Double => Double
  ): TransitionConfig =
    val to  = node.getBoundingClientRect()
    val dx  = from.left - to.left
    val dy  = from.top - to.top
    val dw  = if to.width != 0 then from.width / to.width else 1.0
    val dh  = if to.height != 0 then from.height / to.height else 1.0
    val d   = math.sqrt(dx * dx + dy * dy)
    val dur = duration(d)

    val style   = dom.window.getComputedStyle(node)
    val tfm     = if style.transform == "none" then "" else style.transform + " "
    val opacity = style.opacity.toDoubleOption.getOrElse(1.0)

    TransitionConfig(
      delay    = delay,
      duration = dur,
      easing   = easing,
      css      = Some { (t, u) =>
        val scaleX = t + (1.0 - t) * dw
        val scaleY = t + (1.0 - t) * dh
        s"opacity: ${ t * opacity }; " +
          s"transform-origin: top left; " +
          s"transform: ${ tfm }translate(${ u * dx }px, ${ u * dy }px) scale($scaleX, $scaleY)"
      }
    )
