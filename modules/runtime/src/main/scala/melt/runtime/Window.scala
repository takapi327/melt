/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import org.scalajs.dom

/** Reactive bindings for `window` properties and events. */
object Window:

  /** Registers a window event listener with automatic cleanup. */
  def on(event: String)(handler: dom.Event => Unit): Unit =
    val listener: scalajs.js.Function1[dom.Event, Unit] = handler(_)
    dom.window.addEventListener(event, listener)
    Cleanup.register(() => dom.window.removeEventListener(event, listener))

  /** Reactive window scroll Y position. */
  lazy val scrollY: Signal[Double] = windowSignal(dom.window.scrollY, "scroll", _ => dom.window.scrollY)

  /** Reactive window scroll X position. */
  lazy val scrollX: Signal[Double] = windowSignal(dom.window.scrollX, "scroll", _ => dom.window.scrollX)

  /** Reactive window inner width. */
  lazy val innerWidth: Signal[Double] =
    windowSignal(dom.window.innerWidth.toDouble, "resize", _ => dom.window.innerWidth.toDouble)

  /** Reactive window inner height. */
  lazy val innerHeight: Signal[Double] =
    windowSignal(dom.window.innerHeight.toDouble, "resize", _ => dom.window.innerHeight.toDouble)

  /** Reactive online status. */
  lazy val online: Signal[Boolean] =
    val v = Var(dom.window.navigator.onLine)
    val onlineListener:  scalajs.js.Function1[dom.Event, Unit] = _ => v.set(true)
    val offlineListener: scalajs.js.Function1[dom.Event, Unit] = _ => v.set(false)
    dom.window.addEventListener("online", onlineListener)
    dom.window.addEventListener("offline", offlineListener)
    Cleanup.register(() => {
      dom.window.removeEventListener("online", onlineListener)
      dom.window.removeEventListener("offline", offlineListener)
    })
    v.signal

  private def windowSignal[A](initial: A, event: String, update: dom.Event => A): Signal[A] =
    val v = Var(initial)
    val listener: scalajs.js.Function1[dom.Event, Unit] = e => v.set(update(e))
    dom.window.addEventListener(event, listener)
    Cleanup.register(() => dom.window.removeEventListener(event, listener))
    v.signal
