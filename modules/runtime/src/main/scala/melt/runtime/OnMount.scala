/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import scala.collection.mutable

import org.scalajs.dom

/** Post-mount callback queue for [[onMount]] lifecycle hooks.
  *
  * Callbacks are registered during a component's `create()` call and flushed
  * synchronously by [[Mount.apply]] immediately after `appendChild`, before the
  * browser paints.  This mirrors Svelte's `onMount` semantics.
  *
  * Because Scala.js is single-threaded a single global mutable queue is safe.
  *
  * ==Execution order==
  * When components are nested, `create()` builds the tree recursively: the child's
  * `create()` runs inside the parent's `create()`, so child callbacks enqueue first.
  * [[flush]] processes the queue FIFO, giving child-before-parent order — identical
  * to Svelte.
  *
  * ==Cleanup==
  * If a callback returns a `() => Unit` cleanup function, that function is
  * forwarded to [[Lifecycle]] keyed by the mounted root element, so it runs when
  * the component is destroyed via [[Lifecycle.destroy]].
  */
private[runtime] object OnMount:

  private val pending = mutable.Queue[() => Option[() => Unit]]()

  /** Enqueues [fn] to run after the next [[Mount.apply]] call (or [[flush]]). */
  def register(fn: () => Option[() => Unit]): Unit =
    pending.enqueue(fn)

  /** Runs all pending callbacks in FIFO order.
    *
    * Any cleanup functions returned by callbacks are registered with [[Lifecycle]]
    * under [mountedRoot] so they execute on component destruction.
    *
    * Called by [[Mount.apply]] immediately after `target.appendChild(component)`.
    */
  def flush(mountedRoot: dom.Element): Unit =
    while pending.nonEmpty do
      pending.dequeue()().foreach(Lifecycle.addCleanup(mountedRoot, _))
