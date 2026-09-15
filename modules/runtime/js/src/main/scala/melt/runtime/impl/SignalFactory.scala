/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.impl

import scala.scalajs.js

import melt.runtime.{ Batch, Cleanup, Signal }

/** Scala.js factory referenced by the shared `object Signal`. */
private[runtime] object SignalFactory:
  def pure[A](value: A): Signal[A] = JsSignal.create[A](value)

/** Internal helper exposing [[JsSignal]] construction to [[StateFactory]]
  * (which lives in the same `impl` package) without making the class itself
  * public.
  */
private[runtime] object JsSignal:
  def create[A](value: A): JsSignal[A] = new JsSignal[A](value)

/** Scala.js implementation of [[Signal]]. Mirrors the three-phase subscriber
  * model used by [[JsState]].
  */
private[runtime] final class JsSignal[A] private (private var _current: A) extends Signal[A]:

  private val _pre  = js.Array[A => Unit]()
  private val _bind = js.Array[A => Unit]()
  private val _post = js.Array[A => Unit]()

  /** Removes `f` by identity. js.Array rather than mutable.ListBuffer: `ListBuffer`'s
    * `subtractOne` reaches `Predef` and the immutable collection hierarchy, which costs
    * ~41 KB gzip in a Scala.js bundle. See memo/design-bundle-size.md §2.6. Handles are
    * only ever removed through the closure `subscribe` returns, so identity is enough. */
  private def drop(lane: js.Array[A => Unit], f: A => Unit): Unit =
    val i = lane.indexOf(f)
    if i >= 0 then
      val _ = lane.splice(i, 1)

  def value: A = _current

  def subscribe(f: A => Unit): () => Unit =
    val _ = _bind.push(f)
    () => drop(_bind, f)

  private[runtime] def subscribePre(f: A => Unit): () => Unit =
    val _ = _pre.push(f)
    () => drop(_pre, f)

  private[runtime] def subscribePost(f: A => Unit): () => Unit =
    val _ = _post.push(f)
    () => drop(_post, f)

  def map[B](f: A => B): Signal[B] =
    val derived = JsSignal.create[B](f(_current))
    val cancel  = subscribe(a => derived.emit(f(a)))
    Cleanup.register(cancel)
    derived

  def memo[B](f: A => B): Signal[B] =
    val derived = JsSignal.create[B](f(_current))
    val cancel  = subscribe { a =>
      val newVal = f(a)
      if newVal != derived.value then derived.emit(newVal)
    }
    Cleanup.register(cancel)
    derived

  def flatMap[B](f: A => Signal[B]): Signal[B] =
    var inner   = f(_current)
    val derived = JsSignal.create[B](inner.value)
    var cancelInner: () => Unit = inner.subscribe(b => derived.emit(b))
    val cancelOuter = subscribe { a =>
      cancelInner()
      inner = f(a)
      derived.emit(inner.value)
      cancelInner = inner.subscribe(b => derived.emit(b))
    }
    Cleanup.register(() =>
      cancelInner(); cancelOuter(); ()
    )
    derived

  private lazy val _batchFlush: () => Unit = () =>
    _pre.jsSlice().foreach(_(_current))
    _bind.jsSlice().foreach(_(_current))
    _post.jsSlice().foreach(_(_current))

  private[runtime] def emit(value: A): Unit =
    _current = value
    if Batch.isBatching then Batch.enqueue(_batchFlush)
    else
      _pre.jsSlice().foreach(_(value))
      _bind.jsSlice().foreach(_(value))
      _post.jsSlice().foreach(_(value))
