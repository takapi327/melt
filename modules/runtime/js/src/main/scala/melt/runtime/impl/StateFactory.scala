/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.impl

import scala.collection.mutable

import melt.runtime.{ Batch, Cleanup, Owner, Signal, State }

/** Scala.js factory referenced by the shared `object State`. Produces a real
  * reactive cell with three-phase subscriber lanes (Pre → Bind → Post).
  */
private[runtime] object StateFactory:
  def create[A](initial: A): State[A] = new JsState[A](initial)

/** Scala.js implementation of [[State]] — the original class that used to
  * live directly under `melt.runtime`. Now hidden behind `impl` and exposed
  * only through [[StateFactory]] so that the public type is the shared trait.
  *
  * Calling [[set]] or [[update]] notifies all subscribers and propagates
  * changes through any derived [[Signal]] instances created via [[map]],
  * [[flatMap]], or `for`-comprehensions.
  *
  * Subscribers are notified in three ordered phases on each update:
  *
  *   1. '''Pre'''  — `layoutEffect` callbacks run before any DOM mutations.
  *   2. '''Bind''' — `Bind` helpers and derived `Signal` subscriptions update the DOM.
  *   3. '''Post''' — `effect` callbacks run after all DOM mutations.
  */
private final class JsState[A](private var _current: A) extends State[A]:

  // ── Three-phase subscriber lanes ────────────────────────────────────────
  private val _pre  = mutable.ListBuffer.empty[A => Unit]
  private val _bind = mutable.ListBuffer.empty[A => Unit]
  private val _post = mutable.ListBuffer.empty[A => Unit]

  def value: A = _current

  private lazy val _batchFlush: () => Unit = () =>
    _pre.toList.foreach(_(_current))
    _bind.toList.foreach(_(_current))
    _post.toList.foreach(_(_current))

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

  def update(f: A => A): Unit = set(f(_current))

  def subscribe(f: A => Unit): () => Unit =
    _bind += f
    () =>
      _bind -= f; ()

  private[runtime] def subscribePre(f: A => Unit): () => Unit =
    _pre += f
    () =>
      _pre -= f; ()

  private[runtime] def subscribePost(f: A => Unit): () => Unit =
    _post += f
    () =>
      _post -= f; ()

  lazy val signal: Signal[A] =
    val s = JsSignal.create[A](_current)
    _bind += (v => s.emit(v))
    s

  def map[B](f: A => B): Signal[B] =
    val s      = JsSignal.create[B](f(_current))
    val cancel = subscribe(v => s.emit(f(v)))
    Cleanup.register(cancel)
    s

  def flatMap[B](f: A => Signal[B]): Signal[B] =
    var inner = f(_current)
    val s     = JsSignal.create[B](inner.value)
    var cancelInner: () => Unit = inner.subscribe(b => s.emit(b))
    val cancel:      () => Unit = subscribe { a =>
      cancelInner()
      inner = f(a)
      s.emit(inner.value)
      cancelInner = inner.subscribe(b => s.emit(b))
    }
    Cleanup.register(() =>
      cancelInner(); cancel(); ()
    )
    s
