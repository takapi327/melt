/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import scala.concurrent.{ Future, Promise }
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

import org.scalajs.dom

import melt.runtime.Async

import meltkit.*

/** Client-side query behaviour: creating a [[meltkit.Query]] fires a request that
  * settles its reactive state, verified by stubbing the global `fetch`. */
class QueryClientSpec extends munit.FunSuite:

  private def globalThis: js.Dynamic = js.Dynamic.global.globalThis

  private var original:   js.Any = null
  private var fetchCalls: Int    = 0

  /** Installs a `fetch` stub returning `status`/`body`, counting invocations. */
  private def installFetch(status: Int, body: String): Unit =
    original   = globalThis.selectDynamic("fetch").asInstanceOf[js.Any]
    fetchCalls = 0
    val stub: js.Function2[String, js.Dynamic, js.Promise[js.Any]] = (_, _) =>
      fetchCalls += 1
      val res = js.Dynamic.literal(
        status  = status,
        ok      = status >= 200 && status < 300,
        headers = new dom.Headers()
      )
      res.updateDynamic("text")(() => js.Promise.resolve[String](body))
      js.Promise.resolve[js.Any](res)
    globalThis.updateDynamic("fetch")(stub)

  private def restoreFetch(): Unit = globalThis.updateDynamic("fetch")(original)

  /** Resolves once the query's state leaves `Loading` (or fails after a timeout). */
  private def settled[A](q: meltkit.Query[A]): Future[Async[A]] =
    if !q.state.value.isLoading then Future.successful(q.state.value)
    else
      val p     = Promise[Async[A]]()
      val unsub = q.state.subscribe { a => if !a.isLoading then p.trySuccess(a) }
      p.future.map { a => unsub(); a }

  test("a query starts Loading, then resolves to Done with the decoded value"):
    installFetch(200, "[1,2,3]")
    val list = ServerFn.query[Unit, List[Int]]("posts.list")
    val q    = list()
    assert(q.state.value.isLoading, "query should begin in Loading")
    settled(q).map { a =>
      assertEquals(a, Async.Done(List(1, 2, 3)))
      restoreFetch()
    }

  test("a non-2xx response resolves the query to Failed"):
    installFetch(500, "boom")
    val list = ServerFn.query[Unit, List[Int]]("posts.list")
    settled(list()).map { a =>
      assert(a.isFailed, s"expected Failed but got $a")
      restoreFetch()
    }

  test("refresh re-issues the request and updates the state"):
    installFetch(200, "[9]")
    val list = ServerFn.query[Unit, List[Int]]("posts.list")
    val q    = list()
    settled(q).flatMap { _ =>
      installFetch(200, "[42]")
      q.refresh()
      settled(q).map { a =>
        assertEquals(a, Async.Done(List(42)))
        restoreFetch()
      }
    }

  test("a seeded query starts Done and does NOT fetch on creation"):
    installFetch(200, "[99]")
    val list = ServerFn.query[Unit, List[Int]]("posts.list")
    val q    = list.seeded(List(1, 2, 3))
    assertEquals(q.state.value, Async.Done(List(1, 2, 3)))
    assertEquals(fetchCalls, 0, "seeded query must not fetch on creation")
    restoreFetch()

  test("a seeded query still refreshes on demand, fetching live data"):
    installFetch(200, "[42]")
    val list = ServerFn.query[Unit, List[Int]]("posts.list")
    val q    = list.seeded(List(1, 2, 3))
    q.refresh()
    settled(q).map { a =>
      assertEquals(a, Async.Done(List(42)))
      assertEquals(fetchCalls, 1)
      restoreFetch()
    }

  test("identical concurrent queries share a single fetch (in-flight dedup)"):
    installFetch(200, "[7]")
    val list = ServerFn.query[Unit, List[Int]]("posts.list")
    val q1   = list()
    val q2   = list()
    Future.sequence(List(settled(q1), settled(q2))).map { results =>
      assertEquals(results, List(Async.Done(List(7)), Async.Done(List(7))))
      assertEquals(fetchCalls, 1, "identical concurrent queries must coalesce to one fetch")
      restoreFetch()
    }

  test("queries with different arguments do not coalesce"):
    installFetch(200, "\"x\"")
    val byId = ServerFn.query[Int, String]("posts.byId")
    val q1   = byId(1)
    val q2   = byId(2)
    Future.sequence(List(settled(q1), settled(q2))).map { _ =>
      assertEquals(fetchCalls, 2, "distinct arguments must each fetch")
      restoreFetch()
    }

  test("single-flight run() returns the result and applies the piggybacked update"):
    installFetch(200, """{"result":3,"updates":[{"name":"posts.list","args":"null","value":[1,2,3]}]}""")
    val list  = ServerFn.query[Unit, List[Int]]("posts.list")
    val like  = ServerFn.command[Int, Int]("posts.like")
    val posts = list.seeded(List(1, 2)) // seeded → starts Done, no auto-fetch
    like.dispatch(3).updates(posts).run().map { result =>
      assertEquals(result, 3)
      assertEquals(posts.state.value, Async.Done(List(1, 2, 3)))
      assertEquals(fetchCalls, 1, "single-flight is exactly one round-trip")
      restoreFetch()
    }

  test("optimistic update shows immediately, then reconciles with the server value"):
    installFetch(200, """{"result":3,"updates":[{"name":"posts.list","args":"null","value":[1,2,3]}]}""")
    val list  = ServerFn.query[Unit, List[Int]]("posts.list")
    val like  = ServerFn.command[Int, Int]("posts.like")
    val posts = list.seeded(List(1, 2))
    val fut   = like.dispatch(3).optimistic(posts)(_ :+ 99).run()
    // synchronously after run(): the optimistic value is already visible
    assertEquals(posts.state.value, Async.Done(List(1, 2, 99)))
    fut.map { result =>
      assertEquals(result, 3)
      // reconciled with the authoritative server value from the same round-trip
      assertEquals(posts.state.value, Async.Done(List(1, 2, 3)))
      restoreFetch()
    }

  test("optimistic update rolls back when the mutation fails"):
    installFetch(500, "boom")
    val list  = ServerFn.query[Unit, List[Int]]("posts.list")
    val like  = ServerFn.command[Int, Int]("posts.like")
    val posts = list.seeded(List(1, 2))
    val fut   = like.dispatch(3).optimistic(posts)(_ :+ 99).run()
    assertEquals(posts.state.value, Async.Done(List(1, 2, 99))) // optimistic shown
    fut.failed.map { _ =>
      assertEquals(posts.state.value, Async.Done(List(1, 2))) // rolled back to pre-mutation value
      restoreFetch()
    }
