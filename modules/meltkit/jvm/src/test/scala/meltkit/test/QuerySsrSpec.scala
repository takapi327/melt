/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import melt.runtime.Async

import meltkit.*

/** On the JVM a query renders only its initial snapshot — `Loading` — and issues
  * no request; `refresh`/`set` are inert because SSR `State` is frozen. */
class QuerySsrSpec extends munit.FunSuite:

  test("apply on the JVM yields a Query frozen at Async.Loading"):
    val list = ServerFn.query[Unit, List[Int]]("posts.list")
    val q    = list()
    assertEquals(q.state.value, Async.Loading)

  test("refresh and set are no-ops during SSR (state stays Loading)"):
    val list = ServerFn.query[Unit, List[Int]]("posts.list")
    val q    = list()
    q.refresh()
    q.set(List(1, 2, 3))
    assertEquals(q.state.value, Async.Loading)

  test("a seeded query renders the resolved value during SSR (no loading flash)"):
    val list = ServerFn.query[Unit, List[Int]]("posts.list")
    val q    = list.seeded(List(1, 2, 3))
    assertEquals(q.state.value, Async.Done(List(1, 2, 3)))

  test("seeded with an explicit input also resolves during SSR"):
    val byId = ServerFn.query[Int, String]("posts.byId")
    val q    = byId.seeded(7, "hello")
    assertEquals(q.state.value, Async.Done("hello"))
