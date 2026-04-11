/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.transition

import org.scalajs.dom

/** Fades an element in or out by animating `opacity`.
  *
  * {{{
  * // In template:
  * // <div transition:fade={TransitionParams(duration = 400)}>...</div>
  * }}}
  */
object Fade extends Transition:
  def apply(node: dom.Element, params: TransitionParams, direction: Direction): TransitionConfig =
    TransitionConfig(
      delay    = params.delay,
      duration = params.duration,
      easing   = params.easing,
      css      = Some((t, _) => s"opacity: $t")
    )
