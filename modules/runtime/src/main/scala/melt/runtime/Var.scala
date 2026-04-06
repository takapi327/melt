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
  * {{{
  * val count   = Var(0)
  * val doubled = count.map(_ * 2)
  * count += 1
  * assert(doubled.now() == 2)
  * }}}
  */
final class Var[A] private (private var _current: A):

  private val subscribers = mutable.ListBuffer.empty[A => Unit]

  /** Returns the current value without registering any reactive dependency. */
  def now(): A = _current

  /** Replaces the current value and notifies all subscribers.
    * If inside a `batch { }` block, notifications are deferred until the batch completes.
    */
  def set(value: A): Unit =
    _current = value
    if Batch.isBatching then
      val subs = subscribers.toList
      Batch.enqueue(() => subs.foreach(_(value)))
    else subscribers.foreach(_(value))

  /** Updates the current value using `f` and notifies all subscribers. */
  def update(f: A => A): Unit = set(f(_current))

  /** Subscribes to future value changes.
    *
    * @return an unsubscribe function; call it to stop receiving notifications.
    */
  def subscribe(f: A => Unit): () => Unit =
    subscribers += f
    () => { subscribers -= f; () }

  /** Returns a read-only view of this variable as a [[Signal]].
    *
    * The returned Signal always reflects the current value of this Var.
    * The same Signal instance is returned on subsequent calls.
    */
  lazy val signal: Signal[A] =
    val s = new Signal[A](_current)
    subscribers += (v => s.emit(v))
    s

  /** Derives a new [[Signal]] by transforming each emitted value with `f`.
    *
    * The internal subscription is registered with [[Cleanup]] so that
    * component destruction can release it and prevent memory leaks.
    */
  def map[B](f: A => B): Signal[B] =
    val s = new Signal[B](f(_current))
    val callback: A => Unit = v => s.emit(f(v))
    subscribers += callback
    Cleanup.register(() => { subscribers -= callback; () })
    s

  /** Derives a new [[Signal]] by flat-mapping, supporting dynamic source switching.
    *
    * When this Var emits a new value, the previous inner Signal is
    * unsubscribed and a fresh one is obtained by calling `f`.
    * The internal subscription is registered with [[Cleanup]].
    */
  def flatMap[B](f: A => Signal[B]): Signal[B] =
    var inner = f(_current)
    val s     = new Signal[B](inner.now())
    var cancelInner: () => Unit = inner.subscribe(b => s.emit(b))
    val callback:    A => Unit  = { a =>
      cancelInner()
      inner = f(a)
      s.emit(inner.now())
      cancelInner = inner.subscribe(b => s.emit(b))
    }
    subscribers += callback
    Cleanup.register(() => { cancelInner(); subscribers -= callback; () })
    s

object Var:
  def apply[A](initial: A): Var[A] = new Var(initial)
