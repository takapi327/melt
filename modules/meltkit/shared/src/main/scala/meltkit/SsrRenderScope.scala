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

  private var _counter = 0
  private val _pending: ListBuffer[SsrRenderScope.Suspended[?]] = ListBuffer.empty

  /** Allocates a request-unique boundary marker id (never a compile-time literal,
    * which would collide across the many `ServerRenderer`s a request builds). */
  def nextId(): String =
    _counter += 1
    s"melt-sb-$_counter"

  /** Registers a boundary: `renderBranch` renders the resolved (or failed) branch
    * to a fragment once the query settles. */
  def suspend[Out](id: String, query: Query[Out], renderBranch: Async[Out] => RenderResult): Unit =
    _pending += SsrRenderScope.Suspended(id, query, renderBranch)

  private[meltkit] def nonEmpty: Boolean = _pending.nonEmpty

  /** Resolves every boundary concurrently, isolating failures per boundary (a
    * failed query renders its `Failed` branch, never the whole page). Returns the
    * rendered fragments (`markerId -> fragment`) for marker splicing plus the
    * hydration seed JSON that lets the client adopt the data without refetching. */
  private[meltkit] def resolveAll(using
    functor:  Functor[F],
    recover:  Recover[F],
    parallel: Parallel[F]
  ): F[SsrRenderScope.Resolved] =
    functor.map(parallel.parTraverse(_pending.toList)(s => resolveOne(s))) { outcomes =>
      val fragments = outcomes.map(o => o.id -> o.fragment).toMap
      SsrRenderScope.Resolved(fragments, SsrRenderScope.buildSeedJson(outcomes.flatMap(_.seed)))
    }

  private def resolveOne[Out](s: SsrRenderScope.Suspended[Out])(using
    functor: Functor[F],
    recover: Recover[F]
  ): F[SsrRenderScope.Outcome] =
    functor.map(recover.attempt(resolveQuery(s.query.name, s.query.argsJson))) {
      // Each branch renders inside `wrapBranch`, which re-establishes the request
      // ambient (e.g. `Router.withPath`) that a deferred F phase would otherwise
      // have lost — the generated branch HTML may read `Router.currentPath`.
      case Right(Some(json)) =>
        val fragment = wrapBranch {
          try s.renderBranch(Async.Done(s.query.outCodec.decode(SimpleJson.parse(json))))
          catch case e: Throwable => s.renderBranch(Async.Failed(e))
        }
        // Only a successfully resolved query is seeded — the client adopts its raw
        // JSON verbatim (decoded with the same codec) and skips its initial fetch.
        SsrRenderScope.Outcome(s.id, fragment, Some(s.query.key -> json))
      case Right(None) => SsrRenderScope.Outcome(s.id, wrapBranch(s.renderBranch(Async.Loading)), None)
      case Left(e)     => SsrRenderScope.Outcome(s.id, wrapBranch(s.renderBranch(Async.Failed(e))), None)
    }

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

  /** The result of [[SsrRenderScope.resolveAll]]: fragments keyed by marker id for
    * splicing, and the `data-melt-queries` JSON object (empty when nothing seeded). */
  private[meltkit] final case class Resolved(
    fragments: Map[String, RenderResult],
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
