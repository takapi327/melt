/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.concurrent.Future
import scala.util.{ Failure, Success }

/** A typeclass for blocking execution of `F[A]` effects.
  *
  * Used by [[meltkit.ssg.SsgGenerator]] to run route handlers synchronously
  * at build time. MeltKit core does not depend on any specific effect library,
  * so this typeclass keeps `meltkit-ssg` free of a cats-effect dependency.
  *
  * Provide a given instance in your SSG object (your app already depends on
  * cats-effect via `meltkit-adapter-http4s` or similar):
  *
  * {{{
  * import cats.effect.unsafe.implicits.global
  *
  * given SyncRunner[IO] with
  *   def runSync[A](fa: IO[A]): A = fa.unsafeRunSync()
  * }}}
  *
  * @see [[AsyncRunner]] for fire-and-forget execution used in the browser.
  */
trait SyncRunner[F[_]]:
  def runSync[A](fa: F[A]): A

object SyncRunner:
  def apply[F[_]](using r: SyncRunner[F]): SyncRunner[F] = r

  /** [[SyncRunner]] for [[scala.concurrent.Future]].
    *
    * Extracts the value immediately via [[Future.value]] without blocking.
    * SSG handlers must complete synchronously (i.e. return `Future.successful`);
    * a not-yet-completed `Future` will throw at runtime.
    */
  given SyncRunner[Future] with
    def runSync[A](fa: Future[A]): A = fa.value match
      case Some(Success(v)) => v
      case Some(Failure(e)) => throw e
      case None             =>
        throw new RuntimeException(
          "[meltkit-ssg] Future was not completed synchronously. SSG handlers must be synchronous."
        )

  /** [[SyncRunner]] for the identity effect `[A] =>> A` (synchronous, no wrapping). */
  given SyncRunner[[A] =>> A] with
    def runSync[A](fa: A): A = fa
