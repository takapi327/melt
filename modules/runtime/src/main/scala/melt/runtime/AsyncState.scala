/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** Manages the lifecycle of an asynchronous operation.
  *
  * Tracks `loading`, `value`, and `error` as reactive signals.
  *
  * {{{
  * val users = AsyncState.create { Future { fetchUsers() } }
  * // users.loading — Signal[Boolean]
  * // users.value   — Signal[Option[List[User]]]
  * // users.error   — Signal[Option[Throwable]]
  * }}}
  */
final class AsyncState[A]:
  private val _loading = Var(false)
  private val _value   = Var(Option.empty[A])
  private val _error   = Var(Option.empty[Throwable])

  val loading: Signal[Boolean]           = _loading.signal
  val value:   Signal[Option[A]]         = _value.signal
  val error:   Signal[Option[Throwable]] = _error.signal

  private[runtime] def setLoading(): Unit =
    _loading.set(true)
    _error.set(None)

  private[runtime] def setSuccess(a: A): Unit =
    _value.set(Some(a))
    _loading.set(false)

  private[runtime] def setFailure(e: Throwable): Unit =
    _error.set(Some(e))
    _loading.set(false)

object AsyncState:

  /** Runs an async effect once and tracks its lifecycle. */
  def create[F[_]: MeltEffect, A](fa: => F[A]): AsyncState[A] =
    val state = new AsyncState[A]
    state.setLoading()
    val cancel = summon[MeltEffect[F]].runAsync(fa)(
      a => state.setSuccess(a),
      e => state.setFailure(e)
    )
    onCleanup(cancel)
    state

  /** Runs an async effect that re-executes whenever `dep` changes. */
  def derived[F[_]: MeltEffect, A, B](dep: Var[A])(f: A => F[B]): AsyncState[B] =
    val state              = new AsyncState[B]
    var cancelPrev: () => Unit = () => ()

    effect(dep) { a =>
      cancelPrev()
      state.setLoading()
      cancelPrev = summon[MeltEffect[F]].runAsync(f(a))(
        b => state.setSuccess(b),
        e => state.setFailure(e)
      )
      onCleanup(() => cancelPrev())
    }
    state

  def derived[F[_]: MeltEffect, A, B](dep: Signal[A])(f: A => F[B]): AsyncState[B] =
    val state              = new AsyncState[B]
    var cancelPrev: () => Unit = () => ()

    effect(dep) { a =>
      cancelPrev()
      state.setLoading()
      cancelPrev = summon[MeltEffect[F]].runAsync(f(a))(
        b => state.setSuccess(b),
        e => state.setFailure(e)
      )
      onCleanup(() => cancelPrev())
    }
    state
