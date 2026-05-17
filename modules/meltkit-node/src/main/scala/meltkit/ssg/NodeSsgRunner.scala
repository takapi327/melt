/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.ssg

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{ Failure, Success }

import meltkit.SyncRunner

/** Provides [[SyncRunner]] given instances for use with [[NodeSsgGenerator]].
  *
  * Import `NodeSsgRunner.given` to bring the instances into scope:
  *
  * {{{
  * import meltkit.ssg.NodeSsgRunner.given
  *
  * NodeSsgGenerator.run(MyApp.app, config)
  * }}}
  */
object NodeSsgRunner:

  /** [[SyncRunner]] for [[scala.concurrent.Future]].
    *
    * Extracts the value immediately via [[Future.value]] without blocking.
    * SSG handlers must complete synchronously (i.e. return `Future.successful`);
    * a not-yet-completed `Future` will throw at runtime.
    *
    * Uses [[scala.scalajs.concurrent.JSExecutionContext]] for `map`, which is
    * always available in a Node.js environment.
    */
  given SyncRunner[Future] with
    def runSync[A](fa: Future[A]): A = fa.value match
      case Some(Success(v)) => v
      case Some(Failure(e)) => throw e
      case None             =>
        throw new RuntimeException(
          "[meltkit-ssg] Future was not completed synchronously. SSG handlers must be synchronous."
        )
    def map[A, B](fa: Future[A])(f: A => B): Future[B] = fa.map(f)
    def pure[A](a:    A):                    Future[A] = Future.successful(a)

  /** [[SyncRunner]] for the identity effect `[A] =>> A` (synchronous, no wrapping). */
  given SyncRunner[[A] =>> A] with
    def runSync[A](fa: A):            A = fa
    def map[A, B](fa:  A)(f: A => B): B = f(fa)
    def pure[A](a:     A):            A = a
