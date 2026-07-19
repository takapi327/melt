/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** The three states of an in-flight asynchronous value: still loading, resolved
  * with a value, or failed with an error.
  *
  * Shared across platforms so that a server-driven [[melt.runtime.State]] of
  * `Async[A]` renders a `Loading` (or seeded `Done`) snapshot on the JVM during
  * SSR and becomes reactive on the client after hydration. This is the single
  * abstraction a reactive query resolves into, and the same shape a future
  * streaming SSR layer can settle — matching `Boundary`/`Await`'s
  * loading/resolved/failed model without duplicating it.
  *
  * {{{
  * query.state.value match
  *   case Async.Loading    => <p>Loading…</p>
  *   case Async.Failed(e)  => <p class="error">{e.getMessage}</p>
  *   case Async.Done(list) => <ul>{list.map(p => <li>{p.title}</li>)}</ul>
  * }}}
  */
enum Async[+A]:
  case Loading
  case Done(value: A)
  case Failed(error: Throwable)

  /** Transforms a resolved value, leaving `Loading`/`Failed` untouched. */
  def map[B](f: A => B): Async[B] = this match
    case Async.Loading   => Async.Loading
    case Async.Done(a)   => Async.Done(f(a))
    case Async.Failed(e) => Async.Failed(e)

  /** The resolved value, if any. */
  def toOption: Option[A] = this match
    case Async.Done(a) => Some(a)
    case _             => None

  /** The error, if this failed. */
  def toError: Option[Throwable] = this match
    case Async.Failed(e) => Some(e)
    case _               => None

  def isLoading: Boolean = this match
    case Async.Loading => true
    case _             => false

  def isDone: Boolean = this match
    case Async.Done(_) => true
    case _             => false

  def isFailed: Boolean = this match
    case Async.Failed(_) => true
    case _               => false

  /** Collapses all three cases to a single `B`. */
  def fold[B](onLoading: => B)(onFailed: Throwable => B)(onDone: A => B): B = this match
    case Async.Loading   => onLoading
    case Async.Failed(e) => onFailed(e)
    case Async.Done(a)   => onDone(a)
