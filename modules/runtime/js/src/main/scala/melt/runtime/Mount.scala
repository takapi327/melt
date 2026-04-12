/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import org.scalajs.dom

/** Mounts a component's root element into a target DOM element.
  *
  * Generated components call this via `App.mount(target)`.
  *
  * {{{
  * Mount(dom.document.getElementById("app"), App.create())
  * }}}
  */
object Mount:

  /** Appends `component` as a child of `target`, then flushes [[OnMount]] callbacks.
    *
    * The flush runs synchronously after `appendChild` and before the browser
    * paints, so [[onMount]] callbacks can safely read DOM geometry.
    */
  def apply(target: dom.Element, component: dom.Element): Unit =
    target.appendChild(component)
    OnMount.flush()
