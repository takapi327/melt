/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.concurrent.{ ExecutionContext, Future }
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.Thenable.Implicits.given
import scala.util.{ Failure, Success }

import org.scalajs.dom

import melt.runtime.json.SimpleJson
import melt.runtime.render.ServerRenderer
import melt.runtime.Async

/** Client-side invocation of server functions.
  *
  * Both the command and query call sites live here, in one file, so package
  * `meltkit` has a single top-level `apply` symbol (Scala forbids the same
  * top-level name across files on one classpath). They use `dom.fetch` directly
  * rather than the browser adapter's `Fetch`, so a query call — which appears in
  * component script/template and therefore also compiles for SSR — has its
  * client behaviour defined in `meltkit/js`, while the JVM counterpart lives in
  * `meltkit/jvm` (`QueryClient.scala`).
  *
  * A command is only ever invoked inside an event handler (stripped from SSR
  * output), so it needs no JVM counterpart.
  */

/** Invoke a command.
  *
  *   - `apply` POSTs the encoded argument and decodes the result (fire-and-forget
  *     from an event handler). A non-2xx response fails the `Future` with a
  *     [[ServerFnException]].
  *   - `dispatch` starts a single-flight [[Mutation]] builder: declare queries to
  *     refresh with `.updates(…)`, then `.run()` mutates and refreshes them all in
  *     one round-trip.
  */
extension [In, Out](fn: CommandFn[In, Out])
  def apply(in: In)(using ec: ExecutionContext): Future[Out] =
    val url  = fn.endpoint.url(PathSpec.emptyValue)
    val body = fn.inCodec.encodeToString(in)
    ServerFnClient.postJson(url, body).flatMap { res =>
      res.text().toFuture.map { text =>
        if res.ok then fn.outCodec.decode(SimpleJson.parse(text))
        else throw ServerFnException(res.status, text)
      }
    }

  def dispatch(in:         In):          Mutation[In, Out] = new Mutation(fn, in)
  def dispatch()(using ev: Unit =:= In): Mutation[In, Out] = new Mutation(fn, ev(()))

/** A single-flight mutation: declares the queries to refresh from the mutation's
  * response, so `run()` both mutates and updates the client's reactive queries in
  * one round-trip.
  *
  * {{{
  * // in an event handler
  * Posts.like.dispatch(post.id).updates(posts).run()
  * }}}
  */
final class Mutation[In, Out] private[meltkit] (fn: CommandFn[In, Out], in: In):
  private var refresh:   List[Query[?]]           = Nil
  private var optimisms: List[() => (() => Unit)] = Nil

  /** Declares queries to refresh from the mutation's response (single-flight). */
  def updates(queries: Query[?]*): Mutation[In, Out] =
    refresh = refresh ++ queries.toList
    this

  /** Optimistically updates `query` with `f` the moment `run()` fires — before
    * the server responds — then reconciles with the authoritative value from the
    * same round-trip on success, or rolls back on failure. The query is refreshed
    * automatically, so no separate `updates` call is needed for it.
    *
    * {{{
    * Posts.like.dispatch(post.id)
    *   .optimistic(posts)(_.map(p => if p.id == post.id then p.copy(likes = p.likes + 1) else p))
    *   .run()
    * }}}
    */
  def optimistic[A](query: Query[A])(f: A => A): Mutation[In, Out] =
    refresh   = refresh :+ query
    optimisms = optimisms :+ (() => query.applyOptimistic(f))
    this

  /** Fires the mutation; on success applies the piggybacked query updates and
    * returns the decoded result, rolling back any optimistic changes on failure. */
  def run()(using ExecutionContext): Future[Out] = ServerFnClient.runMutation(fn, in, refresh, optimisms)

/** Invoke a query.
  *
  *   - `apply` returns a reactive [[Query]] that issues the request immediately
  *     and settles its state as the response arrives.
  *   - `seeded` returns a [[Query]] already resolved with an initial value
  *     (typically a page-loader prop rendered during SSR), so hydration shows the
  *     data with no loading flash and no redundant initial fetch; `refresh()`
  *     still re-runs the request on demand.
  */
extension [In, Out](fn: QueryFn[In, Out])
  def apply(in: In): Query[Out] =
    val q = ServerFnClient.build(fn, in, Async.Loading)
    q.refresh()
    q
  def apply()(using ev: Unit =:= In): Query[Out] = apply(ev(()))

  def seeded(in:   In, seed:      Out):         Query[Out] = ServerFnClient.build(fn, in, Async.Done(seed))
  def seeded(seed: Out)(using ev: Unit =:= In): Query[Out] = seeded(ev(()), seed)

