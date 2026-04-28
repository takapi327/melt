/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import melt.runtime.impl.SupplierSignal
import melt.runtime.Signal

/** JVM SSR implementation of [[Router]].
  *
  * SSR has no browser history — [[navigate]] and [[replace]] are no-ops.
  * [[currentPath]] reads from a `ThreadLocal` so that each server request can
  * set the path independently (see [[withPath]]) without interfering with
  * concurrent renders running on other threads.
  *
  * `withPath` is called automatically by `ctx.render(...)` via [[meltkit.ComponentOps]],
  * so route handlers do not need to call it directly.
  */
object Router:

  private val _pathLocal: ThreadLocal[String] =
    ThreadLocal.withInitial[String](() => "/")

  /** A read-only reactive view of the current URL path.
    *
    * On the JVM this is backed by a `ThreadLocal` so the value seen during
    * SSR rendering reflects the path set by [[withPath]] on the same thread.
    */
  val currentPath: Signal[String] = new SupplierSignal(() => _pathLocal.get())

  /** Renders `f` with [[currentPath]] returning `path` on the calling thread.
    *
    * The previous path value is restored after `f` returns (or throws),
    * making this safe to call in nested contexts.
    *
    * @param path the URL path to expose during the render (e.g. `"/users/42"`)
    * @param f    the render block — typically `ctx.render(App())`
    */
  def withPath[A](path: String)(f: => A): A =
    val prev = _pathLocal.get()
    _pathLocal.set(path)
    try f
    finally _pathLocal.set(prev)

  def navigate(path: String): Unit = ()

  def replace(path: String): Unit = ()
