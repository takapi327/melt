/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.transition

import org.scalajs.dom

/** Scales an element in or out while fading.
  *
  * The `start` parameter controls the initial scale factor (0 = invisible,
  * 1 = full size). The default is `0.0` which scales from nothing.
  *
  * {{{
  * // Scale in from 50% size:
  * // <div transition:scale={TransitionParams(start = 0.5)}>...</div>
  * }}}
  */
object Scale extends Transition:
  def apply(node: dom.Element, params: TransitionParams, direction: Direction): TransitionConfig =
    val start         = params.start
    val style         = dom.window.getComputedStyle(node)
    val targetOpacity = style.getPropertyValue("opacity").toDoubleOption.getOrElse(1.0)
    val deltaOpacity  = targetOpacity * (1.0 - params.opacity)
    TransitionConfig(
      delay    = params.delay,
      duration = params.duration,
      easing   = params.easing,
      css      = Some { (t, u) =>
        val scale    = start + (1.0 - start) * t
        val opacityV = targetOpacity - deltaOpacity * u
        s"transform: scale($scale); opacity: $opacityV"
      }
    )
