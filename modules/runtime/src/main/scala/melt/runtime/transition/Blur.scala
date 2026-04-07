/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.transition

import org.scalajs.dom

/** Blurs an element in or out while fading.
  *
  * The `amount` parameter controls the maximum blur radius in pixels (default 5px).
  *
  * {{{
  * // Blur in with 8px max radius:
  * // <div transition:blur={TransitionParams(amount = 8)}>...</div>
  * }}}
  */
object Blur extends Transition:
  def apply(node: dom.Element, params: TransitionParams, direction: Direction): TransitionConfig =
    val style         = dom.window.getComputedStyle(node)
    val targetOpacity = style.getPropertyValue("opacity").toDoubleOption.getOrElse(1.0)
    val deltaOpacity  = targetOpacity * (1.0 - params.opacity)
    TransitionConfig(
      delay    = params.delay,
      duration = params.duration,
      easing   = params.easing,
      css      = Some { (t, u) =>
        val blurPx   = params.amount * u
        val opacityV = targetOpacity - deltaOpacity * u
        s"opacity: $opacityV; filter: blur(${ blurPx }px)"
      }
    )
