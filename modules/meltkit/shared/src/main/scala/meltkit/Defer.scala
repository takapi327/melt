/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/** A minimal typeclass for suspending the evaluation of an effectful computation.
  *
  * `defer(fa)` guarantees that `fa` is not evaluated until the `F` computation
  * is actually executed by the runtime. The route handler is not invoked until
  * the effect chain reaches the deferred point, ensuring middleware can set
  * [[Locals]] before
  * the handler reads them.
  *
  * Without `defer`, route handlers are invoked eagerly at request-description time,
  * before any middleware effects have executed.  This means reads like
  * `IO.pure(ctx.locals.get(key))` silently return `None` even though middleware
  * subsequently calls `locals.set`.  Wrapping the handler invocation in `defer`
  * makes the read happen at execution time — after middleware has run.
  *
  * A built-in instance for [[scala.concurrent.Future]] is provided in the companion
  * object. For cats-based effects (e.g. `cats.effect.IO`), import the bridge
  * `given` from the adapter module (`Http4sAdapter.given`).
  */
trait Defer[F[_]]:
  def defer[A](fa: => F[A]): F[A]

object Defer:
  def apply[F[_]](using d: Defer[F]): Defer[F] = d

  /** Built-in [[Defer]] instance for [[scala.concurrent.Future]].
    *
    * Schedules a completed `Future(())` and chains `fa` via `flatMap`, so that
    * `fa` (the by-name argument) is evaluated on the [[ExecutionContext]] at
    * execution time rather than at call-site.
    */
  given (using ec: ExecutionContext): Defer[Future] with
    def defer[A](fa: => Future[A]): Future[A] =
      Future(()).flatMap(_ => fa)
