/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import meltkit.NavGuards

/** Unit tests for the DOM-free navigation-guard decision logic. */
class NavGuardsTest extends munit.FunSuite:

  override def afterEach(context: AfterEach): Unit = NavGuards.clear()

  test("no guards → navigation is always allowed") {
    assert(NavGuards.allows("/a", "/b"))
  }

  test("a guard returning false cancels navigation") {
    NavGuards.register((_, _) => false)
    assert(!NavGuards.allows("/a", "/b"))
  }

  test("all guards must permit for navigation to proceed") {
    NavGuards.register((_, _) => true)
    NavGuards.register((_, to) => to != "/blocked")
    assert(NavGuards.allows("/a", "/ok"))
    assert(!NavGuards.allows("/a", "/blocked"))
  }

  test("guards receive the from and to paths") {
    var seen: (String, String) = ("", "")
    NavGuards.register { (from, to) => seen = (from, to); true }
    NavGuards.allows("/from", "/to")
    assertEquals(seen, ("/from", "/to"))
  }

  test("unregister removes the guard") {
    val off = NavGuards.register((_, _) => false)
    assert(!NavGuards.allows("/a", "/b"))
    off()
    assert(NavGuards.allows("/a", "/b"))
    assertEquals(NavGuards.size, 0)
  }
