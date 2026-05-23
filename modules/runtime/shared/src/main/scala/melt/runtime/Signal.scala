/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import melt.runtime.impl.SignalFactory

/** A read-only reactive value derived from one or more [[State]] instances.
  *
  * As with [[State]], each platform provides its own implementation via
  * [[melt.runtime.impl.SignalFactory]]. On Scala.js this is a full three-phase
  * reactive cell; on the JVM it is a frozen wrapper that simply holds the
  * computed initial value (SSR never re-emits).
  */
trait Signal[A] extends ReactiveValue[A]:

  /** Returns the current value. */
  def value: A

  /** Subscribes to future value changes. Returns an unsubscribe function. */
  def subscribe(f: A => Unit): () => Unit

  /** Derives a new [[Signal]] by transforming each emitted value.
    *
    * The downstream signal receives every emission from this signal,
    * even if the computed value is the same as before.
    * Use [[memo]] when you want to suppress redundant updates.
    */
  def map[B](f: A => B): Signal[B]

  /** Derives a new [[Signal]] by flat-mapping, supporting dynamic source
    * switching.
    */
  def flatMap[B](f: A => Signal[B]): Signal[B]

  /** Derives a memoized [[Signal]] that only propagates when the computed
    * value actually changes (checked via `!=`).
    *
    * Unlike [[map]], which emits on every upstream change, `memo` suppresses
    * redundant updates — useful for Boolean flags, enums, or any derived
    * value whose change frequency is lower than the source.
    *
    * {{{
    * val count  = State(0)
    * val isEven = count.memo(_ % 2 == 0) // only emits when parity changes
    * }}}
    */
  def memo[B](f: A => B): Signal[B]

  // ── Runtime-internal hooks ─────────────────────────────────────────────────

  /** Pushes a new value to subscribers. Package-private; called by [[State]]
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

  /** Implicit conversion that allows a `Signal[A]` to be used directly as an
    * `A`. Equivalent to calling `.value`.
    *
    * {{{
    * val count: Signal[Int] = ???
    * val doubled: Int = count * 2   // no .now() needed
    * }}}
    */
  given [A]: Conversion[Signal[A], A] = _.value
