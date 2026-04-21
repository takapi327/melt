/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import org.scalajs.dom

import melt.runtime.Signal
import melt.runtime.Var

/** Client-side router backed by the History API.
  *
  * Tracks the current URL path as a reactive [[Signal]] and exposes
  * [[navigate]] / [[replace]] to change it programmatically.
  *
  * {{{
  * // .melt component
  * val path = BrowserRouter.currentPath
  *
  * {if path.value == "/" then
  *   <IndexPage />
  * else if path.value.startsWith("/users/") then
  *   <UserDetailPage />
  * else
  *   <NotFoundPage />
  * }
  * }}}
  */
object BrowserRouter:

  private val _path: Var[String] = Var(dom.window.location.pathname)

  private val _popStateListener: scalajs.js.Function1[dom.PopStateEvent, Unit] =
    _ => _path.set(dom.window.location.pathname)

  dom.window.addEventListener("popstate", _popStateListener)

  /** A read-only reactive view of the current URL path. */
  val currentPath: Signal[String] = _path.signal

  /** Pushes a new entry onto the history stack and updates [[currentPath]]. */
  def navigate(path: String): Unit =
    dom.window.history.pushState(null, "", path)
    _path.set(path)

  /** Replaces the current history entry and updates [[currentPath]]. */
  def replace(path: String): Unit =
    dom.window.history.replaceState(null, "", path)
    _path.set(path)
