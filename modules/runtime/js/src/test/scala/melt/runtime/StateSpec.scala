/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

class StateSpec extends munit.FunSuite:

  // ── Basic API ──────────────────────────────────────────────────────────────

  test("now() returns the initial value") {
    val v = State(42)
    assertEquals(v.value, 42)
  }

  test("set() updates the current value") {
    val v = State(0)
    v.set(10)
    assertEquals(v.value, 10)
  }

  test("update() transforms the current value") {
    val v = State(3)
    v.update(_ * 2)
    assertEquals(v.value, 6)
  }

  // ── subscribe ─────────────────────────────────────────────────────────────

  test("subscribe() is called when value changes") {
    val v        = State(0)
    var received = -1
    v.subscribe(x => received = x)
    v.set(7)
    assertEquals(received, 7)
  }

  test("subscribe() returns an unsubscribe function that stops notifications") {
    val v        = State(0)
    var received = 0
    val cancel   = v.subscribe(_ => received += 1)
    v.set(1)
    cancel()
    v.set(2)
    assertEquals(received, 1)
  }

  test("multiple subscribers are all notified") {
    val v = State(0)
    var a = 0
    var b = 0
    v.subscribe(x => a = x)
    v.subscribe(x => b = x * 10)
    v.set(3)
    assertEquals(a, 3)
    assertEquals(b, 30)
  }

  // ── map / Signal ──────────────────────────────────────────────────────────

  test("map() creates a derived Signal with the transformed initial value") {
    val v       = State(5)
    val doubled = v.map(_ * 2)
    assertEquals(doubled.value, 10)
  }

  test("map() propagates subsequent changes") {
    val v       = State(0)
    val doubled = v.map(_ * 2)
    v.set(4)
    assertEquals(doubled.value, 8)
  }

  test("signal property reflects State changes") {
    val v = State(1)
    val s = v.signal
    assertEquals(s.value, 1)
    v.set(9)
    assertEquals(s.value, 9)
  }

  // ── flatMap / for comprehension ───────────────────────────────────────────

  test("Phase 1 acceptance test: count + doubled + greeting") {
    val count   = State(0)
    val doubled = count.map(_ * 2)
    assertEquals(doubled.value, 0)
    count += 1
    assertEquals(doubled.value, 2)

    val name     = State("Alice")
    val greeting = for
      n <- name
      d <- doubled
    yield s"$n: $d"
    assertEquals(greeting.value, "Alice: 2")
  }

  test("flatMap() switches inner Signal when outer State changes") {
    val flag   = State(true)
    val a      = State(10)
    val b      = State(20)
    val result = flag.flatMap(f => if f then a.signal else b.signal)
    assertEquals(result.value, 10)
    flag.set(false)
    assertEquals(result.value, 20)
    b.set(99)
    assertEquals(result.value, 99)
  }

  test("for comprehension over two Vars yields correct Signal") {
    val x   = State(1)
    val y   = State(2)
    val sum = for
      a <- x
      b <- y
    yield a + b
    assertEquals(sum.value, 3)
    x.set(10)
    assertEquals(sum.value, 12)
    y.set(5)
    assertEquals(sum.value, 15)
  }

  // ── Numeric extensions ────────────────────────────────────────────────────

  test("+= increments an Int State") {
    val v = State(0)
    v += 3
    assertEquals(v.value, 3)
    v += 7
    assertEquals(v.value, 10)
  }

  test("-= decrements an Int State") {
    val v = State(10)
    v -= 4
    assertEquals(v.value, 6)
  }

  test("*= multiplies an Int State") {
    val v = State(3)
    v *= 4
    assertEquals(v.value, 12)
  }

  test("+= on Long State") {
    val v = State(0L)
    v += 5L
    assertEquals(v.value, 5L)
  }

  test("+= on Double State") {
    val v = State(1.0)
    v += 0.5
    assertEqualsDouble(v.value, 1.5, 1e-9)
  }

  // ── String extension ──────────────────────────────────────────────────────

  test("+= appends to a String State") {
    val v = State("Hello")
    v += ", World"
    assertEquals(v.value, "Hello, World")
  }

  // ── Boolean extension ─────────────────────────────────────────────────────

  test("toggle() flips a Boolean State") {
    val v = State(false)
    v.toggle()
    assertEquals(v.value, true)
    v.toggle()
    assertEquals(v.value, false)
  }

  // ── Collection extensions ─────────────────────────────────────────────────

  test("append() adds an item to the end") {
    val v = State(List(1, 2))
    v.append(3)
    assertEquals(v.value, List(1, 2, 3))
  }

  test("prepend() adds an item to the front") {
    val v = State(List(2, 3))
    v.prepend(1)
    assertEquals(v.value, List(1, 2, 3))
  }

  test("removeWhere() removes matching items") {
    val v = State(List(1, 2, 3, 4))
    v.removeWhere(_ % 2 == 0)
    assertEquals(v.value, List(1, 3))
  }

  test("removeAt() removes item at index") {
    val v = State(List("a", "b", "c"))
    v.removeAt(1)
    assertEquals(v.value, List("a", "c"))
  }

  test("mapItems() transforms all items") {
    val v = State(List(1, 2, 3))
    v.mapItems(_ * 10)
    assertEquals(v.value, List(10, 20, 30))
  }

  test("updateWhere() updates matching items only") {
    case class Item(id: Int, done: Boolean)
    val v = State(List(Item(1, false), Item(2, false), Item(3, false)))
    v.updateWhere(_.id == 2)(_.copy(done = true))
    assertEquals(v.value, List(Item(1, false), Item(2, true), Item(3, false)))
  }

  test("clear() empties the list") {
    val v = State(List(1, 2, 3))
    v.clear()
    assertEquals(v.value, List.empty[Int])
  }

  test("sortBy() sorts by a key") {
    val v = State(List(3, 1, 2))
    v.sortBy(identity)
    assertEquals(v.value, List(1, 2, 3))
  }
