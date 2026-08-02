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

  test("sibling providers do not leak across owner subtrees") {
    val ctx = Context.create("default")
    var nodeA: OwnerNode = null
    var nodeB: OwnerNode = null
    Owner.withNew {
      val (_, a) = Owner.withNew { ctx.provide("A") }
      val (_, b) = Owner.withNew { ctx.provide("B") }
      nodeA = a
      nodeB = b
    }
    // Both subtrees are alive. Each must see its own provider, not whoever
    // pushed last (the failure mode of a global temporal stack).
    assertEquals(Owner.withOwner(nodeA)(ctx.inject()), Some("A"))
    assertEquals(Owner.withOwner(nodeB)(ctx.inject()), Some("B"))
  }

  test("child injects the ancestor's value during nested construction (generated flow)") {
    val ctx  = Context.create("default")
    var seen = "unset"
    Owner.withNew { // parent component apply()
      ctx.provide("parent")
      Owner.withNew { // child component apply(), nested in parent
        seen = ctx.inject() // natural inject during the child's own setup
      }
    }
    assertEquals(seen, "parent")
  }

  test("inject walks up owner ancestry to the nearest provider") {
    val ctx = Context.create("default")
    var leaf: OwnerNode = null
    Owner.withNew {
      ctx.provide("root")
      Owner.withNew { // intermediate node with no provider
        val (_, l) = Owner.withNew {} // leaf
        leaf = l
      }
    }
    assertEquals(Owner.withOwner(leaf)(ctx.inject()), Some("root"))
  }

  test("nearest owner provider overrides an ancestor's") {
    val ctx = Context.create("default")
    var inner: OwnerNode = null
    Owner.withNew {
      ctx.provide("outer")
      val (_, i) = Owner.withNew { ctx.provide("inner") }
      inner = i
    }
    assertEquals(Owner.withOwner(inner)(ctx.inject()), Some("inner"))
  }

  test("provided context dies with its owner node") {
    val ctx = Context.create("default")
    var node: OwnerNode = null
    Owner.withNew {
      val (_, n) = Owner.withNew { ctx.provide("scoped") }
      node = n
    }
    assertEquals(Owner.withOwner(node)(ctx.inject()), Some("scoped"))
    node.destroy()
    // A destroyed node can no longer be entered, so the value is gone.
    assertEquals(Owner.withOwner(node)(ctx.inject()), None)
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
