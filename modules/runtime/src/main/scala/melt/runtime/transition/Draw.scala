/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.transition

import scala.scalajs.js

import org.scalajs.dom

/** Animates an SVG `<path>` or other element with `getTotalLength()` as if
  * drawing it with a pen, using `stroke-dasharray` and `stroke-dashoffset`.
  *
  * The element must be an SVG path-like element that supports `getTotalLength()`.
  *
  * Duration is computed as follows (matching Svelte behaviour):
  *   - If `params.speed` is set: `duration = totalLength / speed`
  *   - Otherwise: `params.duration` is used directly
  *
  * When `strokeLinecap` is not `"butt"`, the stroke-width is added to the
  * total length so that rounded/squared caps are fully visible.
  *
  * {{{
  * // Fixed duration:
  * // <path transition:draw={TransitionParams(duration = 1000)} d="..." />
  * // Speed-based duration (1 px per ms):
  * // <path transition:draw={TransitionParams(speed = Some(1.0))} d="..." />
  * }}}
  */
object Draw extends Transition:
  def apply(node: dom.Element, params: TransitionParams, direction: Direction): TransitionConfig =
    var totalLength = node.asInstanceOf[js.Dynamic].getTotalLength().asInstanceOf[Double]

    // Add stroke-width when linecap is not "butt" (rounded/square caps extend beyond path end)
    val style = dom.window.getComputedStyle(node)
    if style.getPropertyValue("stroke-linecap") != "butt" then
      val strokeWidth = style.getPropertyValue("stroke-width")
        .filter(c => c.isDigit || c == '.')
        .toDoubleOption
        .getOrElse(0.0)
      totalLength += strokeWidth

    val computedDuration = params.speed match
      case Some(spd) if spd > 0 => (totalLength / spd).toInt
      case _                    => params.duration

    TransitionConfig(
      delay    = params.delay,
      duration = computedDuration,
      easing   = params.easing,
      css      = Some { (t, u) =>
        // stroke-dashoffset = totalLength means invisible; 0 means fully drawn
        val offset = totalLength * u
        s"stroke-dasharray: $totalLength; stroke-dashoffset: $offset"
      }
    )
