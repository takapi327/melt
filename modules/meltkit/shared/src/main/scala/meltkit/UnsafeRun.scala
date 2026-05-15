/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Success, Failure }

/** A typeclass for executing an effect `F[A]` asynchronously and delivering the
  * result to a callback.
  *
  * [[NodeServerAdapter]] needs this to bridge `F[Response]` into the node:http
  * callback `(req, res) => Unit`. `Functor` / `Pure` / `Defer` alone cannot
  * "run" an `F` value — this typeclass fills that gap.
  *
  * A built-in instance for [[scala.concurrent.Future]] is provided in the
  * companion object. For cats-effect IO, import the bridge given from the
  * http4s adapter module.
  *
  * @see [[AsyncRunner]] for the browser-side fire-and-forget variant
  */
trait UnsafeRun[F[_]]:
  def unsafeRunAsync[A](fa: F[A])(cb: Either[Throwable, A] => Unit): Unit

object UnsafeRun:
  def apply[F[_]](using r: UnsafeRun[F]): UnsafeRun[F] = r

  /** Built-in [[UnsafeRun]] instance for [[scala.concurrent.Future]]. */
  given (using ec: ExecutionContext): UnsafeRun[Future] with
    def unsafeRunAsync[A](fa: Future[A])(cb: Either[Throwable, A] => Unit): Unit =
      fa.onComplete {
        case Success(a) => cb(Right(a))
        case Failure(e) => cb(Left(e))
      }
