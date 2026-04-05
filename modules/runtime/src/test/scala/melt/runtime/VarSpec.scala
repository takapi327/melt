/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

class VarSpec extends munit.FunSuite:

  // ── Basic API ──────────────────────────────────────────────────────────────

  test("now() returns the initial value") {
    val v = Var(42)
    assertEquals(v.now(), 42)
  }

  test("set() updates the current value") {
    val v = Var(0)
    v.set(10)
    assertEquals(v.now(), 10)
  }

  test("update() transforms the current value") {
    val v = Var(3)
    v.update(_ * 2)
    assertEquals(v.now(), 6)
  }

  // ── subscribe ─────────────────────────────────────────────────────────────

  test("subscribe() is called when value changes") {
    val v       = Var(0)
    var received = -1
    v.subscribe(x => received = x)
    v.set(7)
    assertEquals(received, 7)
  }

  test("subscribe() returns an unsubscribe function that stops notifications") {
    val v        = Var(0)
    var received = 0
    val cancel   = v.subscribe(_ => received += 1)
    v.set(1)
    cancel()
    v.set(2)
    assertEquals(received, 1)
  }

  test("multiple subscribers are all notified") {
    val v  = Var(0)
    var a  = 0
    var b  = 0
    v.subscribe(x => a = x)
    v.subscribe(x => b = x * 10)
    v.set(3)
    assertEquals(a, 3)
    assertEquals(b, 30)
  }

  // ── map / Signal ──────────────────────────────────────────────────────────

  test("map() creates a derived Signal with the transformed initial value") {
    val v      = Var(5)
    val doubled = v.map(_ * 2)
    assertEquals(doubled.now(), 10)
  }

  test("map() propagates subsequent changes") {
    val v       = Var(0)
    val doubled = v.map(_ * 2)
    v.set(4)
    assertEquals(doubled.now(), 8)
  }

  test("signal property reflects Var changes") {
    val v = Var(1)
    val s = v.signal
    assertEquals(s.now(), 1)
    v.set(9)
    assertEquals(s.now(), 9)
  }

  // ── flatMap / for comprehension ───────────────────────────────────────────

  test("Phase 1 acceptance test: count + doubled + greeting") {
    val count   = Var(0)
    val doubled = count.map(_ * 2)
    assertEquals(doubled.now(), 0)
    count += 1
    assertEquals(doubled.now(), 2)

    val name = Var("Alice")
    val greeting = for
      n <- name
      d <- doubled
    yield s"$n: $d"
    assertEquals(greeting.now(), "Alice: 2")
  }

  test("flatMap() switches inner Signal when outer Var changes") {
    val flag = Var(true)
    val a    = Var(10)
    val b    = Var(20)
    val result = flag.flatMap(f => if f then a.signal else b.signal)
    assertEquals(result.now(), 10)
    flag.set(false)
    assertEquals(result.now(), 20)
    b.set(99)
    assertEquals(result.now(), 99)
  }

  test("for comprehension over two Vars yields correct Signal") {
    val x = Var(1)
    val y = Var(2)
    val sum = for
      a <- x
      b <- y
    yield a + b
    assertEquals(sum.now(), 3)
    x.set(10)
    assertEquals(sum.now(), 12)
    y.set(5)
    assertEquals(sum.now(), 15)
  }

  // ── Numeric extensions ────────────────────────────────────────────────────

  test("+= increments an Int Var") {
    val v = Var(0)
    v += 3
    assertEquals(v.now(), 3)
    v += 7
    assertEquals(v.now(), 10)
  }

  test("-= decrements an Int Var") {
    val v = Var(10)
    v -= 4
    assertEquals(v.now(), 6)
  }

  test("*= multiplies an Int Var") {
    val v = Var(3)
    v *= 4
    assertEquals(v.now(), 12)
  }

  test("+= on Long Var") {
    val v = Var(0L)
    v += 5L
    assertEquals(v.now(), 5L)
  }

  test("+= on Double Var") {
    val v = Var(1.0)
    v += 0.5
    assertEqualsDouble(v.now(), 1.5, 1e-9)
  }

  // ── String extension ──────────────────────────────────────────────────────

  test("+= appends to a String Var") {
    val v = Var("Hello")
    v += ", World"
    assertEquals(v.now(), "Hello, World")
  }

  // ── Boolean extension ─────────────────────────────────────────────────────

  test("toggle() flips a Boolean Var") {
    val v = Var(false)
    v.toggle()
    assertEquals(v.now(), true)
    v.toggle()
    assertEquals(v.now(), false)
  }

  // ── Collection extensions ─────────────────────────────────────────────────

  test("append() adds an item to the end") {
    val v = Var(List(1, 2))
    v.append(3)
    assertEquals(v.now(), List(1, 2, 3))
  }

  test("prepend() adds an item to the front") {
    val v = Var(List(2, 3))
    v.prepend(1)
    assertEquals(v.now(), List(1, 2, 3))
  }

  test("removeWhere() removes matching items") {
    val v = Var(List(1, 2, 3, 4))
    v.removeWhere(_ % 2 == 0)
    assertEquals(v.now(), List(1, 3))
  }

  test("removeAt() removes item at index") {
    val v = Var(List("a", "b", "c"))
    v.removeAt(1)
    assertEquals(v.now(), List("a", "c"))
  }

  test("mapItems() transforms all items") {
    val v = Var(List(1, 2, 3))
    v.mapItems(_ * 10)
    assertEquals(v.now(), List(10, 20, 30))
  }

  test("updateWhere() updates matching items only") {
    case class Item(id: Int, done: Boolean)
    val v = Var(List(Item(1, false), Item(2, false), Item(3, false)))
    v.updateWhere(_.id == 2)(_.copy(done = true))
    assertEquals(v.now(), List(Item(1, false), Item(2, true), Item(3, false)))
  }

  test("clear() empties the list") {
    val v = Var(List(1, 2, 3))
    v.clear()
    assertEquals(v.now(), List.empty[Int])
  }

  test("sortBy() sorts by a key") {
    val v = Var(List(3, 1, 2))
    v.sortBy(identity)
    assertEquals(v.now(), List(1, 2, 3))
  }
