/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import scala.concurrent.Future
/** Context for propagating pending-Future state from [[Await]] to [[Boundary]].
  *
  * While [[Boundary.create]] renders its children it installs a context via
  * [[withContext]]. Any [[Await]] call made during that window registers its
  * `Future` here, allowing the boundary to show a pending UI until all
  * registered futures have settled.
  *
  * This uses a global variable, which is safe because Scala.js is single-threaded.
  */
object BoundaryScope:

  private var _current: Option[AsyncBoundaryCtx] = None

  /** Runs `body` with `ctx` as the active boundary context, then restores the previous context. */
  def withContext[A](ctx: AsyncBoundaryCtx)(body: => A): A =
    val prev = _current
    _current = Some(ctx)
    try body
    finally _current = prev

  /** Called by [[Await]] to register a Future with the currently active boundary, if any. */
  def register(f: Future[Any]): Unit =
    _current.foreach(_.register(f))

/** Tracks the count of pending Futures registered during a single [[Boundary]] render pass.
  *
  * When all registered futures have settled (regardless of success or failure) `onAllResolved`
  * is invoked so the boundary can swap the pending UI for the real children content.
  * [[Await]] handles both `Success` and `Failure` cases via its own pattern-match
  * (Case X design decision), so the boundary only needs to know when ALL futures are done.
  */
class AsyncBoundaryCtx:
  private var _count            = 0
  var onAllResolved: () => Unit = () => ()

  def register(f: Future[Any]): Unit =
    _count += 1
    f.onComplete { _ =>
      _count -= 1
      if _count == 0 then onAllResolved()
    }(scala.concurrent.ExecutionContext.Implicits.global)

  def hasPending: Boolean = _count > 0
