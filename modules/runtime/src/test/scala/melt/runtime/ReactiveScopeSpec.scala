/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

class ReactiveScopeSpec extends munit.FunSuite:

  // ── pure / unit ──────────────────────────────────────────────────────────

  test("pure provides the given value with no cleanup") {
    val scope           = ReactiveScope.pure(42)
    val (value, cancel) = scope.allocate()
    assertEquals(value, 42)
    cancel() // no-op
  }

  test("unit provides Unit with no cleanup") {
    val scope           = ReactiveScope.unit
    val (value, cancel) = scope.allocate()
    assertEquals(value, ())
    cancel()
  }

  // ── make ─────────────────────────────────────────────────────────────────

  test("make acquires and releases") {
    var acquired = false
    var released = false
    val scope    = ReactiveScope.make { acquired = true; "resource" } { _ => released = true }
    assert(!acquired)
    val (value, cancel) = scope.allocate()
    assert(acquired)
    assertEquals(value, "resource")
    assert(!released)
    cancel()
    assert(released)
  }

  test("make passes the acquired value to release") {
    val log         = scala.collection.mutable.ListBuffer[String]()
    val scope       = ReactiveScope.make("hello")(v => log += s"released: $v")
    val (_, cancel) = scope.allocate()
    cancel()
    assertEquals(log.toList, List("released: hello"))
  }

  // ── resource ─────────────────────────────────────────────────────────────

  test("resource runs acquire then release on cancel") {
    val log   = scala.collection.mutable.ListBuffer[String]()
    val scope = ReactiveScope.resource { log += "acquired" } { log += "released" }
    assert(log.isEmpty)
    val cancel = scope.allocated
    assertEquals(log.toList, List("acquired"))
    cancel()
    assertEquals(log.toList, List("acquired", "released"))
  }

  test("resource release is evaluated at cancel time, not at definition time") {
    var flag   = "initial"
    val scope  = ReactiveScope.resource { flag = "acquired" } { flag = "released" }
    val cancel = scope.allocated
    assertEquals(flag, "acquired")
    flag = "mutated"
    cancel()
    // release block re-evaluates 'flag = "released"' at cancel time
    assertEquals(flag, "released")
  }

  // ── for-comprehension (map / flatMap) ────────────────────────────────────

  test("for-comprehension composes acquire order") {
    val log     = scala.collection.mutable.ListBuffer[String]()
    val program =
      for
        a <- ReactiveScope.make { log += "acquire A"; "A" } { _ => log += "release A" }
        b <- ReactiveScope.make { log += "acquire B"; "B" } { _ => log += "release B" }
      yield (a, b)

    val (result, cancel) = program.allocate()
    assertEquals(result, ("A", "B"))
    assertEquals(log.toList, List("acquire A", "acquire B"))
    cancel()
    // LIFO: B released before A
    assertEquals(log.toList, List("acquire A", "acquire B", "release B", "release A"))
  }

  test("for-comprehension passes values between steps") {
    val program =
      for
        v1 <- ReactiveScope.pure(10)
        v2 <- ReactiveScope.pure(v1 * 2)
        v3 <- ReactiveScope.pure(v1 + v2)
      yield (v1, v2, v3)

    program.use {
      case (v1, v2, v3) =>
        assertEquals(v1, 10)
        assertEquals(v2, 20)
        assertEquals(v3, 30)
    }
  }

  // ── allocated idempotency / memoization ──────────────────────────────────

  test("allocated returns same cancel function on multiple calls") {
    var acquireCount = 0
    val scope        = ReactiveScope.make { acquireCount += 1; acquireCount } { _ => () }
    val c1           = scope.allocated
    val c2           = scope.allocated
    // _run called only once
    assertEquals(acquireCount, 1)
    c1()
    c2() // no-op (same underlying cancel, already canceled)
  }

  test("cancel is idempotent — calling twice has no double effect") {
    var releaseCount = 0
    val scope        = ReactiveScope.make("x") { _ => releaseCount += 1 }
    val cancel       = scope.allocated
    cancel()
    cancel()
    assertEquals(releaseCount, 1)
  }

  // ── use ──────────────────────────────────────────────────────────────────

  test("use provides value and cleans up after body") {
    var released = false
    val scope    = ReactiveScope.make("res") { _ => released = true }
    val result   = scope.use(v => s"got: $v")
    assertEquals(result, "got: res")
    assert(released)
  }

  test("use cleans up even if body throws") {
    var released = false
    val scope    = ReactiveScope.make("res") { _ => released = true }
    intercept[RuntimeException]:
      scope.use(_ => throw RuntimeException("boom"))
    assert(released)
  }

  // ── flatMap exception safety ──────────────────────────────────────────────

  test("flatMap releases A immediately when acquiring B fails") {
    var releasedA = false
    val scopeA    = ReactiveScope.make("A") { _ => releasedA = true }
    val scopeB    = ReactiveScope.make(throw RuntimeException("B failed")) { _ => () }
    val program   = scopeA.flatMap(_ => scopeB)

    intercept[RuntimeException]:
      program.allocate()

    assert(releasedA, "A should be released when B acquisition fails")
  }

  test("flatMap runs both cleanups even if first cleanup throws") {
    val log     = scala.collection.mutable.ListBuffer[String]()
    val scopeA  = ReactiveScope.make("A") { _ => log += "release A"; throw RuntimeException("A cleanup failed") }
    val scopeB  = ReactiveScope.make("B") { _ => log += "release B" }
    val program = scopeA.flatMap(_ => scopeB)

    val (_, cancel) = program.allocate()
    val ex          = intercept[RuntimeException](cancel())
    assertEquals(ex.getMessage, "A cleanup failed")
    // Both cleanups ran (LIFO: B then A)
    assertEquals(log.toList, List("release B", "release A"))
  }

  // ── ReactiveScope.effect ──────────────────────────────────────────────────

  test("effect runs immediately and re-runs on change") {
    val v      = Var(0)
    val log    = scala.collection.mutable.ListBuffer[Int]()
    val scope  = ReactiveScope.effect(v) { n => log += n }
    val cancel = scope.allocated

    assertEquals(log.toList, List(0))
    v.set(1)
    assertEquals(log.toList, List(0, 1))
    v.set(2)
    assertEquals(log.toList, List(0, 1, 2))
    cancel()
    v.set(3)
    // no more updates after cancel
    assertEquals(log.toList, List(0, 1, 2))
  }

  test("effect with Signal dependency") {
    val v      = Var(0)
    val sig    = v.map(_ * 10)
    val log    = scala.collection.mutable.ListBuffer[Int]()
    val cancel = ReactiveScope.effect(sig)(n => log += n).allocated

    assertEquals(log.toList, List(0))
    v.set(3)
    assertEquals(log.toList, List(0, 30))
    cancel()
  }

  test("onCleanup inside ReactiveScope.effect runs before re-execution") {
    val v      = Var(0)
    val log    = scala.collection.mutable.ListBuffer[String]()
    val cancel = ReactiveScope
      .effect(v) { n =>
        log += s"run $n"
        onCleanup(() => log += s"cleanup $n")
      }
      .allocated

    assertEquals(log.toList, List("run 0"))
    v.set(1)
    assertEquals(log.toList, List("run 0", "cleanup 0", "run 1"))
    cancel()
    assertEquals(log.toList, List("run 0", "cleanup 0", "run 1", "cleanup 1"))
  }

  // ── ReactiveScope.layoutEffect ────────────────────────────────────────────

  test("layoutEffect does not run on initial creation") {
    val v      = Var(0)
    val log    = scala.collection.mutable.ListBuffer[Int]()
    val cancel = ReactiveScope.layoutEffect(v)(n => log += n).allocated

    assert(log.isEmpty)
    v.set(1)
    assertEquals(log.toList, List(1))
    cancel()
    v.set(2)
    assertEquals(log.toList, List(1))
  }

  test("layoutEffect with Signal dependency") {
    val v      = Var(0)
    val sig    = v.map(_ + 100)
    val log    = scala.collection.mutable.ListBuffer[Int]()
    val cancel = ReactiveScope.layoutEffect(sig)(n => log += n).allocated

    assert(log.isEmpty)
    v.set(5)
    assertEquals(log.toList, List(105))
    cancel()
  }

  // ── Cleanup stack safety (Effect.scala try-finally fix) ───────────────────

  test("Cleanup stack remains balanced when effect body throws") {
    val v = Var(0)
    Cleanup.pushScope()
    // Effect body that throws — Cleanup stack must remain balanced
    try
      effect(v) { n =>
        if n == 1 then throw RuntimeException("intentional")
      }
    catch case _ => ()

    // stack was pushed before effect, pop it now — must not throw
    val cleanups = Cleanup.popScope()
    // trigger the throwing body
    try v.set(1)
    catch case _ => ()
      // stack should still be balanced (no extra pushScope left behind)
    Cleanup.runAll(cleanups)
  }
