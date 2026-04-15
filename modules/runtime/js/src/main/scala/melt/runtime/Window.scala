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
  def on(event: String)(handler: melt.runtime.dom.Event => Unit): Unit =
    val listener: scalajs.js.Function1[dom.Event, Unit] =
      (e: dom.Event) => handler(melt.runtime.dom.Conversions.wrap(e))
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

  // ── Bind directives (<melt:window bind:prop={v}>) ──────────────────────

  /** Two-way binding for `window.scrollY`.
    *
    * - `window → Var`: the Var is updated on every `scroll` event.
    * - `Var → window`: calling `scrollTo` on every change, **skipping the
    *   initial value** to avoid unexpected scroll-to-top on mount
    *   (mirrors Svelte's accessibility-friendly behaviour).
    */
  def bindScrollY(v: Var[Double]): Unit =
    v.set(dom.window.scrollY)
    val scrollListener: scalajs.js.Function1[dom.Event, Unit] = _ => v.set(dom.window.scrollY)
    dom.window.addEventListener("scroll", scrollListener)
    Cleanup.register(() => dom.window.removeEventListener("scroll", scrollListener))
    var initialized = false
    val cancel      = v.signal.subscribe { newY =>
      if initialized then dom.window.scrollTo(dom.window.scrollX.toInt, newY.toInt)
      else initialized = true
    }
    Cleanup.register(cancel)

  /** Two-way binding for `window.scrollX`.
    *
    * Same semantics as [[bindScrollY]] — initial value does not trigger scrolling.
    */
  def bindScrollX(v: Var[Double]): Unit =
    v.set(dom.window.scrollX)
    val scrollListener: scalajs.js.Function1[dom.Event, Unit] = _ => v.set(dom.window.scrollX)
    dom.window.addEventListener("scroll", scrollListener)
    Cleanup.register(() => dom.window.removeEventListener("scroll", scrollListener))
    var initialized = false
    val cancel      = v.signal.subscribe { newX =>
      if initialized then dom.window.scrollTo(newX.toInt, dom.window.scrollY.toInt)
      else initialized = true
    }
    Cleanup.register(cancel)

  /** One-way binding: `window.innerWidth → Var` (read-only). */
  def bindInnerWidth(v: Var[Double]): Unit =
    v.set(dom.window.innerWidth.toDouble)
    val listener: scalajs.js.Function1[dom.Event, Unit] = _ => v.set(dom.window.innerWidth.toDouble)
    dom.window.addEventListener("resize", listener)
    Cleanup.register(() => dom.window.removeEventListener("resize", listener))

  /** One-way binding: `window.innerHeight → Var` (read-only). */
  def bindInnerHeight(v: Var[Double]): Unit =
    v.set(dom.window.innerHeight.toDouble)
    val listener: scalajs.js.Function1[dom.Event, Unit] = _ => v.set(dom.window.innerHeight.toDouble)
    dom.window.addEventListener("resize", listener)
    Cleanup.register(() => dom.window.removeEventListener("resize", listener))

  /** One-way binding: `window.outerWidth → Var` (read-only). */
  def bindOuterWidth(v: Var[Double]): Unit =
    v.set(dom.window.outerWidth.toDouble)
    val listener: scalajs.js.Function1[dom.Event, Unit] = _ => v.set(dom.window.outerWidth.toDouble)
    dom.window.addEventListener("resize", listener)
    Cleanup.register(() => dom.window.removeEventListener("resize", listener))

  /** One-way binding: `window.outerHeight → Var` (read-only). */
  def bindOuterHeight(v: Var[Double]): Unit =
    v.set(dom.window.outerHeight.toDouble)
    val listener: scalajs.js.Function1[dom.Event, Unit] = _ => v.set(dom.window.outerHeight.toDouble)
    dom.window.addEventListener("resize", listener)
    Cleanup.register(() => dom.window.removeEventListener("resize", listener))

  /** One-way binding: `window.devicePixelRatio → Var` (read-only).
    *
    * There is no dedicated event for DPR changes; `resize` is used as a proxy
    * since display changes (zoom, moving to another screen) typically also fire it.
    */
  def bindDevicePixelRatio(v: Var[Double]): Unit =
    v.set(dom.window.devicePixelRatio)
    val listener: scalajs.js.Function1[dom.Event, Unit] = _ => v.set(dom.window.devicePixelRatio)
    dom.window.addEventListener("resize", listener)
    Cleanup.register(() => dom.window.removeEventListener("resize", listener))

  /** One-way binding: `navigator.onLine → Var[Boolean]` (read-only). */
  def bindOnline(v: Var[Boolean]): Unit =
    v.set(dom.window.navigator.onLine)
    val onListener:  scalajs.js.Function1[dom.Event, Unit] = _ => v.set(true)
    val offListener: scalajs.js.Function1[dom.Event, Unit] = _ => v.set(false)
    dom.window.addEventListener("online", onListener)
    dom.window.addEventListener("offline", offListener)
    Cleanup.register(() => {
      dom.window.removeEventListener("online", onListener)
      dom.window.removeEventListener("offline", offListener)
    })

  // ── Internal ─────────────────────────────────────────────────────────────

  private def windowSignal[A](initial: A, event: String, update: dom.Event => A): Signal[A] =
    val v = Var(initial)
    val listener: scalajs.js.Function1[dom.Event, Unit] = e => v.set(update(e))
    dom.window.addEventListener(event, listener)
    Cleanup.register(() => dom.window.removeEventListener(event, listener))
    v.signal
