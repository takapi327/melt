/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.transition

import org.scalajs.dom

/** Slides an element open or closed by animating its height or width.
  *
  * Uses CSS keyframes for off-thread animation. The `axis` parameter selects
  * `"y"` (default, animates `height`) or `"x"` (animates `width`).
  * Padding, margin, and border-width on the slide axis are also animated so
  * the surrounding space collapses smoothly.
  *
  * {{{
  * // Vertical slide (default):
  * // <div transition:slide>...</div>
  * // Horizontal slide:
  * // <div transition:slide={TransitionParams(axis = "x")}>...</div>
  * }}}
  */
object Slide extends Transition:
  def apply(node: dom.Element, params: TransitionParams, direction: Direction): TransitionConfig =
    val htmlEl = node.asInstanceOf[dom.html.Element]
    val style  = dom.window.getComputedStyle(node)

    val isY = params.axis != "x"

    val primarySize =
      if isY then htmlEl.offsetHeight.toDouble
      else htmlEl.offsetWidth.toDouble

    val paddingStart = parseFloat(if isY then style.paddingTop    else style.paddingLeft)
    val paddingEnd   = parseFloat(if isY then style.paddingBottom else style.paddingRight)
    val marginStart  = parseFloat(if isY then style.marginTop     else style.marginLeft)
    val marginEnd    = parseFloat(if isY then style.marginBottom  else style.marginRight)
    val borderStart  = parseFloat(if isY then style.borderTopWidth    else style.borderLeftWidth)
    val borderEnd    = parseFloat(if isY then style.borderBottomWidth else style.borderRightWidth)

    val targetOpacity = style.opacity.toDoubleOption.getOrElse(1.0)

    val (primaryProp, paddingStartProp, paddingEndProp,
         marginStartProp, marginEndProp, borderStartProp, borderEndProp) =
      if isY then
        ("height", "padding-top", "padding-bottom",
         "margin-top", "margin-bottom", "border-top-width", "border-bottom-width")
      else
        ("width", "padding-left", "padding-right",
         "margin-left", "margin-right", "border-left-width", "border-right-width")

    TransitionConfig(
      delay    = params.delay,
      duration = params.duration,
      easing   = params.easing,
      css      = Some { (t, _) =>
        val opacity = math.min(t * 20, 1.0) * targetOpacity
        s"overflow: hidden; " +
        s"opacity: $opacity; " +
        s"$primaryProp: ${primarySize * t}px; " +
        s"$paddingStartProp: ${paddingStart * t}px; " +
        s"$paddingEndProp: ${paddingEnd * t}px; " +
        s"$marginStartProp: ${marginStart * t}px; " +
        s"$marginEndProp: ${marginEnd * t}px; " +
        s"$borderStartProp: ${borderStart * t}px; " +
        s"$borderEndProp: ${borderEnd * t}px"
      }
    )

  private def parseFloat(s: String): Double =
    s.filter(c => c.isDigit || c == '.').toDoubleOption.getOrElse(0.0)
