/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import org.scalajs.dom

/** Event listener registration for `document.body`.
  *
  * Mirrors [[Window.on]] but targets `document.body`.  This is necessary for
  * events such as `mouseenter` and `mouseleave`, which do not fire on `window`
  * in Firefox and some other browsers.
  *
  * Use-directive actions are applied via the existing `Bind.action` helper —
  * no additional methods are needed here.
  *
  * All listeners are automatically removed when the component is destroyed.
  *
  * @see [[Window]]
  */
object Body:

  /** Registers a `document.body` event listener with automatic cleanup. */
  def on(event: String)(handler: melt.runtime.dom.Event => Unit): Unit =
    val listener: scalajs.js.Function1[dom.Event, Unit] =
      (e: dom.Event) => handler(melt.runtime.dom.Conversions.wrap(e))
    dom.document.body.addEventListener(event, listener)
    Cleanup.register(() => dom.document.body.removeEventListener(event, listener))
