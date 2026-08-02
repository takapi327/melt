/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import meltkit.ScrollPositions

/** Unit tests for the DOM-free per-entry scroll-position bookkeeping. */
class ScrollPositionsTest extends munit.FunSuite:

  override def afterEach(context: AfterEach): Unit = ScrollPositions.clear()

  test("unseen key restores to the top (0, 0)") {
    assertEquals(ScrollPositions.restore(42L), (0.0, 0.0))
    assert(!ScrollPositions.has(42L))
  }

  test("save then restore returns the recorded offset") {
    ScrollPositions.save(1L, 0.0, 250.0)
    assertEquals(ScrollPositions.restore(1L), (0.0, 250.0))
    assert(ScrollPositions.has(1L))
  }

  test("saving again overwrites the previous offset for the same key") {
    ScrollPositions.save(1L, 0.0, 100.0)
    ScrollPositions.save(1L, 0.0, 900.0)
    assertEquals(ScrollPositions.restore(1L), (0.0, 900.0))
  }

  test("distinct keys keep independent offsets") {
    ScrollPositions.save(1L, 0.0, 100.0)
    ScrollPositions.save(2L, 0.0, 700.0)
    assertEquals(ScrollPositions.restore(1L), (0.0, 100.0))
    assertEquals(ScrollPositions.restore(2L), (0.0, 700.0))
  }
