/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

class BatchSpec extends munit.FunSuite:

  test("batch coalesces subscriber notifications") {
    val a     = Var(1)
    val b     = Var(2)
    var count = 0
    a.subscribe(_ => count += 1)
    b.subscribe(_ => count += 1)

    batch {
      a.set(10)
      b.set(20)
    }
    // Notifications deferred to end of batch — each fires once
    assertEquals(count, 2)
  }

  test("now() returns updated value inside batch") {
    val v = Var(0)
    batch {
      v.set(42)
      assertEquals(v.now(), 42)
    }
  }

  test("nested batch flushes at outermost level") {
    val a      = Var(0)
    val b      = Var(0)
    var countA = 0
    var countB = 0
    a.subscribe(_ => countA += 1)
    b.subscribe(_ => countB += 1)

    batch {
      batch {
        a.set(1)
      }
      // Inner batch should NOT have flushed yet
      assertEquals(countA, 0)
      b.set(2)
    }
    // Outer batch flushes — each Var notifies once
    assertEquals(countA, 1)
    assertEquals(countB, 1)
  }

  test("same Var set multiple times in batch notifies only once with final value") {
    val a       = Var(1)
    var updates = 0
    var lastVal = 0
    a.subscribe { v => updates += 1; lastVal = v }

    batch {
      a.set(2)
      a.set(3)
      a.set(4)
    }
    assertEquals(updates, 1, "should notify only once")
    assertEquals(lastVal, 4, "should notify with final value")
    assertEquals(a.now(), 4)
  }

  test("derived signal sees final value after batch") {
    val a = Var(1)
    Cleanup.pushScope()
    val doubled = a.map(_ * 2)
    batch {
      a.set(2)
      a.set(3)
      a.set(4)
    }
    assertEquals(doubled.now(), 8)
    Cleanup.popScope()
  }
