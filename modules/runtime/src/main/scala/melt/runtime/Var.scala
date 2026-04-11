/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import scala.collection.mutable

/** A mutable reactive variable.
  *
  * Calling [[set]] or [[update]] notifies all subscribers and propagates
  * changes through any derived [[Signal]] instances created via [[map]],
  * [[flatMap]], or `for`-comprehensions.
  *
  * Subscribers are notified in three ordered phases on each update:
  *
  *   1. **Pre** — [[layoutEffect]] callbacks run before any DOM mutations.
  *   2. **Bind** — [[Bind]] helpers and derived [[Signal]] subscriptions update the DOM.
  *   3. **Post** — [[effect]] callbacks run after all DOM mutations.
  *
  * {{{
  * val count   = Var(0)
  * val doubled = count.map(_ * 2)
  * count += 1
  * assert(doubled.now() == 2)
  * }}}
  */
final class Var[A] private (private var _current: A):

  // ── Three-phase subscriber lanes ────────────────────────────────────────
  // Pre  → layoutEffect (runs before DOM updates)
  // Bind → Bind.* helpers and Signal propagation (DOM updates)
  // Post → effect (runs after DOM updates)
  private val _pre  = mutable.ListBuffer.empty[A => Unit]
  private val _bind = mutable.ListBuffer.empty[A => Unit]
  private val _post = mutable.ListBuffer.empty[A => Unit]

  /** Returns the current value without registering any reactive dependency. */
  def now(): A = _current

  /** Per-instance flush function for batch dedup. */
  private lazy val _batchFlush: () => Unit = () =>
    _pre.toList.foreach(_(_current))
    _bind.toList.foreach(_(_current))
    _post.toList.foreach(_(_current))

  /** Replaces the current value and notifies all subscribers in phase order.
    * If inside a `batch { }` block, notifications are deferred and coalesced.
    * Tracks reactive update depth to detect infinite loops.
    */
  def set(value: A): Unit =
    Owner.enterReactive()
    try
      _current = value
      if Batch.isBatching then Batch.enqueue(_batchFlush)
      else
        _pre.toList.foreach(_(value))
        _bind.toList.foreach(_(value))
        _post.toList.foreach(_(value))
    finally Owner.exitReactive()

  /** Updates the current value using `f` and notifies all subscribers. */
  def update(f: A => A): Unit = set(f(_current))

  /** Subscribes to future value changes in the **Bind** phase (DOM updates).
    *
    * Used internally by [[Bind]] helpers and [[map]] / [[flatMap]].
    *
    * @return an unsubscribe function; call it to stop receiving notifications.
    */
  def subscribe(f: A => Unit): () => Unit =
    _bind += f
    () => { _bind -= f; () }

  /** Subscribes in the **Pre** phase — fires before [[Bind]] DOM updates.
    * Used by [[layoutEffect]].
    */
  private[runtime] def subscribePre(f: A => Unit): () => Unit =
    _pre += f
    () => { _pre -= f; () }

  /** Subscribes in the **Post** phase — fires after [[Bind]] DOM updates.
    * Used by [[effect]].
    */
  private[runtime] def subscribePost(f: A => Unit): () => Unit =
    _post += f
    () => { _post -= f; () }

  /** Returns a read-only view of this variable as a [[Signal]].
    *
    * The returned Signal always reflects the current value of this Var.
    * The same Signal instance is returned on subsequent calls.
    */
  lazy val signal: Signal[A] =
    val s = new Signal[A](_current)
    _bind += (v => s.emit(v))
    s

  /** Derives a new [[Signal]] by transforming each emitted value with `f`.
    *
    * The internal subscription is registered with [[Cleanup]] so that
    * component destruction can release it and prevent memory leaks.
    */
  def map[B](f: A => B): Signal[B] =
    val s      = new Signal[B](f(_current))
    val cancel = subscribe(v => s.emit(f(v)))
    Cleanup.register(cancel)
    s

  /** Derives a new [[Signal]] by flat-mapping, supporting dynamic source switching.
    *
    * When this Var emits a new value, the previous inner Signal is
    * unsubscribed and a fresh one is obtained by calling `f`.
    * The internal subscription is registered with [[Owner]] (via [[Cleanup.register]]).
    */
  def flatMap[B](f: A => Signal[B]): Signal[B] =
    var inner = f(_current)
    val s     = new Signal[B](inner.now())
    var cancelInner: () => Unit = inner.subscribe(b => s.emit(b))
    val cancel:      () => Unit = subscribe { a =>
      cancelInner()
      inner = f(a)
      s.emit(inner.now())
      cancelInner = inner.subscribe(b => s.emit(b))
    }
    Cleanup.register(() => { cancelInner(); cancel(); () })
    s

object Var:
  def apply[A](initial: A): Var[A] = new Var(initial)
