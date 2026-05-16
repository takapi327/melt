/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.ssg

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.Duration

import meltkit.SyncRunner

/** Provides [[SyncRunner]] given instances for use with [[SsgGenerator]].
  *
  * Import `SsgRunner.given` to bring the appropriate instance into scope:
  *
  * {{{
  * import meltkit.ssg.SsgRunner.given   // SyncRunner[Future] or SyncRunner[[A]=>>A]
  *
  * SsgGenerator.run(MyApp.app, config)
  * }}}
  */
object SsgRunner:

  /** [[SyncRunner]] for [[scala.concurrent.Future]] that blocks via `Await.result`. */
  given SyncRunner[Future] with
    def runSync[A](fa: Future[A]): A = Await.result(fa, Duration.Inf)

  /** [[SyncRunner]] for the identity effect `[A] =>> A` (synchronous, no wrapping). */
  given SyncRunner[[A] =>> A] with
    def runSync[A](fa: A): A = fa
