/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.concurrent.{ ExecutionContext, Future }

/** A minimal typeclass for running many effects concurrently and collecting
  * their results.
  *
  * MeltKit core depends only on this trait — not on any specific effect library.
  * The async-SSR foundation uses it to resolve several suspense boundaries in
  * parallel (`resolveAll`) so a slow boundary does not delay the others.
  *
  * A built-in instance for [[scala.concurrent.Future]] is provided here (eager
  * `Future`s start on construction, so `Future.sequence(as.map(f))` is genuinely
  * concurrent); for cats-based effects (e.g. `cats.effect.IO`), import the bridge
  * given from an adapter module.
  */
trait Parallel[F[_]]:
  def parTraverse[A, B](as: List[A])(f: A => F[B]): F[List[B]]

object Parallel:
  def apply[F[_]](using p: Parallel[F]): Parallel[F] = p

  /** Built-in [[Parallel]] instance for [[scala.concurrent.Future]]. */
  given (using ec: ExecutionContext): Parallel[Future] with
    override def parTraverse[A, B](as: List[A])(f: A => Future[B]): Future[List[B]] =
      Future.sequence(as.map(f))
