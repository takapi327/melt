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

  private val _popStateListener: scalajs.js.Function1[dom.PopStateEvent, Unit] =
    _ =>
      val from = _path.value
      val to   = dom.window.location.pathname
      if from == to then ()
      else if NavGuards.allows(from, to) then _path.set(to)
      else
        // A guard cancelled a back/forward navigation — revert the URL to `from`.
        dom.window.history.pushState(null, "", from)

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
      dom.window.history.pushState(null, "", path)
      _path.set(path)
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
      dom.window.history.replaceState(null, "", path)
      _path.set(path)
      true
