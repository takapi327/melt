/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

class ManagedSpec extends munit.FunSuite:

  test("managed acquires resource and returns it") {
    Cleanup.pushScope()
    val res = managed("hello", _ => ())
    assertEquals(res, "hello")
    Cleanup.popScope()
  }

  test("managed release runs on scope cleanup") {
    Cleanup.pushScope()
    var released = false
    managed(42, _ => released = true)
    assert(!released)
    Cleanup.runAll(Cleanup.popScope())
    assert(released)
  }

  test("managed release receives the acquired resource") {
    Cleanup.pushScope()
    var releasedValue = 0
    managed(99, v => releasedValue = v)
    Cleanup.runAll(Cleanup.popScope())
    assertEquals(releasedValue, 99)
  }
