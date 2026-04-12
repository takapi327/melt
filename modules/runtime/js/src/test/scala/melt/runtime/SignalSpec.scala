/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

class SignalSpec extends munit.FunSuite:

  // ── now ───────────────────────────────────────────────────────────────────

  test("now() returns the current value") {
    val v = Var(7)
    val s = v.signal
    assertEquals(s.now(), 7)
    v.set(99)
    assertEquals(s.now(), 99)
  }

  // ── subscribe ─────────────────────────────────────────────────────────────

  test("subscribe() is called on each emission") {
    val v        = Var(0)
    val s        = v.signal
    var received = List.empty[Int]
    s.subscribe(x => received = received :+ x)
    v.set(1)
    v.set(2)
    assertEquals(received, List(1, 2))
  }

  test("unsubscribe function stops notifications") {
    val v      = Var(0)
    val s      = v.signal
    var count  = 0
    val cancel = s.subscribe(_ => count += 1)
    v.set(1)
    cancel()
    v.set(2)
    assertEquals(count, 1)
  }

  // ── map ───────────────────────────────────────────────────────────────────

  test("map() on Signal reflects source changes") {
    val v       = Var(2)
    val doubled = v.signal.map(_ * 2)
    assertEquals(doubled.now(), 4)
    v.set(5)
    assertEquals(doubled.now(), 10)
  }

  test("chained map() propagates through multiple layers") {
    val v = Var(1)
    val s = v.signal.map(_ + 1).map(_ * 3)
    assertEquals(s.now(), 6)
    v.set(2)
    assertEquals(s.now(), 9)
  }

  // ── flatMap / dynamic switching ───────────────────────────────────────────

  test("flatMap() switches the inner Signal when the outer changes") {
    val selector = Var(0)
    val a        = Var(100)
    val b        = Var(200)
    val sources  = List(a.signal, b.signal)
    val result   = selector.signal.flatMap(i => sources(i))
    assertEquals(result.now(), 100)
    selector.set(1)
    assertEquals(result.now(), 200)
    b.set(999)
    assertEquals(result.now(), 999)
  }

  test("flatMap() unsubscribes from the previous inner Signal") {
    val flag  = Var(true)
    val a     = Var(1)
    val b     = Var(2)
    var calls = 0
    // derived tracks changes; we count how many times the derived Signal emits
    val result = flag.signal.flatMap(f => if f then a.signal else b.signal)
    result.subscribe(_ => calls += 1)

    a.set(10) // result listens to a
    assertEquals(calls, 1)
    flag.set(false) // result now listens to b; subscription to a is cancelled
    calls = 0
    a.set(20) // should NOT trigger result
    assertEquals(calls, 0)
    b.set(30) // should trigger result
    assertEquals(calls, 1)
  }

  // ── for comprehension ─────────────────────────────────────────────────────

  test("for comprehension over Signal and Var") {
    val x   = Var(3)
    val y   = Var(4)
    val hyp = for
      a <- x.signal
      b <- y.signal
    yield math.sqrt((a * a + b * b).toDouble)
    assertEqualsDouble(hyp.now(), 5.0, 1e-9)
    x.set(5)
    y.set(12)
    assertEqualsDouble(hyp.now(), 13.0, 1e-9)
  }
