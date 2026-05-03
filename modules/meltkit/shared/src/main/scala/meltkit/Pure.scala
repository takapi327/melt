/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.concurrent.Future

/** A minimal typeclass for lifting a pure value into an effect `F`.
  *
  * MeltKit core depends only on this trait — not on any specific effect library.
  * A built-in instance for [[scala.concurrent.Future]] is provided in the companion object.
  * For cats-based effects (e.g. `cats.effect.IO`), import the bridge given from an adapter module.
  */
trait Pure[F[_]]:
  def pure[A](a: A): F[A]

object Pure:
  def apply[F[_]](using p: Pure[F]): Pure[F] = p

  /** Built-in [[Pure]] instance for [[scala.concurrent.Future]]. */
  given Pure[Future] with
    override def pure[A](a: A): Future[A] = Future.successful(a)
