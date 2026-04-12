/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

class ContextSpec extends munit.FunSuite:

  test("inject returns default when no provider") {
    val ctx = Context.create("light")
    assertEquals(ctx.inject(), "light")
  }

  test("provide then inject returns provided value") {
    Cleanup.pushScope()
    val ctx = Context.create("light")
    ctx.provide("dark")
    assertEquals(ctx.inject(), "dark")
    Cleanup.runAll(Cleanup.popScope())
  }

  test("nested provide — inner overrides outer") {
    Cleanup.pushScope()
    val ctx = Context.create("default")
    ctx.provide("outer")
    assertEquals(ctx.inject(), "outer")

    Cleanup.pushScope()
    ctx.provide("inner")
    assertEquals(ctx.inject(), "inner")
    Cleanup.runAll(Cleanup.popScope())

    // After inner scope cleanup, outer value is restored
    assertEquals(ctx.inject(), "outer")
    Cleanup.runAll(Cleanup.popScope())

    // After outer scope cleanup, default is restored
    assertEquals(ctx.inject(), "default")
  }

  test("OptionalContext returns None when no provider") {
    val ctx = Context.createOptional[String]
    assertEquals(ctx.inject(), None)
  }

  test("OptionalContext returns Some when provided") {
    Cleanup.pushScope()
    val ctx = Context.createOptional[Int]
    ctx.provide(42)
    assertEquals(ctx.inject(), Some(42))
    Cleanup.runAll(Cleanup.popScope())
    assertEquals(ctx.inject(), None)
  }
