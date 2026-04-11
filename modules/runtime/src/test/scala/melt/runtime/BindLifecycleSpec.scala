/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import org.scalajs.dom

/** Verifies that Bind.show / Bind.list / Bind.each clean up subscriptions for removed elements.
  *
  * Each test simulates the pattern produced by ScalaCodeGen:
  *   - render functions call `Owner.withNew { ... }` and `Lifecycle.register(el, owner)`
  *   - The component wrapper uses the same pattern
  *
  * Phase 5 of the OwnerNode migration adds `Lifecycle.destroyTree(el)` calls whenever a
  * rendered element is removed from the DOM mid-lifecycle, ensuring subscriptions registered
  * during rendering are released and registry entries are removed (preventing GC leaks).
  */
class BindLifecycleSpec extends munit.FunSuite:

  /** Creates a tracked element that records when its subscription is cancelled.
    * Mirrors the pattern produced by ScalaCodeGen's `create()` method.
    */
  private def makeTrackedEl(cancelCount: => Unit): dom.Element =
    val (el, elOwner) = Owner.withNew {
      val el = dom.document.createElement("div")
      Owner.register(() => cancelCount)
      el
    }
    Lifecycle.register(el, elOwner)
    el

  // ── Bind.show ────────────────────────────────────────────────────────────

  test("Bind.show destroys old element subscriptions when swapping") {
    var cancelCalls = 0
    val condition   = Var(true)
    val container   = dom.document.createElement("div")
    val anchor      = dom.document.createComment("")
    container.appendChild(anchor)

    val (_, compOwner) = Owner.withNew {
      Bind.show(condition, _ => makeTrackedEl { cancelCalls += 1 }, anchor)
    }
    Lifecycle.register(container, compOwner)

    assertEquals(cancelCalls, 0, "initial render: no subscriptions cancelled yet")

    condition.set(false) // swap: old element (condition=true) should be destroyed
    assertEquals(cancelCalls, 1, "old element's subscription should be cleaned up on swap")

    condition.set(true) // swap again: old element (condition=false) should be destroyed
    assertEquals(cancelCalls, 2, "second old element's subscription should be cleaned up")

    Lifecycle.destroyTree(container) // final cleanup: current element destroyed
    assertEquals(cancelCalls, 3, "current element's subscription cleaned up on destroyTree")
  }

  test("Bind.show(Signal) destroys old element subscriptions when swapping") {
    var cancelCalls = 0
    val v         = Var(true)
    val condition = v.signal
    val container = dom.document.createElement("div")
    val anchor    = dom.document.createComment("")
    container.appendChild(anchor)

    val (_, compOwner) = Owner.withNew {
      Bind.show(condition, _ => makeTrackedEl { cancelCalls += 1 }, anchor)
    }
    Lifecycle.register(container, compOwner)

    assertEquals(cancelCalls, 0)

    v.set(false)
    assertEquals(cancelCalls, 1, "old element destroyed on signal swap")

    Lifecycle.destroyTree(container)
    assertEquals(cancelCalls, 2)
  }

  test("Bind.show does not double-cancel when the same element remains current") {
    // When condition doesn't actually swap (same value), subscribe callback fires
    // but the render produces a NEW element each time (no caching). Here we verify
    // that two distinct swaps produce two distinct cancels.
    var cancelCalls = 0
    val condition   = Var(true)
    val container   = dom.document.createElement("div")
    val anchor      = dom.document.createComment("")
    container.appendChild(anchor)

    val (_, compOwner) = Owner.withNew {
      Bind.show(condition, _ => makeTrackedEl { cancelCalls += 1 }, anchor)
    }
    Lifecycle.register(container, compOwner)

    condition.set(false)
    condition.set(false) // same value again → subscribe still fires → new render
    assertEquals(cancelCalls, 2, "each swap destroys the previous element exactly once")

    Lifecycle.destroyTree(container)
    assertEquals(cancelCalls, 3)
  }

  // ── Bind.list ────────────────────────────────────────────────────────────

  test("Bind.list destroys all old nodes' subscriptions on full rebuild") {
    var cancelCalls = 0
    val items       = Var(List("a", "b", "c"))
    val container   = dom.document.createElement("div")
    val anchor      = dom.document.createComment("")
    container.appendChild(anchor)

    val renderFn: String => dom.Node = _ => makeTrackedEl { cancelCalls += 1 }

    val (_, compOwner) = Owner.withNew {
      Bind.list(items, renderFn, anchor)
    }
    Lifecycle.register(container, compOwner)

    assertEquals(cancelCalls, 0, "initial render: 3 nodes created, no cancels yet")

    // Full rebuild: all 3 old nodes removed → 2 new nodes created
    items.set(List("a", "b"))
    assertEquals(cancelCalls, 3, "all 3 old nodes' subscriptions cleaned up on rebuild")

    Lifecycle.destroyTree(container) // 2 current nodes cleaned up
    assertEquals(cancelCalls, 5)
  }

  test("Bind.list(Signal) destroys all old nodes' subscriptions on full rebuild") {
    var cancelCalls = 0
    val v         = Var(List(1, 2))
    val source    = v.signal
    val container = dom.document.createElement("div")
    val anchor    = dom.document.createComment("")
    container.appendChild(anchor)

    val renderFn: Int => dom.Node = _ => makeTrackedEl { cancelCalls += 1 }

    val (_, compOwner) = Owner.withNew {
      Bind.list(source, renderFn, anchor)
    }
    Lifecycle.register(container, compOwner)

    assertEquals(cancelCalls, 0)

    v.set(List(1))
    assertEquals(cancelCalls, 2, "both old nodes destroyed on rebuild")

    Lifecycle.destroyTree(container)
    assertEquals(cancelCalls, 3)
  }

  // ── Bind.each ────────────────────────────────────────────────────────────

  test("Bind.each destroys only removed keyed items' subscriptions") {
    var cancelCalls = 0
    val items       = Var(List(1, 2, 3))
    val container   = dom.document.createElement("div")
    val anchor      = dom.document.createComment("")
    container.appendChild(anchor)

    val renderFn: Int => dom.Node = _ => makeTrackedEl { cancelCalls += 1 }

    val (_, compOwner) = Owner.withNew {
      Bind.each(items, identity[Int], renderFn, anchor)
    }
    Lifecycle.register(container, compOwner)

    assertEquals(cancelCalls, 0, "3 items rendered, no cancels yet")

    // Remove item 2: only item 2's subscription should be cleaned up
    items.set(List(1, 3))
    assertEquals(cancelCalls, 1, "only removed item's subscription cleaned up")

    // Remove item 3: only item 3's subscription cleaned up
    items.set(List(1))
    assertEquals(cancelCalls, 2, "only newly removed item's subscription cleaned up")

    Lifecycle.destroyTree(container) // remaining item 1 cleaned up
    assertEquals(cancelCalls, 3)
  }

  test("Bind.each(Signal) destroys removed keyed items' subscriptions") {
    var cancelCalls = 0
    val v         = Var(List("x", "y", "z"))
    val source    = v.signal
    val container = dom.document.createElement("div")
    val anchor    = dom.document.createComment("")
    container.appendChild(anchor)

    val renderFn: String => dom.Node = _ => makeTrackedEl { cancelCalls += 1 }

    val (_, compOwner) = Owner.withNew {
      Bind.each(source, identity[String], renderFn, anchor)
    }
    Lifecycle.register(container, compOwner)

    assertEquals(cancelCalls, 0)

    v.set(List("x", "z")) // remove "y"
    assertEquals(cancelCalls, 1, "only removed item cleaned up")

    Lifecycle.destroyTree(container)
    assertEquals(cancelCalls, 3)
  }

  test("Bind.each reused items are not destroyed when list is reordered") {
    var cancelCalls = 0
    val items       = Var(List(1, 2, 3))
    val container   = dom.document.createElement("div")
    val anchor      = dom.document.createComment("")
    container.appendChild(anchor)

    val renderFn: Int => dom.Node = _ => makeTrackedEl { cancelCalls += 1 }

    val (_, compOwner) = Owner.withNew {
      Bind.each(items, identity[Int], renderFn, anchor)
    }
    Lifecycle.register(container, compOwner)

    assertEquals(cancelCalls, 0)

    // Reorder only: all keys survive, no destruction
    items.set(List(3, 1, 2))
    assertEquals(cancelCalls, 0, "reordered items must not be destroyed")

    Lifecycle.destroyTree(container)
    assertEquals(cancelCalls, 3)
  }
