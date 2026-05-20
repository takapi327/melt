/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

import melt.runtime.impl.SupplierSignal
import melt.runtime.Signal

/** Facade for Node.js `AsyncLocalStorage` from `async_hooks`. */
@js.native
@JSImport("async_hooks", "AsyncLocalStorage")
class AsyncLocalStorage[A] extends js.Object:
  /** Runs `callback` with `store` as the current async context value. */
  def run[B](store: A, callback: js.Function0[B]): B = js.native

  /** Returns the current store value, or `undefined` if not in a `run()` context. */
  def getStore(): js.UndefOr[A] = js.native

/** Node.js SSR implementation of [[Router]].
  *
  * Uses `AsyncLocalStorage` (Node.js v16.4+ standard API) to scope the current
  * request path per async context chain, analogous to `ThreadLocal` on the JVM.
  *
  * `currentPath` is backed by a [[melt.runtime.impl.SupplierSignal]] that reads
  * from the storage on every `.value` access — no reactivity needed for SSR.
  *
  * `navigate` and `replace` are no-ops (no browser history on the server).
  */
object Router:

  private val _storage = new AsyncLocalStorage[String]()

  /** A read-only view of the current URL path for the active async context. */
  val currentPath: Signal[String] =
    new SupplierSignal(() => _storage.getStore().getOrElse("/"))

  /** Runs `f` with `currentPath` returning `path` for the duration of the call.
    *
    * Called automatically by [[NodeMeltContext.render]] — route handlers do not
    * need to call this directly.
    *
    * @param path the URL path (e.g. `"/users/42"`)
    * @param f    the render block — evaluated synchronously
    */
  def withPath[A](path: String)(f: => A): A =
    _storage.run(path, () => f)

  def navigate(path: String): Unit = ()
  def replace(path:  String): Unit = ()
