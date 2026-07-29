/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import scala.concurrent.{ Future, Promise }
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.Dynamic.global as g

import org.scalajs.dom

import melt.runtime.Async

import meltkit.*

/** Client prefetch: `QueryFn.prefetch` warms a short-lived, single-use cache that a
  * later `query()` for the same key adopts, so a navigation renders `Done` with no
  * loading flash and no extra fetch. `query()` reads the SSR seed via
  * `document.querySelector`, so a jsdom document is provided (this module runs under
  * the plain Node env); `fetch` is stubbed to count round-trips.
  */
class PrefetchSpec extends munit.FunSuite:

  private def installDocument(): Unit =
    if js.isUndefined(g.globalThis.document) then
      val jsdom = g.require("jsdom")
      val dom   = js.Dynamic.newInstance(jsdom.JSDOM)("<!doctype html><body></body>")
      g.globalThis.updateDynamic("document")(dom.window.document)

  private var fetchCalls = 0

  private def installFetch(status: Int, body: String): Unit =
    fetchCalls = 0
    val stub: js.Function2[String, js.Dynamic, js.Promise[js.Any]] = (_, _) =>
      fetchCalls += 1
      val res = js.Dynamic.literal(status = status, ok = status >= 200 && status < 300, headers = new dom.Headers())
      res.updateDynamic("text")(() => js.Promise.resolve[String](body))
      js.Promise.resolve[js.Any](res)
    g.globalThis.updateDynamic("fetch")(stub)

  /** A macrotask delay, long enough for the stubbed fetch + text promises to settle. */
  private def settle(): Future[Unit] =
    val p = Promise[Unit]()
    js.timers.setTimeout(5)(p.success(()))
    p.future

  test("a prefetch warms the query so the next call starts Done and does not refetch"):
    installDocument()
    installFetch(200, "[1,2,3]")
    val nums = ServerFn.query[Unit, List[Int]]("prefetch.warm")

    nums.prefetch() // fires one stubbed request into the cache

    settle().map { _ =>
      assertEquals(fetchCalls, 1, "prefetch should fetch exactly once")
      val q = nums() // consumes the cache — Done, no new fetch
      assertEquals(fetchCalls, 1, "adopting the prefetch must not fetch again")
      q.state.value match
        case Async.Done(v) => assertEquals(v, List(1, 2, 3))
        case other         => fail(s"expected Done(List(1,2,3)), got $other")
    }

  test("the prefetch entry is single-use — a second query for the same key fetches"):
    installDocument()
    installFetch(200, "[9]")
    val nums = ServerFn.query[Unit, List[Int]]("prefetch.single")

    nums.prefetch()

    settle().map { _ =>
      val first = nums() // consumes the cache
      assertEquals(first.state.value, Async.Done(List(9)))
      assertEquals(fetchCalls, 1)
      val second = nums() // cache gone → starts Loading and fetches
      assertEquals(second.state.value, Async.Loading)
      assertEquals(fetchCalls, 2, "a second query re-fetches (no lingering cache)")
    }

  test("a query with no prefetch behaves as before (Loading, then fetches)"):
    installDocument()
    installFetch(200, "[1]")
    val nums = ServerFn.query[Unit, List[Int]]("prefetch.none")

    val q = nums()
    assertEquals(q.state.value, Async.Loading)
    assertEquals(fetchCalls, 1)
