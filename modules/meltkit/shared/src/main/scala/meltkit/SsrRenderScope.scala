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
  private val resolveQuery: (String, String) => F[Option[String]]
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
    * failed query renders its `Failed` branch, never the whole page). Returns
    * `markerId -> fragment`. */
  private[meltkit] def resolveAll(using
    functor:  Functor[F],
    recover:  Recover[F],
    parallel: Parallel[F]
  ): F[Map[String, RenderResult]] =
    functor.map(parallel.parTraverse(_pending.toList)(s => resolveOne(s)))(_.toMap)

  private def resolveOne[Out](s: SsrRenderScope.Suspended[Out])(using
    functor: Functor[F],
    recover: Recover[F]
  ): F[(String, RenderResult)] =
    functor.map(recover.attempt(resolveQuery(s.query.name, s.query.argsJson))) {
      case Right(Some(json)) =>
        val fragment =
          try s.renderBranch(Async.Done(s.query.outCodec.decode(SimpleJson.parse(json))))
          catch case e: Throwable => s.renderBranch(Async.Failed(e))
        s.id -> fragment
      case Right(None) => s.id -> s.renderBranch(Async.Loading) // not registered → keep fallback
      case Left(e)     => s.id -> s.renderBranch(Async.Failed(e))
    }

object SsrRenderScope:

  private[meltkit] final case class Suspended[Out](
    id:           String,
    query:        Query[Out],
    renderBranch: Async[Out] => RenderResult
  )

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
    * shell result and the populated scope for the adapter to resolve. */
  private[meltkit] def withScope[F[_], A](
    resolveQuery: (String, String) => F[Option[String]]
  )(body: => A): (A, SsrRenderScope[F]) =
    val scope = new SsrRenderScope[F](resolveQuery)
    val prev  = _current.get
    _current.set(scope)
    try (body, scope)
    finally _current.set(prev)
