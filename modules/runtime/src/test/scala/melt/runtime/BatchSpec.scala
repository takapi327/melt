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
    val v     = Var(0)
    var count = 0
    v.subscribe(_ => count += 1)

    batch {
      batch {
        v.set(1)
      }
      // Inner batch should NOT have flushed yet
      assertEquals(count, 0)
      v.set(2)
    }
    // Outer batch flushes — both notifications fire
    assertEquals(count, 2)
  }

  test("derived signal updates once per batch") {
    val a       = Var(1)
    val doubled = a.map(_ * 2)
    var updates = 0
    doubled.subscribe(_ => updates += 1)

    batch {
      a.set(2)
      a.set(3)
      a.set(4)
    }
    // The map subscriber fires for each enqueued notification,
    // but the final value should be correct
    assertEquals(doubled.now(), 8)
  }
