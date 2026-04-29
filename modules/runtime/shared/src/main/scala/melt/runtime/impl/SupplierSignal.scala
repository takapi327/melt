/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.impl

import melt.runtime.Signal

/** A [[Signal]] backed by a `() => A` supplier function.
  *
  * Unlike a reactive [[melt.runtime.Var]], `value` calls the supplier on every
  * access so that the underlying source (e.g. a `ThreadLocal` on the JVM or an
  * `AsyncLocalStorage` on Node.js) can change between SSR renders.
  * All subscription / notification methods are no-ops — reactivity is not
  * needed on the server side since each SSR render is a single synchronous snapshot.
  *
  * Used by [[meltkit.Router]] on the JVM and Node.js to expose the per-request
  * path as a `Signal[String]` without breaking the cross-platform API.
  */
final class SupplierSignal[A](supplier: () => A) extends Signal[A]:
  def value:                         A          = supplier()
  def subscribe(f:  A => Unit):      () => Unit = () => ()
  def map[B](f:     A => B):         Signal[B]  = new SupplierSignal(() => f(supplier()))
  def flatMap[B](f: A => Signal[B]): Signal[B]  = f(supplier())

  private[runtime] def emit(v:          A):         Unit       = ()
  private[runtime] def subscribePre(f:  A => Unit): () => Unit = () => ()
  private[runtime] def subscribePost(f: A => Unit): () => Unit = () => ()
