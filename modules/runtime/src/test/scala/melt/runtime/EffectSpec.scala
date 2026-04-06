/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

class EffectSpec extends munit.FunSuite:

  test("effect runs immediately with current value") {
    Cleanup.pushScope()
    val v      = Var(10)
    var result = 0
    effect(v)(n => result = n)
    assertEquals(result, 10)
    Cleanup.popScope()
  }

  test("effect re-runs when dependency changes") {
    Cleanup.pushScope()
    val v      = Var("hello")
    var result = ""
    effect(v)(s => result = s)
    assertEquals(result, "hello")
    v.set("world")
    assertEquals(result, "world")
    Cleanup.popScope()
  }

  test("onCleanup inside effect runs before re-execution") {
    Cleanup.pushScope()
    val v         = Var(0)
    var cleanedUp = false
    effect(v) { _ =>
      onCleanup(() => cleanedUp = true)
    }
    assert(!cleanedUp)
    v.set(1) // triggers re-run → inner cleanup runs first
    assert(cleanedUp)
    Cleanup.popScope()
  }

  test("effect with Signal dependency") {
    Cleanup.pushScope()
    val v      = Var(0)
    val sig    = v.map(_ + 100)
    var result = 0
    effect(sig)(n => result = n)
    assertEquals(result, 100)
    v.set(5)
    assertEquals(result, 105)
    Cleanup.popScope()
  }

  test("managed inside effect releases on re-execution") {
    Cleanup.pushScope()
    val v        = Var(0)
    var released = false

    effect(v) { _ =>
      managed("resource", _ => released = true)
    }

    assert(!released)
    v.set(1) // triggers re-run → managed release runs
    assert(released)
    Cleanup.popScope()
  }
