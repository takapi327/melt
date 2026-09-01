/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.ziohttp.test

import java.io.File
import java.nio.file.Files

import zio.*
import zio.http.Server

import melt.runtime.render.{ RenderResult, ServerRenderer }
import melt.runtime.Async

import meltkit.*
import meltkit.adapter.ziohttp.ZioHttpAdapter
import meltkit.adapter.ziohttp.ZioInstances.given

/** A real zio-http server whose single page has three `<melt:await>` boundaries resolving at
  * staggered delays, used to measure when each chunk actually reaches a client socket.
  *
  * `RenderStreamSpec` proves the stream carries the right bytes; it cannot prove they leave the
  * server incrementally, because `runCollect` drains the whole stream before returning. Only a
  * client that timestamps its reads can tell an incremental flush from a buffered one.
  */
object StreamTimingMain extends ZIOAppDefault:

  private type App[A] = ZIO[Any, Throwable, A]

  private val fast   = ServerFn.query[Unit, String]("timing.fast")
  private val medium = ServerFn.query[Unit, String]("timing.medium")
  private val slow   = ServerFn.query[Unit, String]("timing.slow")

  /** The markup `<melt:await>` lowers to, once per boundary: a marked region holding a pending
    * fallback, with the resolved branch registered on the render scope. */
  private def boundary(r: ServerRenderer, label: String, q: Query[String]): Unit =
    val id = SsrRenderScope.current.map(_.nextId()).getOrElse("melt-sb-0")
    r.push("<!--melt:sb:" + id + "-->")
    r.push(s"""<p class="loading">$label loading…</p>""")
    r.push("<!--/melt:sb:" + id + "-->")
    SsrRenderScope.current.foreach(
      _.suspend(
        id,
        q,
        {
          case Async.Done(v)   => RenderResult(s"""<p class="done">$label: $v</p>""", "")
          case Async.Failed(_) => RenderResult(s"""<p class="error">$label failed</p>""", "")
          case Async.Loading   => RenderResult("", "")
        }
      )
    )

  private def page: RenderResult =
    val r = ServerRenderer()
    r.push("<main>")
    boundary(r, "fast", fast())
    boundary(r, "medium", medium())
    boundary(r, "slow", slow())
    r.push("</main>")
    r.result()

  /** The same three boundaries, slowest first in document order. */
  private def reversedPage: RenderResult =
    val r = ServerRenderer()
    r.push("<main>")
    boundary(r, "slow", slow())
    boundary(r, "medium", medium())
    boundary(r, "fast", fast())
    r.push("</main>")
    r.result()

  private def app: MeltKit[App] =
    val a = MeltKit[App]()
    a.serve(fast)((_, _) => ZIO.succeed("300ms").delay(300.millis))
    a.serve(medium)((_, _) => ZIO.succeed("900ms").delay(900.millis))
    a.serve(slow)((_, _) => ZIO.succeed("1500ms").delay(1500.millis))
    a.get("")(ctx => ZIO.succeed(ctx.renderStream(page)).flatten)
    a.get("async")(ctx => ZIO.succeed(ctx.renderAsync(page)).flatten)
    a.get("reversed")(ctx => ZIO.succeed(ctx.renderStream(reversedPage)).flatten)
    a

  private def dist: File =
    val dir = Files.createTempDirectory("melt-zio-timing").toFile
    Files.writeString(
      new File(dir, "index.html").toPath,
      """<!doctype html><html lang="%melt.lang%"><head>%melt.head%</head><body>%melt.body%</body></html>"""
    )
    dir

  def run =
    ZioHttpAdapter
      .ssrRoutes(app, dist, ViteManifest.empty)
      .flatMap(Server.serve)
      .provide(Server.defaultWithPort(9099))
