/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.ziohttp

import zio.ZIO

/** Bridges ZIO to the effect type classes `meltkit` needs, keeping `R` as a type parameter.
  *
  * `MeltKit` takes a unary type constructor, so ZIO is passed as the type lambda
  * `[A] =>> ZIO[R, Throwable, A]`. The core needs no change: a user writes one type alias and
  * keeps their environment, so `ZIO.service` / `ZLayer` stay available inside handlers.
  *
  * {{{
  * type Env    = PostStore
  * type App[A] = ZIO[Env, Throwable, A]
  *
  * import meltkit.adapter.ziohttp.ZioInstances.given
  *
  * val app = MeltKit[App]()
  * app.get("posts") { ctx => ZIO.serviceWithZIO[PostStore](_.list).map(ctx.ok(_)) }
  * }}}
  *
  * None of these instances runs an effect — the adapter only builds `Routes[R, Response]` and
  * `Server.serve` does the running — so no `Runtime[R]` is required anywhere.
  */
object ZioInstances:

  /** ZIO fixed to a `Throwable` error channel, as a unary type constructor. */
  type ZTask[R] = [A] =>> ZIO[R, Throwable, A]

  given zioFunctor[R]: meltkit.Functor[ZTask[R]] with
    def map[A, B](fa: ZIO[R, Throwable, A])(f: A => B): ZIO[R, Throwable, B] = fa.map(f)

  given zioFlatMap[R]: meltkit.FlatMap[ZTask[R]] with
    def flatMap[A, B](fa: ZIO[R, Throwable, A])(f: A => ZIO[R, Throwable, B]): ZIO[R, Throwable, B] =
      fa.flatMap(f)

  given zioPure[R]: meltkit.Pure[ZTask[R]] with
    def pure[A](a: A): ZIO[R, Throwable, A] = ZIO.succeed(a)

  given zioDefer[R]: meltkit.Defer[ZTask[R]] with
    def defer[A](fa: => ZIO[R, Throwable, A]): ZIO[R, Throwable, A] = ZIO.suspendSucceed(fa)

  /** Catches defects as well as typed failures.
    *
    * `fa.either` would only surface the typed error channel, and Melt's render paths throw
    * synchronously (`throw missingTemplate` in the SSR contexts, `UnsupportedOperationException`
    * in `ServerMeltContext`'s defaults). Those become `Cause.Die` and would escape a plain
    * `either`, taking down the request instead of producing the 500 the http4s adapter returns.
    * `ZIO.suspend` does not help: the throw happens when the effect runs, not when it is built.
    */
  given zioRecover[R]: meltkit.Recover[ZTask[R]] with
    def attempt[A](fa: ZIO[R, Throwable, A]): ZIO[R, Throwable, Either[Throwable, A]] =
      fa.sandbox.either.map(_.left.map(_.squash))

  given zioParallel[R]: meltkit.Parallel[ZTask[R]] with
    def parTraverse[A, B](as: List[A])(f: A => ZIO[R, Throwable, B]): ZIO[R, Throwable, List[B]] =
      ZIO.foreachPar(as)(f).map(_.toList)
