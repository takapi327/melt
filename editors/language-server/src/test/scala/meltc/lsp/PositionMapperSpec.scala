/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.lsp

class PositionMapperSpec extends munit.FunSuite:

  private def mapper(
    script: Option[(Int, Int)] = None,
    style:  Option[(Int, Int)] = None
  ): PositionMapper =
    PositionMapper(
      script.map { case (s, e) => LineRange(s, e) },
      style.map { case (s, e) => LineRange(s, e) }
    )

  // ── sectionAt ────────────────────────────────────────────────────────────

  test("sectionAt returns Script for lines inside scriptRange") {
    val m = mapper(script = Some((1, 5)))
    assertEquals(m.sectionAt(1), MeltSection.Script)
    assertEquals(m.sectionAt(3), MeltSection.Script)
    assertEquals(m.sectionAt(5), MeltSection.Script)
  }

  test("sectionAt returns Template for lines outside scriptRange and styleRange") {
    val m = mapper(script = Some((1, 3)), style = Some((7, 9)))
    assertEquals(m.sectionAt(0), MeltSection.Template)
    assertEquals(m.sectionAt(4), MeltSection.Template)
    assertEquals(m.sectionAt(5), MeltSection.Template)
    assertEquals(m.sectionAt(6), MeltSection.Template)
  }

  test("sectionAt returns Style for lines inside styleRange") {
    val m = mapper(style = Some((10, 12)))
    assertEquals(m.sectionAt(10), MeltSection.Style)
    assertEquals(m.sectionAt(11), MeltSection.Style)
    assertEquals(m.sectionAt(12), MeltSection.Style)
  }

  test("sectionAt returns Template when no ranges are defined") {
    val m = mapper()
    assertEquals(m.sectionAt(0), MeltSection.Template)
    assertEquals(m.sectionAt(99), MeltSection.Template)
  }

  test("scriptRange boundary lines are included in Script section") {
    val m = mapper(script = Some((3, 7)))
    assertEquals(m.sectionAt(2), MeltSection.Template)
    assertEquals(m.sectionAt(3), MeltSection.Script)
    assertEquals(m.sectionAt(7), MeltSection.Script)
    assertEquals(m.sectionAt(8), MeltSection.Template)
  }

  // ── isScriptLine ──────────────────────────────────────────────────────────

  test("isScriptLine returns true only for script body lines") {
    val m = mapper(script = Some((2, 4)))
    assert(!m.isScriptLine(1))
    assert(m.isScriptLine(2))
    assert(m.isScriptLine(3))
    assert(m.isScriptLine(4))
    assert(!m.isScriptLine(5))
  }

  // ── position identity mapping ─────────────────────────────────────────────

  test("virtualToMelt is the identity function") {
    val m = mapper(script = Some((1, 3)))
    assertEquals(m.virtualToMelt(1, 5), (1, 5))
    assertEquals(m.virtualToMelt(10, 0), (10, 0))
    assertEquals(m.virtualToMelt(0, 100), (0, 100))
  }

  test("meltToVirtual is the identity function") {
    val m = mapper(script = Some((1, 3)))
    assertEquals(m.meltToVirtual(1, 5), (1, 5))
    assertEquals(m.meltToVirtual(10, 0), (10, 0))
    assertEquals(m.meltToVirtual(0, 100), (0, 100))
  }

  // ── LineRange ─────────────────────────────────────────────────────────────

  test("LineRange.contains is inclusive on both ends") {
    val r = LineRange(3, 7)
    assert(!r.contains(2))
    assert(r.contains(3))
    assert(r.contains(5))
    assert(r.contains(7))
    assert(!r.contains(8))
  }

  test("LineRange with equal start and end covers single line") {
    val r = LineRange(5, 5)
    assert(!r.contains(4))
    assert(r.contains(5))
    assert(!r.contains(6))
  }
