/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.impl

import scala.collection.mutable

import melt.runtime.{ Batch, Cleanup, Signal }

/** Scala.js factory referenced by the shared `object Signal`. */
private[runtime] object SignalFactory:
  def pure[A](value: A): Signal[A] = JsSignal.create[A](value)

/** Internal helper exposing [[JsSignal]] construction to [[VarFactory]]
  * (which lives in the same `impl` package) without making the class itself
  * public.
  */
private[runtime] object JsSignal:
  def create[A](value: A): JsSignal[A] = new JsSignal[A](value)

/** Scala.js implementation of [[Signal]]. Mirrors the three-phase subscriber
  * model used by [[JsVar]].
  */
private[runtime] final class JsSignal[A] private (private var _current: A) extends Signal[A]:

  private val _pre  = mutable.ListBuffer.empty[A => Unit]
  private val _bind = mutable.ListBuffer.empty[A => Unit]
  private val _post = mutable.ListBuffer.empty[A => Unit]

  def value: A = _current

  def subscribe(f: A => Unit): () => Unit =
    _bind += f
    () => { _bind -= f; () }

  private[runtime] def subscribePre(f: A => Unit): () => Unit =
    _pre += f
    () => { _pre -= f; () }

  private[runtime] def subscribePost(f: A => Unit): () => Unit =
    _post += f
    () => { _post -= f; () }

  def map[B](f: A => B): Signal[B] =
    val derived = JsSignal.create[B](f(_current))
    val cancel  = subscribe(a => derived.emit(f(a)))
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
    Cleanup.register(() => { cancelInner(); cancelOuter(); () })
    derived

  private lazy val _batchFlush: () => Unit = () =>
    _pre.toList.foreach(_(_current))
    _bind.toList.foreach(_(_current))
    _post.toList.foreach(_(_current))

  private[runtime] def emit(value: A): Unit =
    _current = value
    if Batch.isBatching then Batch.enqueue(_batchFlush)
    else
      _pre.toList.foreach(_(value))
      _bind.toList.foreach(_(value))
      _post.toList.foreach(_(value))
