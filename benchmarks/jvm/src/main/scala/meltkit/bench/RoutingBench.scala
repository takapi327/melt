/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.bench

import java.util.concurrent.TimeUnit

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole

import meltkit.*

/** Per-request routing cost.
  *
  * Every adapter resolves a request the same way (see `Http4sAdapter.scala:120`):
  *
  * {{{
  * app.routes.find { r => r.method == m && PathSegment.matches(r.segments, segments) }
  * }}}
  *
  * `routes` is not a stored list. Mounted sub-routers are resolved lazily — it walks
  * `_mounts` and relocates each child route on every call (`MeltKitPlatform.scala:87`).
  * That design is what makes routes added *after* mounting take effect, but it moves
  * work onto the request path, and nothing measured what that costs.
  *
  * Lives in package `meltkit` because `routes` and `PathSegment.matches` are
  * `private[meltkit]`.
  *
  * {{{ sbt "benchmarks/Jmh/run -i 10 -wi 5 -f 2 .*RoutingBench.*" }}}
  */
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)
class RoutingBench:

  /** Routes registered on the leaf router. */
  @Param(Array("10", "100", "1000"))
  var routeCount: Int = 0

  /** How many routers are chained with `route(prefix, sub)`. `0` means a flat router,
    * so the difference against the other values isolates the cost of lazy mount folding.
    */
  @Param(Array("0", "1", "3", "5"))
  var mountDepth: Int = 0

  private var app: MeltKit[Future] = null

  /** Path of the LAST registered route — the worst case for a linear `find`. */
  private var worstCase: List[String] = Nil

  /** Path that matches nothing, so `find` always scans every route. */
  private val noMatch: List[String] = List("does", "not", "exist")

  /** `HttpMethod` is an opaque string type; resolve it once outside the measured loop. */
  private val getMethod: HttpMethod = HttpMethod.fromString("GET").get

  @Setup(Level.Trial)
  def setup(): Unit =
    val leaf = MeltKit[Future]()
    var i    = 0
    while i < routeCount do
      leaf.get(s"page$i/detail") { ctx => Future.successful(ctx.text("ok")) }
      i += 1

    // Wrap the leaf in `mountDepth` routers: root -> m0 -> m1 -> ... -> leaf
    var current = leaf
    var prefix  = List.empty[String]
    var d       = mountDepth
    while d > 0 do
      val parent = MeltKit[Future]()
      parent.route(s"m$d", current)
      prefix  = s"m$d" :: prefix
      current = parent
      d -= 1

    app       = current
    worstCase = prefix ::: List(s"page${ routeCount - 1 }", "detail")

    // Guard: a silently non-matching path would make every number meaningless.
    val ok = app.routes.exists(r => r.method == getMethod && PathSegment.matches(r.segments, worstCase))
    require(ok, s"benchmark setup is wrong: no route matches $worstCase (routes=${ app.routes.size })")
    require(
      app.routes.size == routeCount,
      s"expected $routeCount routes, got ${ app.routes.size }"
    )

  /** Resolution of a path that exists but sits last — the linear scan runs to the end. */
  @Benchmark
  def resolveWorstCase(bh: Blackhole): Unit =
    val hit = app.routes.find { r =>
      r.method == getMethod && PathSegment.matches(r.segments, worstCase)
    }
    bh.consume(hit)

  /** Resolution of a path that matches nothing — 404 handling still scans everything. */
  @Benchmark
  def resolveMiss(bh: Blackhole): Unit =
    val hit = app.routes.find { r =>
      r.method == getMethod && PathSegment.matches(r.segments, noMatch)
    }
    bh.consume(hit)

  /** `routes` alone, with no matching. Isolates the mount-folding cost from the scan. */
  @Benchmark
  def foldRoutesOnly(bh: Blackhole): Unit =
    bh.consume(app.routes)
