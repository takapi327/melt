/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package components

import org.scalajs.dom

import melt.runtime.*

/** Tests for the onMount lifecycle hook and related Lifecycle/OnMount infrastructure.
  *
  * These tests verify that onMount callbacks are:
  *   - NOT called before Mount.apply (i.e. during create())
  *   - Called synchronously after Mount.apply
  *   - Called in child-before-parent order for nested components
  *   - Able to register cleanup functions that run on Lifecycle.destroyTree
  */
class OnMountSpec extends munit.FunSuite:

  /** Creates a detached container that is NOT appended to document.body,
    * so individual tests are fully isolated.
    */
  private def makeContainer(): dom.Element =
    dom.document.createElement("div")

  // ── Basic callback invocation ─────────────────────────────────────────────

  test("onMount callback is NOT called before Mount.apply") {
    var ran = false
    val el        = dom.document.createElement("div")
    val container = makeContainer()

    onMount(() => ran = true)

    assert(!ran, "onMount should not fire during create()")

    // cleanup — flush the pending queue so it does not affect later tests
    Mount(container, el)
    Lifecycle.destroyTree(container)
  }

  test("onMount callback is called synchronously after Mount.apply") {
    var ran = false
    val el        = dom.document.createElement("div")
    val container = makeContainer()

    onMount(() => ran = true)
    Mount(container, el)

    assert(ran, "onMount should fire after Mount.apply")
    Lifecycle.destroyTree(container)
  }

  test("multiple onMount callbacks all run") {
    var count = 0
    val el        = dom.document.createElement("div")
    val container = makeContainer()

    onMount(() => count += 1)
    onMount(() => count += 1)
    onMount(() => count += 1)
    Mount(container, el)

    assertEquals(count, 3)
    Lifecycle.destroyTree(container)
  }

  // ── Child-before-parent execution order ───────────────────────────────────

  test("child onMount runs before parent onMount") {
    val order     = scala.collection.mutable.ListBuffer.empty[String]
    val parent    = dom.document.createElement("div")
    val child     = dom.document.createElement("span")
    val container = makeContainer()

    // Simulate parent create() calling child create() first (recursive)
    // child registers first → dequeued first → child-before-parent
    onMount(() => { order += "child"; () })
    onMount(() => { order += "parent"; () })

    parent.appendChild(child)
    Mount(container, parent)

    assertEquals(order.toList, List("child", "parent"))
    Lifecycle.destroyTree(container)
  }

  // ── Cleanup returned from onMount ─────────────────────────────────────────

  test("cleanup returned from onMount runs on Lifecycle.destroyTree") {
    var mounted   = false
    var destroyed = false
    val el        = dom.document.createElement("div")
    val container = makeContainer()

    onMount { () =>
      mounted = true
      () => destroyed = true
    }
    Mount(container, el)

    assert(mounted, "onMount callback should have run")
    assert(!destroyed, "cleanup should not run before destroyTree")

    Lifecycle.destroyTree(container)

    assert(destroyed, "cleanup returned from onMount should run on destroyTree")
  }

  test("void onMount does not register a cleanup") {
    var ran = false
    val el        = dom.document.createElement("div")
    val container = makeContainer()

    // void overload: onMount(() => Unit)
    onMount(() => ran = true)
    Mount(container, el)

    assert(ran)
    // destroyTree should succeed even with no registered cleanup
    Lifecycle.destroyTree(container)
  }

  // ── Lifecycle.destroy / destroyTree ───────────────────────────────────────

  test("Lifecycle.register + destroy runs cleanups exactly once") {
    var calls = 0
    val el        = dom.document.createElement("div")
    val container = makeContainer()

    Cleanup.pushScope()
    Cleanup.register(() => calls += 1)
    val cleanups = Cleanup.popScope()
    Lifecycle.register(el, cleanups)
    container.appendChild(el)

    Lifecycle.destroy(el)
    assertEquals(calls, 1)

    // Second destroy is a no-op (already removed from registry)
    Lifecycle.destroy(el)
    assertEquals(calls, 1)
  }

  test("Lifecycle.destroyTree destroys all registered descendants") {
    val results   = scala.collection.mutable.ListBuffer.empty[String]
    val root      = dom.document.createElement("div")
    val child     = dom.document.createElement("div")
    val container = makeContainer()

    root.appendChild(child)
    container.appendChild(root)

    Cleanup.pushScope()
    Cleanup.register(() => results += "root")
    val rootCleanups = Cleanup.popScope()
    Lifecycle.register(root, rootCleanups)

    Cleanup.pushScope()
    Cleanup.register(() => results += "child")
    val childCleanups = Cleanup.popScope()
    Lifecycle.register(child, childCleanups)

    Lifecycle.destroyTree(container)

    // Both root and child cleanups should have run (order may vary)
    assertEquals(results.toSet, Set("root", "child"))
  }
