/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import scala.collection.mutable

/** A read-only reactive value derived from one or more [[Var]] instances.
  *
  * Subscribers are notified whenever the upstream source changes, in the same
  * three-phase order as [[Var]]: Pre → Bind → Post.
  *
  * Use [[Var.map]] or a `for`-comprehension to create a Signal.
  *
  * {{{
  * val count   = Var(0)
  * val doubled = count.map(_ * 2)   // Signal[Int]
  * count += 1
  * doubled.now() // 2
  * }}}
  */
final class Signal[A] private[runtime] (private var _current: A):

  // ── Three-phase subscriber lanes (mirrors Var) ───────────────────────────
  private val _pre  = mutable.ListBuffer.empty[A => Unit]
  private val _bind = mutable.ListBuffer.empty[A => Unit]
  private val _post = mutable.ListBuffer.empty[A => Unit]

  /** Returns the current value without registering any reactive dependency. */
  def now(): A = _current

  /** Subscribes to future value changes in the **Bind** phase (DOM updates).
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

  /** Derives a new [[Signal]] by transforming each emitted value with `f`.
    *
    * The internal subscription is registered with [[Cleanup]] so that
    * component destruction can release it and prevent memory leaks.
    */
  def map[B](f: A => B): Signal[B] =
    val derived = new Signal[B](f(_current))
    val cancel  = subscribe(a => derived.emit(f(a)))
    Cleanup.register(cancel)
    derived

  /** Derives a new [[Signal]] by flat-mapping, supporting dynamic source switching.
    *
    * When the outer Signal emits a new value, the previous inner Signal is
    * unsubscribed and a fresh one is obtained by calling `f`.
    * The internal subscriptions are registered with [[Cleanup]].
    */
  def flatMap[B](f: A => Signal[B]): Signal[B] =
    var inner   = f(_current)
    val derived = new Signal[B](inner.now())
    var cancelInner: () => Unit = inner.subscribe(b => derived.emit(b))
    val cancelOuter = subscribe { a =>
      cancelInner()
      inner = f(a)
      derived.emit(inner.now())
      cancelInner = inner.subscribe(b => derived.emit(b))
    }
    Cleanup.register(() => { cancelInner(); cancelOuter(); () })
    derived

  /** Per-instance flush function for batch dedup. */
  private lazy val _batchFlush: () => Unit = () =>
    _pre.toList.foreach(_(_current))
    _bind.toList.foreach(_(_current))
    _post.toList.foreach(_(_current))

  /** Pushes a new value to all subscribers in phase order.
    * Package-private; called by [[Var]] and derived Signals.
    * If inside a `batch { }` block, notifications are deferred and coalesced.
    */
  private[runtime] def emit(value: A): Unit =
    _current = value
    if Batch.isBatching then Batch.enqueue(_batchFlush)
    else
      _pre.toList.foreach(_(value))
      _bind.toList.foreach(_(value))
      _post.toList.foreach(_(value))
