/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/** A minimal functor typeclass for mapping over an effect `F`.
  *
  * MeltKit core depends only on this trait — not on any specific effect library.
  * A built-in instance for [[scala.concurrent.Future]] is provided in the companion object.
  * For cats-based effects (e.g. `cats.effect.IO`), import the bridge given from the adapter:
  *
  * {{{
  * import meltkit.adapter.http4s.Http4sAdapter.given
  * }}}
  */
trait Functor[F[_]]:
  def map[A, B](fa: F[A])(f: A => B): F[B]

object Functor:
  /** Built-in [[Functor]] instance for [[scala.concurrent.Future]].
    *
    * Requires an implicit [[scala.concurrent.ExecutionContext]] in scope:
    * {{{
    * given ExecutionContext = ExecutionContext.global
    *
    * val app = MeltKit[Future]()
    * app.on(getUser) { ctx => Future.successful(ctx.ok(User(1, "Alice"))) }
    * }}}
    */
  given (using ec: ExecutionContext): Functor[Future] with
    override def map[A, B](fa: Future[A])(f: A => B): Future[B] = fa.map(f)
