/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import scala.concurrent.{ ExecutionContext, Future }

import melt.runtime.render.{ RenderResult, ServerRenderer }
import melt.runtime.Async

import meltkit.*
import meltkit.codec.BodyDecoder

/** Blocking async SSR (`ctx.renderAsync`) through the JVM (Undertow) context, whose
  * `SyncRunner[Future]` resolves boundaries synchronously. Uses a same-thread EC so
  * every query completes inline (the `SyncRunner` contract), mirroring what
  * `<melt:await>` codegen emits: a marker span + a boundary registered on the scope. */
class JvmRenderAsyncTest extends munit.FunSuite:

  // Same-thread EC so `SyncRunner[Future].runSync` sees each Future already completed.
  private given ExecutionContext = ExecutionContext.parasitic

  private val list = ServerFn.query[Unit, List[Int]]("nums.list")

  private val template =
    Template.fromString("<!doctype html><html><head>%melt.head%</head><body>%melt.body%</body></html>")

  private def ctxWith(app: MeltKit[Future]): JvmMeltContext[Future, PathSpec.Empty, Unit] =
    new JvmMeltContext[Future, PathSpec.Empty, Unit](
      params = PathSpec.emptyValue,
      requestPath = "/await",
      bodyDecoder = summon[BodyDecoder[Unit]],
      rawBody = Future.successful(""),
      templateOpt = Some(template),
      app = Some(app)
    )

  /** Mirrors generated `<melt:await>` SSR code. */
  private def awaitShell(q: Query[List[Int]]): RenderResult =
    val r  = ServerRenderer()
    val id = SsrRenderScope.current.map(_.nextId()).getOrElse("melt-sb-0")
    r.push("<main>")
    r.push("<!--melt:sb:" + id + "-->")
    r.push("<p>Loading…</p>")
    r.push("<!--/melt:sb:" + id + "-->")
    r.push("</main>")
    SsrRenderScope.current.foreach(
      _.suspend(
        id,
        q,
        {
          case Async.Done(xs)  => RenderResult("<ul>" + xs.map(n => s"<li>$n</li>").mkString + "</ul>", "")
          case Async.Failed(_) => RenderResult("<p>failed</p>", "")
          case Async.Loading   => RenderResult("", "")
        }
      )
    )
    r.result()

  test("renderAsync resolves the query in-process, splices the branch, and seeds hydration"):
    val app = MeltKit[Future]()
    app.serve(list) { (_, _) => Future.successful(List(1, 2, 3)) }

    ctxWith(app).renderAsync(awaitShell(list())).map { resp =>
      val html = resp.body
      assert(html.contains("<ul><li>1</li><li>2</li><li>3</li></ul>"), html)
      assert(!html.contains("Loading…"), html)
      assert(html.contains("\"nums.list\\nnull\":[1,2,3]"), html)
    }

  test("a shell with no boundary renders synchronously (no seed script)"):
    val app = MeltKit[Future]()
    app.serve(list) { (_, _) => Future.successful(Nil) }

    ctxWith(app).renderAsync {
      val r = ServerRenderer()
      r.push("<main>static</main>")
      r.result()
    }.map { resp =>
      assert(resp.body.contains("<main>static</main>"), resp.body)
      assert(!resp.body.contains("data-melt-queries"), resp.body)
    }
