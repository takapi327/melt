/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.transition

import org.scalajs.dom

/** Slides an element in or out while fading, using an x/y pixel offset.
  *
  * On enter the element travels from `(x, y)` to `(0, 0)`.
  * On leave it travels from `(0, 0)` back to `(x, y)`.
  *
  * The `opacity` parameter (0.0–1.0) sets the starting opacity ratio.
  * Default `0.0` fades from fully transparent; `1.0` keeps constant opacity.
  *
  * {{{
  * // Fly in from 200px below:
  * // <div transition:fly={TransitionParams(y = 200)}>...</div>
  * // Fly in from half-transparent:
  * // <div transition:fly={TransitionParams(y = 200, opacity = 0.5)}>...</div>
  * }}}
  */
object Fly extends Transition:
  def apply(node: dom.Element, params: TransitionParams, direction: Direction): TransitionConfig =
    val style         = dom.window.getComputedStyle(node)
    val targetOpacity = style.getPropertyValue("opacity").toDoubleOption.getOrElse(1.0)
    val deltaOpacity  = targetOpacity * (1.0 - params.opacity)
    TransitionConfig(
      delay    = params.delay,
      duration = params.duration,
      easing   = params.easing,
      css      = Some { (t, u) =>
        val tx       = params.x * u
        val ty       = params.y * u
        val opacityV = targetOpacity - deltaOpacity * u
        s"transform: translate(${tx}px, ${ty}px); opacity: $opacityV"
      }
    )
