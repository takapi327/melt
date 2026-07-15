/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/** A minimal typeclass for sequencing two effectful steps in `F`.
  *
  * MeltKit core depends only on this trait — not on any specific effect library.
  * It complements [[Functor]] (map) and [[Pure]] (lift) with the ability to
  * chain a dependent effect, which [[ServerMeltKitPlatform.serve]] needs to read
  * a request body and then run the server function implementation.
  *
  * Kept deliberately separate from [[Functor]] (rather than extending it) so that
  * summoning `Functor[Future]` stays unambiguous when both instances are in scope.
  * A built-in instance for [[scala.concurrent.Future]] is provided here; for
  * cats-based effects (e.g. `cats.effect.IO`), import the bridge given from an
  * adapter module.
  */
trait FlatMap[F[_]]:
  def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]

object FlatMap:
  def apply[F[_]](using fm: FlatMap[F]): FlatMap[F] = fm

  /** Built-in [[FlatMap]] instance for [[scala.concurrent.Future]]. */
  given (using ec: ExecutionContext): FlatMap[Future] with
    override def flatMap[A, B](fa: Future[A])(f: A => Future[B]): Future[B] = fa.flatMap(f)
