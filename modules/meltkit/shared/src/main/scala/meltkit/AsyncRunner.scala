/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** A typeclass for running `F[Response]` effects in a fire-and-forget fashion.
  *
  * Used by [[BrowserAdapter]] to execute route handlers without blocking.
  * Provide a given instance for your effect type:
  *
  * {{{
  * import cats.effect.IO
  * import cats.effect.unsafe.implicits.global
  *
  * given AsyncRunner[IO] with
  *   def runAndForget(fa: IO[Response]): Unit = fa.unsafeRunAndForget()
  * }}}
  *
  * @see [[SyncRunner]] for blocking execution used in SSG.
  */
trait AsyncRunner[F[_]]:
  def runAndForget(fa: F[Response]): Unit

object AsyncRunner:
  def apply[F[_]](using r: AsyncRunner[F]): AsyncRunner[F] = r
