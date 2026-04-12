/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import org.scalajs.dom

/** Reactive bindings for `document` properties and events. */
object Document:

  /** Reactively sets the document title from a Signal. */
  def title(t: Signal[String]): Unit =
    dom.document.title = t.now()
    val cancel = t.subscribe(s => dom.document.title = s)
    Cleanup.register(cancel)

  /** Sets the document title from a Var. */
  def title(t: Var[String]): Unit =
    title(t.signal)

  /** Sets the document title to a static string. */
  def title(t: String): Unit =
    dom.document.title = t

  /** Registers a document event listener with automatic cleanup. */
  def on(event: String)(handler: dom.Event => Unit): Unit =
    val listener: scalajs.js.Function1[dom.Event, Unit] = handler(_)
    dom.document.addEventListener(event, listener)
    Cleanup.register(() => dom.document.removeEventListener(event, listener))
