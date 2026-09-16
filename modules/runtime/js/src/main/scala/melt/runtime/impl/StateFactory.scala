/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.impl

import scala.scalajs.js

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

  private lazy val _batchFlush: () => Unit = () =>
    snapshot(_pre).foreach(_(_current))
    snapshot(_bind).foreach(_(_current))
    snapshot(_post).foreach(_(_current))

  def set(value: A): Unit =
    // Dedup: writing an equal value is a no-op — skip the subscriber notification
    // (and its DOM re-render) entirely, matching Svelte/Solid-style change detection.
    if value == _current then ()
    else
      Owner.enterReactive()
      try
        _current = value
        if Batch.isBatching then Batch.enqueue(_batchFlush)
        else
          snapshot(_pre).foreach(_(value))
          snapshot(_bind).foreach(_(value))
          snapshot(_post).foreach(_(value))
      finally Owner.exitReactive()

  def update(f: A => A): Unit = set(f(_current))

  def subscribe(f: A => Unit): () => Unit =
    addTo(_bind, f)

  private[runtime] def subscribePre(f: A => Unit): () => Unit =
    addTo(_pre, f)

  private[runtime] def subscribePost(f: A => Unit): () => Unit =
    addTo(_post, f)

  lazy val signal: Signal[A] =
    val s = JsSignal.create[A](_current)
    val _ = addTo(_bind, (v: A) => s.emit(v))
    s

  def map[B](f: A => B): Signal[B] =
    val s      = JsSignal.create[B](f(_current))
    val cancel = subscribe(v => s.emit(f(v)))
    Cleanup.register(cancel)
    s

  def memo[B](f: A => B): Signal[B] =
    val s      = JsSignal.create[B](f(_current))
    val cancel = subscribe { v =>
      val newVal = f(v)
      if newVal != s.value then s.emit(newVal)
    }
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
