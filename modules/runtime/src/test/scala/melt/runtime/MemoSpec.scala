/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

class MemoSpec extends munit.FunSuite:

  test("memo suppresses unchanged values") {
    Cleanup.pushScope()
    val v       = Var(0)
    val isEven  = memo(v)(_ % 2 == 0)
    var updates = 0
    isEven.subscribe(_ => updates += 1)

    v.set(2) // still even — no propagation
    assertEquals(updates, 0)
    assertEquals(isEven.now(), true)

    v.set(3) // now odd — propagation
    assertEquals(updates, 1)
    assertEquals(isEven.now(), false)

    v.set(5) // still odd — no propagation
    assertEquals(updates, 1)
    Cleanup.popScope()
  }

  test("memo propagates when value changes") {
    Cleanup.pushScope()
    val v     = Var(1)
    val clamped = memo(v)(n => Math.min(n, 10))
    assertEquals(clamped.now(), 1)
    v.set(5)
    assertEquals(clamped.now(), 5)
    v.set(15)
    assertEquals(clamped.now(), 10)
    v.set(20)
    assertEquals(clamped.now(), 10) // unchanged — no downstream propagation
    Cleanup.popScope()
  }
