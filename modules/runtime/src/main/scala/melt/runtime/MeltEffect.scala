/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

/** Type class for asynchronous effect execution.
  *
  * Provides a bridge between effect types (`Future`, etc.) and the Melt runtime.
  * The default instance supports `scala.concurrent.Future`.
  */
trait MeltEffect[F[_]]:
  def runAsync[A](fa: F[A])(onSuccess: A => Unit, onError: Throwable => Unit): () => Unit
  def delay[A](a: => A): F[A]

object MeltEffect:
  given executionContext: ExecutionContext = ExecutionContext.global

  given futureEffect: MeltEffect[Future] with
    def runAsync[A](fa: Future[A])(onSuccess: A => Unit, onError: Throwable => Unit): () => Unit =
      fa.onComplete {
        case Success(a) => onSuccess(a)
        case Failure(e) => onError(e)
      }(using executionContext)
      () => () // Future is not cancellable

    def delay[A](a: => A): Future[A] = Future(a)(using executionContext)
