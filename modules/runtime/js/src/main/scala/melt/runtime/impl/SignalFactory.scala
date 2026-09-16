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

  // js.Map keyed by a monotonic id rather than mutable.ListBuffer: linking a Scala
  // collection drags the immutable hierarchy into the bundle (~41 KB gzip), see
  // memo/design-bundle-size.md §2.6. A js.Map keeps insertion order for the three
  // notification phases while making removal O(1). An earlier js.Array + indexOf
  // version was O(n) per cancel, i.e. quadratic when subscriptions are released in
  // reverse order — the normal teardown order (measured: 4,000 cancels took 144 ms).
  private val _pre    = js.Map[Int, A => Unit]()
  private val _bind   = js.Map[Int, A => Unit]()
  private val _post   = js.Map[Int, A => Unit]()
  private var _nextId = 0

  private def addTo(lane: js.Map[Int, A => Unit], f: A => Unit): () => Unit =
    val id = _nextId
    _nextId += 1
    lane(id) = f
    () => val _ = lane.delete(id)

  /** Snapshot before notifying: a subscriber may cancel during the callback. */
  private def snapshot(lane: js.Map[Int, A => Unit]): js.Array[A => Unit] =
    val out = js.Array[A => Unit]()
    lane.foreach { (_, f) =>
      out.push(f)
      ()
    }
    out

  def value: A = _current

  def subscribe(f: A => Unit): () => Unit =
    addTo(_bind, f)

  private[runtime] def subscribePre(f: A => Unit): () => Unit =
    addTo(_pre, f)

  private[runtime] def subscribePost(f: A => Unit): () => Unit =
    addTo(_post, f)

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
    snapshot(_pre).foreach(_(_current))
    snapshot(_bind).foreach(_(_current))
    snapshot(_post).foreach(_(_current))

  private[runtime] def emit(value: A): Unit =
    _current = value
    if Batch.isBatching then Batch.enqueue(_batchFlush)
    else
      snapshot(_pre).foreach(_(value))
      snapshot(_bind).foreach(_(value))
      snapshot(_post).foreach(_(value))
