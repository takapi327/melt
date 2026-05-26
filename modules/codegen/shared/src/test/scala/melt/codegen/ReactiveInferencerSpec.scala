/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.codegen

import melt.ir.ReactiveKind
import melt.ir.ReactiveKind.{ LikelyStatic, LikelyReactive, Unknown }

/** Unit tests for [[ReactiveInferencer]]. */
class ReactiveInferencerSpec extends munit.FunSuite:

  val reactiveVars: Set[String] = Set("count", "name")

  // ── LikelyStatic ─────────────────────────────────────────────────────────

  test("LikelyStatic: integer literal") {
    assertEquals(ReactiveInferencer.infer("42"), LikelyStatic)
  }

  test("LikelyStatic: negative integer literal") {
    assertEquals(ReactiveInferencer.infer("-1"), LikelyStatic)
  }

  test("LikelyStatic: floating-point literal") {
    assertEquals(ReactiveInferencer.infer("3.14"), LikelyStatic)
  }

  test("LikelyStatic: Boolean true") {
    assertEquals(ReactiveInferencer.infer("true"), LikelyStatic)
  }

  test("LikelyStatic: Boolean false") {
    assertEquals(ReactiveInferencer.infer("false"), LikelyStatic)
  }

  test("LikelyStatic: null") {
    assertEquals(ReactiveInferencer.infer("null"), LikelyStatic)
  }

  test("LikelyStatic: string literal") {
    assertEquals(ReactiveInferencer.infer("\"hello\""), LikelyStatic)
  }

  // ── LikelyReactive ────────────────────────────────────────────────────────

  test("LikelyReactive: simple .value access") {
    assertEquals(ReactiveInferencer.infer("count.value", reactiveVars), LikelyReactive)
  }

  test("LikelyReactive: chained .value access") {
    assertEquals(ReactiveInferencer.infer("foo.bar.value", reactiveVars), LikelyReactive)
  }

  test("LikelyReactive: .value without reactiveVars set") {
    assertEquals(ReactiveInferencer.infer("x.value"), LikelyReactive)
  }

  test("LikelyReactive: bare State variable reference") {
    assertEquals(ReactiveInferencer.infer("count", reactiveVars), LikelyReactive)
  }

  test("LikelyReactive: State variable used in expression") {
    assertEquals(ReactiveInferencer.infer("count + 1", reactiveVars), LikelyReactive)
  }

  test("LikelyReactive: name variable reference") {
    assertEquals(ReactiveInferencer.infer("name", reactiveVars), LikelyReactive)
  }

  // ── Unknown ───────────────────────────────────────────────────────────────

  test("Unknown: plain identifier not in reactiveVars") {
    assertEquals(ReactiveInferencer.infer("title", reactiveVars), Unknown)
  }

  test("Unknown: method call with no reactive identifiers") {
    assertEquals(ReactiveInferencer.infer("someMethod()", reactiveVars), Unknown)
  }

  test("Unknown: empty reactiveVars and no .value") {
    assertEquals(ReactiveInferencer.infer("x + y"), Unknown)
  }

  // ── Comment / string stripping ────────────────────────────────────────────

  test("ignores .value inside a line comment") {
    assertEquals(ReactiveInferencer.infer("// count.value\nstaticStr", reactiveVars), Unknown)
  }

  test("ignores .value inside a block comment") {
    assertEquals(ReactiveInferencer.infer("/* count.value */ staticStr", reactiveVars), Unknown)
  }

  test("LikelyStatic: string literal containing .value") {
    assertEquals(ReactiveInferencer.infer("\"count.value\"", reactiveVars), LikelyStatic)
  }

  test("ignores reactive var inside a string literal") {
    assertEquals(ReactiveInferencer.infer("\"count\"", reactiveVars), LikelyStatic)
  }

  test("ignores reactive var inside a line comment") {
    assertEquals(ReactiveInferencer.infer("// count\nstaticStr", reactiveVars), Unknown)
  }
