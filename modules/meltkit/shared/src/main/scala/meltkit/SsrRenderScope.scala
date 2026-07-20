/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.collection.mutable.ListBuffer

import melt.runtime.json.SimpleJson
import melt.runtime.render.RenderResult
import melt.runtime.Async

/** Per-request ambient that collects the async suspense boundaries a
  * `<melt:await>` registers during synchronous shell rendering, then resolves
  * them (in `F`) for blocking async SSR.
  *
  * The generated (F-free) component code registers boundaries through the
  * existential [[SsrRenderScope.current]]; the adapter — which knows the concrete
  * `F` and holds the app's server-function registry — establishes the scope with
  * [[SsrRenderScope.withScope]] and drives [[resolveAll]].
  *
  * `resolveQuery(name, args)` re-runs a registered query in-process (no HTTP
  * loopback), returning its encoded JSON — this is the app's server-function
  * registry closed over the request context.
  *
  * The ambient is a plain `ThreadLocal` (a single-value holder on Scala.js): it
  * only needs to be visible during the synchronous shell render, since each
  * boundary's `renderBranch` captures what it needs and [[resolveAll]] runs later.
  */
final class SsrRenderScope[F[_]] private[meltkit] (
  private val resolveQuery: (String, String) => F[Option[String]],
  private val wrapBranch:   SsrRenderScope.BranchWrap = SsrRenderScope.BranchWrap.identity
):

  // Guards `_counter` and `_pending`: nested boundaries register during branch
  // rendering, which `resolveAll` runs concurrently (real threads on the JVM).
  private val _lock    = new AnyRef
  private var _counter = 0
  private val _pending: ListBuffer[SsrRenderScope.Suspended[?]] = ListBuffer.empty

  /** Allocates a request-unique boundary marker id (never a compile-time literal,
    * which would collide across the many `ServerRenderer`s a request builds). */
  def nextId(): String = _lock.synchronized {
    _counter += 1
    s"melt-sb-$_counter"
  }

  /** Registers a boundary: `renderBranch` renders the resolved (or failed) branch
    * to a fragment once the query settles. */
  def suspend[Out](id: String, query: Query[Out], renderBranch: Async[Out] => RenderResult): Unit =
    _lock.synchronized(_pending += SsrRenderScope.Suspended(id, query, renderBranch))

  private[meltkit] def nonEmpty: Boolean = _lock.synchronized(_pending.nonEmpty)

  private def pendingSize: Int = _lock.synchronized(_pending.length)

  private def pendingFrom(from: Int): List[SsrRenderScope.Suspended[?]] =
    _lock.synchronized(_pending.slice(from, _pending.length).toList)

  /** Resolves every boundary concurrently, isolating failures per boundary (a failed
    * query renders its `Failed` branch, never the whole page), then repeats for any
    * nested `<melt:await>` boundaries a branch registered while rendering — one level
    * per round, until none remain. Fragments accumulate parent-first, so splicing a
    * parent (whose fragment carries a child's marker) precedes splicing the child.
    * Returns the ordered fragments plus the hydration seed JSON. */
  private[meltkit] def resolveAll(using
    functor:  Functor[F],
    flatMap:  FlatMap[F],
    pure:     Pure[F],
    recover:  Recover[F],
    parallel: Parallel[F]
  ): F[SsrRenderScope.Resolved] =
    def loop(
      from:  Int,
      frags: List[(String, RenderResult)],
      seeds: List[(String, String)]
    ): F[SsrRenderScope.Resolved] =
      val round = pendingFrom(from)
      if round.isEmpty then pure.pure(SsrRenderScope.Resolved(frags, SsrRenderScope.buildSeedJson(seeds)))
      else
        val next = pendingSize
        flatMap.flatMap(parallel.parTraverse(round)(s => resolveOne(s))) { outcomes =>
          loop(next, frags ++ outcomes.map(o => o.id -> o.fragment), seeds ++ outcomes.flatMap(_.seed))
        }
    loop(0, Nil, Nil)

  private def resolveOne[Out](s: SsrRenderScope.Suspended[Out])(using
    functor: Functor[F],
    recover: Recover[F]
  ): F[SsrRenderScope.Outcome] =
    functor.map(recover.attempt(resolveQuery(s.query.name, s.query.argsJson))) {
      case Right(Some(json)) =>
        val fragment = renderBranchIn {
          try s.renderBranch(Async.Done(s.query.outCodec.decode(SimpleJson.parse(json))))
          catch case e: Throwable => s.renderBranch(Async.Failed(e))
        }
        // Only a successfully resolved query is seeded — the client adopts its raw
        // JSON verbatim (decoded with the same codec) and skips its initial fetch.
        SsrRenderScope.Outcome(s.id, fragment, Some(s.query.key -> json))
      case Right(None) => SsrRenderScope.Outcome(s.id, renderBranchIn(s.renderBranch(Async.Loading)), None)
      case Left(e)     => SsrRenderScope.Outcome(s.id, renderBranchIn(s.renderBranch(Async.Failed(e))), None)
    }

  /** Renders a branch with the request ambient (`wrapBranch`, e.g. `Router.withPath`)
    * AND this scope active, so any nested `<melt:await>` the branch renders registers
    * back into this scope's pending list for the next resolution round. */
  private def renderBranchIn(thunk: => RenderResult): RenderResult =
    SsrRenderScope.withCurrent(this)(wrapBranch(thunk))

