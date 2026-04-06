/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

class UntrackSpec extends munit.FunSuite:

  test("untrack Var returns current value") {
    val v = Var(42)
    assertEquals(untrack(v), 42)
    v.set(100)
    assertEquals(untrack(v), 100)
  }

  test("untrack Signal returns current value") {
    val v = Var(1)
    val s = v.map(_ * 10)
    assertEquals(untrack(s), 10)
  }

  test("untrack block returns block result") {
    assertEquals(untrack(1 + 2 + 3), 6)
  }
