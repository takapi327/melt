/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Success

/** A minimal typeclass for capturing a failure in `F` as a value instead of
  * propagating it.
  *
  * MeltKit core depends only on this trait — not on any specific effect library.
  * [[ServerMeltKitPlatform.serve]] uses it to isolate each single-flight refresh:
  * a query that fails during a mutation's refresh is skipped, never failing the
  * whole request (whose mutation has already committed).
  *
  * A built-in instance for [[scala.concurrent.Future]] is provided here; for
  * cats-based effects (e.g. `cats.effect.IO`), import the bridge given from an
  * adapter module.
  */
trait Recover[F[_]]:
  /** Runs `fa`, returning `Left(error)` on failure instead of failing `F`. */
  def attempt[A](fa: F[A]): F[Either[Throwable, A]]

object Recover:
  def apply[F[_]](using r: Recover[F]): Recover[F] = r

  /** Built-in [[Recover]] instance for [[scala.concurrent.Future]]. */
  given (using ec: ExecutionContext): Recover[Future] with
    override def attempt[A](fa: Future[A]): Future[Either[Throwable, A]] =
      fa.transform(t => Success(t.toEither))
