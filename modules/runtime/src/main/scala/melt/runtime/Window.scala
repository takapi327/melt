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
  lazy val scrollY: Signal[Double] =
    val v = Var(dom.window.scrollY)
    dom.window.addEventListener("scroll", (_: dom.Event) => v.set(dom.window.scrollY))
    v.signal

  /** Reactive window scroll X position. */
  lazy val scrollX: Signal[Double] =
    val v = Var(dom.window.scrollX)
    dom.window.addEventListener("scroll", (_: dom.Event) => v.set(dom.window.scrollX))
    v.signal

  /** Reactive window inner width. */
  lazy val innerWidth: Signal[Double] =
    val v = Var(dom.window.innerWidth.toDouble)
    dom.window.addEventListener("resize", (_: dom.Event) => v.set(dom.window.innerWidth.toDouble))
    v.signal

  /** Reactive window inner height. */
  lazy val innerHeight: Signal[Double] =
    val v = Var(dom.window.innerHeight.toDouble)
    dom.window.addEventListener("resize", (_: dom.Event) => v.set(dom.window.innerHeight.toDouble))
    v.signal

  /** Reactive online status. */
  lazy val online: Signal[Boolean] =
    val v = Var(dom.window.navigator.onLine)
    dom.window.addEventListener("online", (_: dom.Event) => v.set(true))
    dom.window.addEventListener("offline", (_: dom.Event) => v.set(false))
    v.signal
