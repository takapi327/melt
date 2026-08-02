/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import org.scalajs.dom

import melt.runtime.Signal
import melt.runtime.State

/** Client-side router backed by the browser History API.
  *
  * Tracks the current URL path as a reactive [[Signal]] and exposes
  * [[navigate]] / [[replace]] to change it programmatically.
  *
  * Used by [[BrowserAdapter]] to react to URL changes, and by components
  * to trigger client-side navigation without a full page reload.
  *
  * {{{
  * // .melt component — read the current path reactively
  * val path = Router.currentPath
  *
  * // Navigate programmatically
  * Router.navigate("/users/42")
  * }}}
  */
object Router:

  private val _path: State[String] = State(dom.window.location.pathname)

  // ── Scroll restoration ──────────────────────────────────────────────────
  // Each history entry carries a monotonic `meltKey` in `history.state`. With
  // `scrollRestoration = "manual"` the browser no longer moves the scroll on
  // back/forward, so at `popstate` time the offset still belongs to the entry we
  // are LEAVING — we save it there and restore the incoming entry's saved offset.
  private var _currentKey: Long = 0L
  private var _nextKey:    Long = 1L

  private def historyDyn: scalajs.js.Dynamic = dom.window.history.asInstanceOf[scalajs.js.Dynamic]

  private def keyState(k: Long): scalajs.js.Any = scalajs.js.Dynamic.literal(meltKey = k.toDouble)

  private def readKey(): Option[Long] =
    val st = dom.window.history.state
    if st == null then None
    else
      val k = st.asInstanceOf[scalajs.js.Dynamic].selectDynamic("meltKey")
      if scalajs.js.isUndefined(k) then None else Some(k.asInstanceOf[Double].toLong)

  private def saveScroll(key: Long): Unit =
    ScrollPositions.save(key, dom.window.scrollX, dom.window.scrollY)

  private def restoreScroll(key: Long): Unit =
    val (x, y) = ScrollPositions.restore(key)
    dom.window.requestAnimationFrame(_ => dom.window.scrollTo(x.toInt, y.toInt))

  // Take manual control and tag the initial entry so its scroll can be restored.
  try historyDyn.scrollRestoration = "manual"
  catch case _: Throwable => ()
  _currentKey = readKey().getOrElse(0L)
  dom.window.history.replaceState(keyState(_currentKey), "", dom.window.location.pathname)

  private val _popStateListener: scalajs.js.Function1[dom.PopStateEvent, Unit] =
    _ =>
      val from = _path.value
      val to   = dom.window.location.pathname
      if from == to then ()
      else if NavGuards.allows(from, to) then
        saveScroll(_currentKey) // offset still belongs to the leaving entry
        _currentKey = readKey().getOrElse({ val k = _nextKey; _nextKey += 1; k })
        _path.set(to)
        restoreScroll(_currentKey)
      else
        // A guard cancelled a back/forward navigation — revert the URL to `from`.
        dom.window.history.pushState(keyState(_currentKey), "", from)

  dom.window.addEventListener("popstate", _popStateListener)

  /** A read-only reactive view of the current URL path. */
  val currentPath: Signal[String] = _path.signal

  /** Registers a navigation guard consulted before every client navigation
    * (programmatic and back/forward). Return `false` to cancel. Returns an
    * unregister function.
    *
    * {{{
    * val off = Router.beforeNavigate((from, to) => !hasUnsavedChanges || confirmLeave())
    * }}}
    */
  def beforeNavigate(guard: (String, String) => Boolean): () => Unit =
    NavGuards.register(guard)

  /** Pushes a new entry onto the history stack and updates [[currentPath]],
    * unless a [[beforeNavigate]] guard cancels it. Returns `true` if the
    * navigation happened.
    *
    * `_path.set` notifies all subscribers (including [[BrowserAdapter]]) so
    * there is no need to dispatch a synthetic `popstate` event.
    */
  def navigate(path: String): Boolean =
    val from = _path.value
    if from == path then true
    else if !NavGuards.allows(from, path) then false
    else
      saveScroll(_currentKey) // remember where we were before leaving
      _currentKey = _nextKey
      _nextKey += 1
      dom.window.history.pushState(keyState(_currentKey), "", path)
      _path.set(path)
      dom.window.scrollTo(0, 0) // a fresh navigation starts at the top
      true

  /** Replaces the current history entry and updates [[currentPath]], unless a
    * [[beforeNavigate]] guard cancels it. Returns `true` if it happened.
    *
    * Like [[navigate]] but replaces the current history entry instead of
    * pushing a new one.
    */
  def replace(path: String): Boolean =
    val from = _path.value
    if !NavGuards.allows(from, path) then false
    else
      // Replace keeps the current entry (and its key/scroll) — no scroll reset.
      dom.window.history.replaceState(keyState(_currentKey), "", path)
      _path.set(path)
      true