object SsrRenderScope:

  /** Wraps a branch render so the adapter can re-establish request-scoped ambients
    * (e.g. `Router.withPath`) around it — a by-name SAM, since `(=> A) => A` is not
    * a valid function type. */
  private[meltkit] trait BranchWrap:
    def apply(thunk: => RenderResult): RenderResult

  private[meltkit] object BranchWrap:
    val identity: BranchWrap = new BranchWrap:
      def apply(thunk: => RenderResult): RenderResult = thunk

  private[meltkit] final case class Suspended[Out](
    id:           String,
    query:        Query[Out],
    renderBranch: Async[Out] => RenderResult
  )

  /** One resolved boundary: its marker id, the rendered fragment to splice, and —
    * for a successfully resolved query — the `key -> rawResultJson` hydration seed. */
  private[meltkit] final case class Outcome(
    id:       String,
    fragment: RenderResult,
    seed:     Option[(String, String)]
  )

  /** The result of [[SsrRenderScope.resolveAll]]: fragments in parent-first splice
    * order, and the `data-melt-queries` JSON object (empty when nothing seeded). */
  private[meltkit] final case class Resolved(
    fragments: List[(String, RenderResult)],
    seedJson:  String
  )

  /** Splices each resolved `<melt:await>` branch over its marker span and appends the
    * hydration seed as a `<script data-melt-queries>` so the client adopts the data
    * without refetching. Shared by every server adapter's `renderAsync`. */
  private[meltkit] def spliceAndSeed(body: String, resolved: Resolved): String =
    var out = body
    resolved.fragments.foreach { case (id, frag) => out = spliceMarker(out, id, frag.body) }
    if resolved.seedJson.nonEmpty then
      // Escape `</` so a string value can never close the <script> element early.
      val safe = resolved.seedJson.replace("</", "<\\/")
      out = s"""$out<script type="application/json" data-melt-queries>$safe</script>"""
    out

  /** Replaces the `<!--melt:sb:ID-->` … `<!--/melt:sb:ID-->` span (marker + pending
    * fallback) with `replacement`. Leaves the body untouched if the markers are absent
    * (e.g. the boundary was inside a stripped event handler). */
  private def spliceMarker(html: String, id: String, replacement: String): String =
    val open  = s"<!--melt:sb:$id-->"
    val close = s"<!--/melt:sb:$id-->"
    val start = html.indexOf(open)
    if start < 0 then html
    else
      val end = html.indexOf(close, start)
      if end < 0 then html
      else html.substring(0, start) + replacement + html.substring(end + close.length)

  /** Builds the `data-melt-queries` JSON object body (`{ "name\nargs": <result>, … }`).
    * Keys are JSON-escaped; the raw result JSON is spliced verbatim as the value.
    * Duplicate keys collapse (several boundaries may share one query). Empty → "". */
  private[meltkit] def buildSeedJson(seeds: List[(String, String)]): String =
    val deduped = seeds.toMap
    if deduped.isEmpty then ""
    else deduped.map { case (k, json) => s"${ SimpleJson.encString(k) }:$json" }.mkString("{", ",", "}")

  // Ambient current scope. A plain holder is enough because it is only read
  // during the synchronous shell render (single-threaded on Scala.js; one thread
  // per request on the JVM via ThreadLocal).
  private val _current: ThreadLocal[SsrRenderScope[?] | Null] =
    new ThreadLocal[SsrRenderScope[?] | Null]

  /** The active scope during synchronous SSR shell rendering, if any. Generated
    * `<melt:await>` code registers through this (existential `F`, so the code
    * stays F-free). */
  def current: Option[SsrRenderScope[?]] = Option(_current.get)

  /** Makes `scope` the active ambient for the duration of `body` (restoring the
    * previous one after), so a deferred branch render re-registers nested boundaries
    * into the right scope. */
  private[meltkit] def withCurrent[A](scope: SsrRenderScope[?])(body: => A): A =
    val prev = _current.get
    _current.set(scope)
    try body
    finally _current.set(prev)

  /** Runs `body` (the shell render) with a fresh scope active, returning the
    * shell result and the populated scope for the adapter to resolve. `wrapBranch`
    * lets the adapter re-establish request-scoped ambients around each deferred
    * branch render (see [[BranchWrap]]). */
  private[meltkit] def withScope[F[_], A](
    resolveQuery: (String, String) => F[Option[String]],
    wrapBranch:   BranchWrap = BranchWrap.identity
  )(body: => A): (A, SsrRenderScope[F]) =
    val scope = new SsrRenderScope[F](resolveQuery, wrapBranch)
    val prev  = _current.get
    _current.set(scope)
    try (body, scope)
    finally _current.set(prev)
