/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import melt.runtime.impl.SignalFactory

/** A read-only reactive value derived from one or more [[Var]] instances.
  *
  * As with [[Var]], each platform provides its own implementation via
  * [[melt.runtime.impl.SignalFactory]]. On Scala.js this is a full three-phase
  * reactive cell; on the JVM it is a frozen wrapper that simply holds the
  * computed initial value (SSR never re-emits).
  */
trait Signal[A]:

  /** Returns the current value. */
  def now(): A

  /** Subscribes to future value changes. Returns an unsubscribe function. */
  def subscribe(f: A => Unit): () => Unit

  /** Derives a new [[Signal]] by transforming each emitted value. */
  def map[B](f: A => B): Signal[B]

  /** Derives a new [[Signal]] by flat-mapping, supporting dynamic source
    * switching.
    */
  def flatMap[B](f: A => Signal[B]): Signal[B]

  // ── Runtime-internal hooks ─────────────────────────────────────────────────

  /** Pushes a new value to subscribers. Package-private; called by [[Var]]
    * and derived Signals on Scala.js. No-op on JVM.
    */
  private[runtime] def emit(value: A): Unit

  /** Subscribes in the Pre phase — used by `layoutEffect`. */
  private[runtime] def subscribePre(f: A => Unit): () => Unit

  /** Subscribes in the Post phase — used by `effect`. */
  private[runtime] def subscribePost(f: A => Unit): () => Unit

/** Shared factory for standalone signals (typically the result of
  * `memo` / derived computations). Delegates to the platform-provided
  * [[melt.runtime.impl.SignalFactory]].
  */
object Signal:
  /** Returns a frozen signal always holding `value`. */
  def pure[A](value: A): Signal[A] = SignalFactory.pure(value)