private object ServerFnClient:

  /** Builds a [[Query]] whose `refresh` re-issues the request, starting from
    * `initial` (`Loading` for an eager query, `Done(seed)` for a seeded one). */
  def build[In, Out](fn: QueryFn[In, Out], in: In, initial: Async[Out]): Query[Out] =
    val url  = fn.endpoint.url(PathSpec.emptyValue)
    val body = fn.inCodec.encodeToString(in)
    new Query[Out](fn.name, body, fn.outCodec, initial, self => runQuery(fn, url, body, self))

  /** POSTs a JSON body to `url`, resolving once response headers arrive. When
    * `singleFlight` is set, adds the header that asks the server to return an
    * `{ result, updates }` envelope. */
  def postJson(url: String, body: String, singleFlight: Boolean = false)(using ExecutionContext): Future[dom.Response] =
    val headers = new dom.Headers()
    headers.set("Content-Type", "application/json")
    if singleFlight then headers.set("X-Melt-Sf", "1")
    val init = new dom.RequestInit {}
    init.method  = dom.HttpMethod.POST
    init.headers = headers
    init.body    = body
    dom.fetch(url, init).toFuture

  /** Runs a single-flight mutation: applies any optimistic updates immediately,
    * POSTs `{ input, refresh }`, then on success applies each piggybacked query
    * update and returns the decoded result. Any failure (non-2xx or network)
    * rolls back the optimistic updates. */
  def runMutation[In, Out](
    fn:        CommandFn[In, Out],
    in:        In,
    refresh:   List[Query[?]],
    optimisms: List[() => (() => Unit)]
  )(using ExecutionContext): Future[Out] =
    val rollbacks   = optimisms.map(_()) // apply optimistic now, keep undo thunks
    val url         = fn.endpoint.url(PathSpec.emptyValue)
    val inputJson   = fn.inCodec.encodeToString(in)
    val refreshJson = refresh
      .map(q => s"""{"name":${ SimpleJson.encString(q.name) },"args":${ SimpleJson.encString(q.argsJson) }}""")
      .mkString("[", ",", "]")
    val body   = s"""{"input":$inputJson,"refresh":$refreshJson}"""
    val result = postJson(url, body, singleFlight = true).flatMap { res =>
      res.text().toFuture.map { text =>
        if res.ok then
          val obj = SimpleJson.parse(text).asInstanceOf[SimpleJson.JsonValue.Obj]
          applyUpdates(obj, refresh)
          fn.outCodec.decode(obj.fields("result"))
        else throw ServerFnException(res.status, text)
      }
    }
    // Roll back as part of settling this future, so any downstream callback
    // observes the restored state on failure (non-2xx or network error). Undo in
    // reverse so stacked optimistic updates on the same query restore correctly.
    result.transform { outcome =>
      if outcome.isFailure then rollbacks.reverse.foreach(_())
      outcome
    }

  /** Routes each response update entry to the matching declared query by
    * `name + args`, resolving its reactive state with the piggybacked value. */
  private def applyUpdates(obj: SimpleJson.JsonValue.Obj, refresh: List[Query[?]]): Unit =
    def str(o: SimpleJson.JsonValue.Obj, k: String): Option[String] = o.fields.get(k) match
      case Some(SimpleJson.JsonValue.Str(s)) => Some(s)
      case _                                 => None
    obj.fields.get("updates") match
      case Some(SimpleJson.JsonValue.Arr(items)) =>
        items.foreach {
          case u: SimpleJson.JsonValue.Obj =>
            for
              name  <- str(u, "name")
              args  <- str(u, "args")
              value <- u.fields.get("value")
            do refresh.find(_.key == s"$name\n$args").foreach(_.applyUpdate(value))
          case _ => ()
        }
      case _ => ()

  /** In-flight requests keyed by endpoint + argument JSON, so that identical
    * concurrent queries (e.g. several components mounting the same `list()`)
    * share a single HTTP round-trip. Entries are removed once settled — results
    * are intentionally NOT cached, so no stale data is ever served. Safe as plain
    * mutable state because Scala.js is single-threaded. */
  private val inFlight = scala.collection.mutable.Map.empty[String, Future[(Int, Boolean, String)]]

  /** Runs a query request and drives the [[Query]]'s reactive state, coalescing
    * identical concurrent requests. */
  def runQuery[In, Out](fn: QueryFn[In, Out], url: String, body: String, q: Query[Out]): Unit =
    // Never loopback-fetch during a server-side render pass (Node SSR runs this
    // JS branch): leave the query at its initial state and let it resolve after
    // hydration on the client. Seed the query for SSR data instead.
    if ServerRenderer.isRendering then ()
    else
      q.setLoading()
      runQueryNow(fn, url, body, q)

  private def runQueryNow[In, Out](fn: QueryFn[In, Out], url: String, body: String, q: Query[Out]): Unit =
    given ExecutionContext = queue
    val key                = s"$url\n$body"
    val raw                = inFlight.getOrElseUpdate(
      key, {
        val f = postJson(url, body).flatMap(res => res.text().toFuture.map(text => (res.status, res.ok, text)))
        f.onComplete(_ => inFlight.remove(key))
        f
      }
    )
    raw.onComplete {
      case Success((status, ok, text)) =>
        if ok then
          try q.setDone(fn.outCodec.decode(SimpleJson.parse(text)))
          catch case e: Throwable => q.setFailed(e)
        else q.setFailed(ServerFnException(status, text))
      case Failure(e) => q.setFailed(e)
    }
