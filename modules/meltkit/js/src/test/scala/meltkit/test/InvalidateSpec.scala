/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import melt.runtime.{ Async, Owner, OwnerNode }
import melt.runtime.json.PropsCodec

import meltkit.*

/** Tag-based query invalidation ([[meltkit.Invalidate]] + [[meltkit.QueryRegistry]]).
  *
  * Queries are built with a counting `runFetch` so `refresh()` is observable without
  * a server; registration lifecycle is driven through a real [[OwnerNode]].
  */
class InvalidateSpec extends munit.FunSuite:

  /** A query whose `refresh()` just bumps a counter (private[meltkit] ctor). */
  private def spyQuery(name: String, tags: Set[String]): (Query[Int], () => Int) =
    var count = 0
    val q     = new Query[Int](name, "null", summon[PropsCodec[Int]], Async.Loading, _ => count += 1, tags)
    (q, () => count)

  test("Invalidate(tag) refreshes only live queries carrying that tag") {
    val node        = new OwnerNode(None)
    val (a, aCount) = spyQuery("a", Set("posts"))
    val (b, bCount) = spyQuery("b", Set("users"))
    Owner.withOwner(node) {
      QueryRegistry.register(a)
      QueryRegistry.register(b)
      Invalidate("posts")
    }
    assertEquals(aCount(), 1) // tagged "posts" → refreshed
    assertEquals(bCount(), 0) // tagged "users" → untouched
    node.destroy()
  }

  test("Invalidate.all() refreshes every live query") {
    val node        = new OwnerNode(None)
    val (a, aCount) = spyQuery("a", Set("posts"))
    val (b, bCount) = spyQuery("b", Set.empty)
    Owner.withOwner(node) {
      QueryRegistry.register(a)
      QueryRegistry.register(b)
      Invalidate.all()
    }
    assertEquals(aCount(), 1)
    assertEquals(bCount(), 1)
    node.destroy()
  }

  test("a query is deregistered when its owner is destroyed (no stale refresh)") {
    val node        = new OwnerNode(None)
    val (a, aCount) = spyQuery("a", Set("posts"))
    Owner.withOwner(node)(QueryRegistry.register(a))
    node.destroy()      // simulate component unmount
    Invalidate("posts") // must not touch the deregistered query
    assertEquals(aCount(), 0)
  }

  test("a query built with no active owner is not tracked") {
    val (a, aCount) = spyQuery("a", Set("posts"))
    QueryRegistry.register(a) // no Owner.current → skipped
    Invalidate("posts")
    assertEquals(aCount(), 0)
  }

  test("QueryFn.tagged accumulates tags onto the contract") {
    val q = ServerFn.query[Unit, Int]("q").tagged("posts").tagged("feed")
    assertEquals(q.tags, Set("posts", "feed"))
  }
