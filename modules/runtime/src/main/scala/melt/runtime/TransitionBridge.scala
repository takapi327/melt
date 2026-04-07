/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits.*

import org.scalajs.dom

import melt.runtime.transition.*

/** Attaches transition metadata to DOM elements and drives enter/leave animations.
  *
  * The code generator emits calls like:
  * {{{
  * // transition:fly={params}  →  both directions
  * TransitionBridge.setBoth(_el0, Fly, params)
  *
  * // in:fly={params}  out:fade={params}  →  separate directions
  * TransitionBridge.setIn(_el0, Fly, params)
  * TransitionBridge.setOut(_el0, Fade, params)
  * }}}
  *
  * [[Bind.show]] and [[Bind.each]] call [[playIn]] / [[playOut]] at the right
  * moments to drive the animations.
  *
  * Internally, each direction's factory is stored as a Scala closure cast to
  * `js.Any` under a JS property on the element (`_meltInFn` / `_meltOutFn`).
  */
object TransitionBridge:

  private val KeyInFn       = "_meltInFn"
  private val KeyOutFn      = "_meltOutFn"
  private val KeyReversible = "_meltReversible"

  /** The type of config factory stored on elements.
    * Captures `Transition` + `TransitionParams` in a closure. */
  private type ConfigFn = dom.Element => TransitionConfig

  // ── Registration ─────────────────────────────────────────────────────────

  /** Registers an intro (`in:`) transition on `el`. */
  def setIn(el: dom.Element, t: Transition, params: TransitionParams): Unit =
    val fn: ConfigFn = e => t(e, params, Direction.In)
    el.asInstanceOf[js.Dynamic].updateDynamic(KeyInFn)(fn.asInstanceOf[js.Any])

  /** Registers an outro (`out:`) transition on `el`. */
  def setOut(el: dom.Element, t: Transition, params: TransitionParams): Unit =
    val fn: ConfigFn = e => t(e, params, Direction.Out)
    el.asInstanceOf[js.Dynamic].updateDynamic(KeyOutFn)(fn.asInstanceOf[js.Any])

  /** Registers both intro and outro (`transition:`) with the same transition and params.
    * Marks the element as reversible so that [[playIn]] / [[playOut]] will perform
    * smooth mid-animation reversal (Svelte `transition:` behaviour).
    */
  def setBoth(el: dom.Element, t: Transition, params: TransitionParams): Unit =
    val fnIn:  ConfigFn = e => t(e, params, Direction.In)
    val fnOut: ConfigFn = e => t(e, params, Direction.Out)
    val dyn = el.asInstanceOf[js.Dynamic]
    dyn.updateDynamic(KeyInFn)(fnIn.asInstanceOf[js.Any])
    dyn.updateDynamic(KeyOutFn)(fnOut.asInstanceOf[js.Any])
    dyn.updateDynamic(KeyReversible)(true)

  // ── Queries ───────────────────────────────────────────────────────────────

  /** Returns `true` if `el` has a registered intro transition. */
  def hasIn(el: dom.Element): Boolean =
    !js.isUndefined(el.asInstanceOf[js.Dynamic].selectDynamic(KeyInFn))

  /** Returns `true` if `el` has a registered outro transition. */
  def hasOut(el: dom.Element): Boolean =
    !js.isUndefined(el.asInstanceOf[js.Dynamic].selectDynamic(KeyOutFn))

  // ── Playback ──────────────────────────────────────────────────────────────

  /** Plays the intro transition on `el` if one was registered.
    * Calls `onDone` immediately if no intro is registered.
    *
    * For elements registered via [[setBoth]] (`transition:` directive), if an outro is
    * currently in progress the animation starts from the current visual `t` value for
    * a smooth mid-animation reversal (Svelte behaviour).
    *
    * @param el     the element to animate
    * @param onDone callback invoked when the animation completes
    */
  def playIn(el: dom.Element, onDone: () => Unit = () => ()): Unit =
    val dyn = el.asInstanceOf[js.Dynamic]
    val fn  = dyn.selectDynamic(KeyInFn)
    if !js.isUndefined(fn) then
      val config     = fn.asInstanceOf[ConfigFn](el)
      val reversible = !js.isUndefined(dyn.selectDynamic(KeyReversible))
      val startFrom  = if reversible then TransitionEngine.currentT(el).getOrElse(-1.0) else -1.0
      TransitionEngine.run(el, config, intro = true, startFrom = startFrom, onDone)
    else onDone()

  /** Plays the outro transition on `el` if one was registered.
    * Calls `onDone` immediately if no outro is registered.
    *
    * For elements registered via [[setBoth]] (`transition:` directive), if an intro is
    * currently in progress the animation starts from the current visual `t` value for
    * a smooth mid-animation reversal (Svelte behaviour).
    *
    * @param el     the element to animate
    * @param onDone callback invoked when the animation completes (e.g., to remove the element)
    */
  def playOut(el: dom.Element, onDone: () => Unit = () => ()): Unit =
    val dyn = el.asInstanceOf[js.Dynamic]
    val fn  = dyn.selectDynamic(KeyOutFn)
    if !js.isUndefined(fn) then
      val config     = fn.asInstanceOf[ConfigFn](el)
      val reversible = !js.isUndefined(dyn.selectDynamic(KeyReversible))
      val startFrom  = if reversible then TransitionEngine.currentT(el).getOrElse(-1.0) else -1.0
      TransitionEngine.run(el, config, intro = false, startFrom = startFrom, onDone)
    else onDone()

  /** Runs a [[KeyedTransition]] (crossfade `send`/`receive`) on `el`.
    *
    * The returned `Promise` is awaited and the resolved [[TransitionConfig]]
    * is passed to [[TransitionEngine.run]].
    *
    * @param key     the crossfade pairing key
    * @param kt      the `send` or `receive` keyed transition
    * @param el      the element to animate
    * @param params  transition parameters
    * @param intro   `true` for receive (enter), `false` for send (leave)
    * @param onDone  called when the animation finishes
    */
  def playKeyed(
    key:    Any,
    kt:     KeyedTransition,
    el:     dom.Element,
    params: TransitionParams,
    intro:  Boolean,
    onDone: () => Unit = () => ()
  ): Unit =
    kt(key, el, params).`then`[Unit](
      { config => TransitionEngine.run(el, config, intro = intro, onDone = onDone) },
      { (_: Any) => onDone() } // ensure onDone is called even if the Promise rejects
    ): Unit
