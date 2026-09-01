/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.ziohttp.test

import zio.*
import zio.test.*
import zio.test.Assertion.*

import meltkit.adapter.ziohttp.ZioInstances
import ZioInstances.given

/** The effect type-class bridges, with `Recover` under the most load.
  *
  * `Recover` is what turns a failing handler into a 500 instead of a dropped request, and ZIO's
  * error channel makes the naive implementation wrong in a way that only shows up at runtime.
  */
object ZioInstancesSpec extends ZIOSpecDefault:

  private type T[A] = ZIO[Any, Throwable, A]

  def spec = suite("ZioInstances")(
    test("Recover catches a typed failure") {
      val boom = ZIO.fail(new RuntimeException("typed"))
      summon[meltkit.Recover[T]].attempt(boom).map { r =>
        assertTrue(r.left.toOption.map(_.getMessage).contains("typed"))
      }
    },
    test("Recover catches a defect thrown while the effect runs") {
      // Regression: `fa.either` only surfaces the typed channel, so a synchronous `throw` inside
      // `ZIO.succeed` stays a `Cause.Die` and escapes. Melt's render paths throw exactly like
      // this (`throw missingTemplate`), so with `either` the 500 would never be produced.
      val boom: T[Int] = ZIO.succeed[Int](throw new RuntimeException("defect"))
      summon[meltkit.Recover[T]].attempt(boom).map { r =>
        assertTrue(r.left.toOption.map(_.getMessage).contains("defect"))
      }
    },
    test("Recover passes a success through") {
      summon[meltkit.Recover[T]].attempt(ZIO.succeed(1)).map(r => assertTrue(r == Right(1)))
    },
    test("Defer does not evaluate its argument until the effect runs") {
      val counter = new java.util.concurrent.atomic.AtomicInteger(0)
      val eff     = summon[meltkit.Defer[T]].defer { counter.incrementAndGet(); ZIO.unit }
      val before  = counter.get()
      eff.map(_ => assertTrue(before == 0, counter.get() == 1))
    },
    test("Functor / FlatMap / Pure compose as expected") {
      val f  = summon[meltkit.Functor[T]]
      val fm = summon[meltkit.FlatMap[T]]
      val p  = summon[meltkit.Pure[T]]
      fm.flatMap(p.pure(2))(a => f.map(p.pure(a * 3))(_ + 1)).map(r => assertTrue(r == 7))
    },
    test("Parallel traverses every element") {
      summon[meltkit.Parallel[T]]
        .parTraverse(List(1, 2, 3))(a => ZIO.succeed(a * 2))
        .map(r => assertTrue(r == List(2, 4, 6)))
    },
    test("the instances resolve for a ZIO with a non-Any environment") {
      // The point of keeping `R`: the bridges must apply to `ZIO[R, Throwable, *]`, not just Task.
      trait Store
      type App[A] = ZIO[Store, Throwable, A]
      val p = summon[meltkit.Pure[App]]
      p.pure(1).provideEnvironment(ZEnvironment(new Store {})).map(r => assertTrue(r == 1))
    }
  )
