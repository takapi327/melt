/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import scala.collection.mutable

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
  * registered on the [[OwnerNode]] captured at registration time, so it runs
  * when the component is destroyed.  If the owner is already destroyed when
  * [[flush]] runs, the cleanup is invoked immediately by [[OwnerNode.addCleanup]].
  */
private final class MountContextImpl(owner: Option[OwnerNode]) extends MountContext:
  def onCleanup(f: () => Unit): Unit =
    owner.foreach(_.addCleanup(f))

private[runtime] object OnMount:

  /** Pairs a pending callback with the [[OwnerNode]] that was current when it was registered. */
  private final case class PendingMount(owner: Option[OwnerNode], fn: MountContext => Unit)

  private val pending = mutable.Queue[PendingMount]()

  /** Enqueues [fn] to run after the next [[Mount.apply]] call (or [[flush]]).
    * Captures the current [[Owner]] node so cleanup can be attributed correctly.
    */
  def register(fn: MountContext => Unit): Unit =
    pending.enqueue(PendingMount(Owner.current, fn))

  /** Runs all pending callbacks in FIFO order.
    *
    * A [[MountContextImpl]] backed by the captured [[OwnerNode]] is created for
    * each callback and passed in, so that any [[MountContext.onCleanup]] calls
    * inside the callback are registered on the correct owner.
    *
    * Each callback is wrapped in a try-catch so one failing mount hook does
    * not prevent the remaining hooks from running.
    *
    * Called by [[Mount.apply]] immediately after `target.appendChild(component)`.
    */
  def flush(): Unit =
    while pending.nonEmpty do
      val PendingMount(owner, fn) = pending.dequeue()
      val ctx                     = new MountContextImpl(owner)
      try fn(ctx)
      catch case _: Throwable => ()
