/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import org.scalajs.dom

/** Correctness of the LIS-based keyed reconciliation ([[Bind.each]]) — the critical
  * property is that the DOM order always matches the new key order, across arbitrary
  * add/remove/reorder operations, while reusing nodes by key. */
class BindEachReconcileSpec extends munit.FunSuite:

  // ── LIS algorithm (direct) ────────────────────────────────────────────────

  test("lisIndices returns the indices of a longest strictly-increasing subsequence") {
    // [2,0,1] → LIS is [0,1] at positions 1,2
    assertEquals(Bind.lisIndices(Array(2, 0, 1), -1), Set(1, 2))
    // already sorted → all positions
    assertEquals(Bind.lisIndices(Array(0, 1, 2, 3), -1), Set(0, 1, 2, 3))
    // reversed → any single element (length-1 LIS); reconstruction yields the last-seen
    assertEquals(Bind.lisIndices(Array(3, 2, 1, 0), -1).size, 1)
    // newMarker (-1) entries are excluded from the LIS
    assertEquals(Bind.lisIndices(Array(-1, 0, -1, 1), -1), Set(1, 3))
    assertEquals(Bind.lisIndices(Array.empty[Int], -1), Set.empty[Int])
  }

  // ── DOM-order correctness ─────────────────────────────────────────────────

  private def setup(initial: List[Int]): (State[List[Int]], dom.Element, dom.Comment) =
    val items     = State(initial)
    val container = dom.document.createElement("div")
    val anchor    = dom.document.createComment("")
    container.appendChild(anchor)
    val renderFn: Int => dom.Node = i =>
      val el = dom.document.createElement("i")
      el.textContent = i.toString
      el
    Bind.each(items, (i: Int) => i, renderFn, anchor)
    (items, container, anchor)

  /** The element children's text, in DOM order. */
  private def order(container: dom.Element): List[String] =
    (0 until container.childNodes.length).toList
      .map(container.childNodes(_))
      .collect { case el: dom.Element => el.textContent }

  private def check(initial: List[Int], next: List[Int]): Unit =
    val (items, container, _) = setup(initial)
    assertEquals(order(container), initial.map(_.toString), s"initial render of $initial")
    items.set(next)
    assertEquals(order(container), next.map(_.toString), s"$initial -> $next")

  test("reverse") { check(List(1, 2, 3), List(3, 2, 1)) }
  test("shuffle") { check(List(1, 2, 3, 4, 5), List(3, 1, 4, 5, 2)) }
  test("move one to end") { check(List(1, 2, 3, 4), List(1, 3, 4, 2)) }
  test("prepend") { check(List(2, 3), List(1, 2, 3)) }
  test("remove middle") { check(List(1, 2, 3), List(1, 3)) }
  test("add + remove + reorder") { check(List(1, 2, 3), List(3, 4, 1)) }
  test("clear then refill") { check(List(1, 2, 3), Nil); check(Nil, List(1, 2)) }

  test("nodes are reused by key across a reorder (same identity)") {
    val (items, container, _) = setup(List(1, 2, 3))
    def nodeOf(key: Int): dom.Node =
      (0 until container.childNodes.length)
        .map(container.childNodes(_))
        .collect {
          case el: dom.Element if el.textContent == key.toString => el
        }
        .head
    val n1 = nodeOf(1)
    val n2 = nodeOf(2)
    items.set(List(3, 2, 1)) // reorder
    assert(nodeOf(1) eq n1, "node for key 1 reused")
    assert(nodeOf(2) eq n2, "node for key 2 reused")
    assertEquals(order(container), List("3", "2", "1"))
  }
