/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

class LayoutEffectSpec extends munit.FunSuite:

  // ── layoutEffect basics ───────────────────────────────────────────────────

  test("layoutEffect does NOT run on initial creation") {
    val count = Var(0)
    var ran   = false
    layoutEffect(count) { _ => ran = true }
    assert(!ran, "layoutEffect must not fire at registration time")
  }

  test("layoutEffect runs when dep changes") {
    val count = Var(0)
    var last  = -1
    layoutEffect(count) { n => last = n }
    count.set(42)
    assertEquals(last, 42)
  }

  test("layoutEffect fires before effect on the same Var") {
    val count = Var(0)
    val order = scala.collection.mutable.ListBuffer.empty[String]

    layoutEffect(count) { _ => order += "pre" }
    effect(count) { _ => order += "post" }

    // effect runs immediately on registration with initial value
    assertEquals(order.toList, List("post"))

    order.clear()
    count.set(1)
    assertEquals(order.toList, List("pre", "post"))
  }

  test("layoutEffect fires before Bind-lane subscriber") {
    val count = Var(0)
    val order = scala.collection.mutable.ListBuffer.empty[String]

    layoutEffect(count) { _ => order += "pre" }
    // Simulate a Bind-lane subscriber (Bind.* uses subscribe = _bind lane)
    val cancel = count.subscribe(_ => order += "bind")

    count.set(1)
    assertEquals(order.toList, List("pre", "bind"))
    cancel()
  }

  test("effect (Post lane) fires after Bind-lane subscriber") {
    val count = Var(0)
    val order = scala.collection.mutable.ListBuffer.empty[String]

    val cancel = count.subscribe(_ => order += "bind")
    effect(count) { _ => order += "post" }

    // initial run of effect
    assertEquals(order.toList, List("post"))
    order.clear()

    count.set(1)
    assertEquals(order.toList, List("bind", "post"))
    cancel()
  }

  test("full order: layoutEffect → bind → effect") {
    val count = Var(0)
    val order = scala.collection.mutable.ListBuffer.empty[String]

    layoutEffect(count) { _ => order += "pre" }
    val cancel = count.subscribe(_ => order += "bind")
    effect(count) { _ => order += "post" }

    // only effect runs at registration
    assertEquals(order.toList, List("post"))
    order.clear()

    count.set(1)
    assertEquals(order.toList, List("pre", "bind", "post"))
    cancel()
  }

  // ── Signal overload ───────────────────────────────────────────────────────

  test("layoutEffect works with Signal") {
    val count   = Var(0)
    val doubled = count.map(_ * 2)
    val order   = scala.collection.mutable.ListBuffer.empty[String]

    layoutEffect(doubled) { _ => order += "pre" }
    val cancel = doubled.subscribe(_ => order += "bind")
    effect(doubled) { _ => order += "post" }

    assertEquals(order.toList, List("post"))
    order.clear()

    count.set(1)
    assertEquals(order.toList, List("pre", "bind", "post"))
    cancel()
  }

  // ── Two-dependency overload ───────────────────────────────────────────────

  test("two-dep layoutEffect fires before effect when both deps change via batch") {
    val a     = Var(0)
    val b     = Var(0)
    val order = scala.collection.mutable.ListBuffer.empty[String]

    layoutEffect(a, b) { (av, bv) => order += s"pre($av,$bv)" }
    effect(a, b) { (av, bv) => order += s"post($av,$bv)" }

    // initial effect run
    assertEquals(order.toList, List("post(0,0)"))
    order.clear()

    batch {
      a.set(1)
      b.set(2)
    }
    // Both deps changed in one batch: each fires once
    assertEquals(order.toList, List("pre(1,2)", "post(1,2)"))
  }

  // ── Cleanup ───────────────────────────────────────────────────────────────

  test("layoutEffect is removed from Pre lane when cleanup runs") {
    val count = Var(0)
    var calls = 0

    Cleanup.pushScope()
    layoutEffect(count) { _ => calls += 1 }
    val cleanups = Cleanup.popScope()

    count.set(1)
    assertEquals(calls, 1)

    Cleanup.runAll(cleanups)

    count.set(2)
    assertEquals(calls, 1, "layoutEffect must not fire after cleanup")
  }
