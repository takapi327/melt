/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

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
