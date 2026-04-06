/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

class CleanupSpec extends munit.FunSuite:

  test("pushScope/register/popScope collects cleanups") {
    Cleanup.pushScope()
    var ran = false
    Cleanup.register(() => ran = true)
    val cleanups = Cleanup.popScope()
    assertEquals(cleanups.length, 1)
    assert(!ran)
    Cleanup.runAll(cleanups)
    assert(ran)
  }

  test("popScope without pushScope returns empty list") {
    val cleanups = Cleanup.popScope()
    assertEquals(cleanups, Nil)
  }

  test("register without active scope is a no-op") {
    // Should not throw
    Cleanup.register(() => ())
  }

  test("nested scopes are independent") {
    Cleanup.pushScope()
    Cleanup.register(() => ())
    Cleanup.pushScope()
    Cleanup.register(() => ())
    Cleanup.register(() => ())
    val inner = Cleanup.popScope()
    val outer = Cleanup.popScope()
    assertEquals(inner.length, 2)
    assertEquals(outer.length, 1)
  }

  test("runAll executes all cleanups in order") {
    val order = scala.collection.mutable.ListBuffer.empty[Int]
    Cleanup.pushScope()
    Cleanup.register(() => order += 1)
    Cleanup.register(() => order += 2)
    Cleanup.register(() => order += 3)
    val cleanups = Cleanup.popScope()
    Cleanup.runAll(cleanups)
    assertEquals(order.toList, List(1, 2, 3))
  }

  test("onCleanup convenience function registers in current scope") {
    Cleanup.pushScope()
    var ran = false
    onCleanup(() => ran = true)
    val cleanups = Cleanup.popScope()
    Cleanup.runAll(cleanups)
    assert(ran)
  }
