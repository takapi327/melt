/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration.*

import melt.runtime.render.RenderResult

import meltkit.*
import meltkit.codec.BodyDecoder

/** Layout composition across the JVM context's render entry points. */
class JvmLayoutCompositionTest extends munit.FunSuite:

  private given ExecutionContext = ExecutionContext.parasitic

  private val template =
    Template.fromString("<!doctype html><html><head>%melt.head%</head><body>%melt.body%</body></html>")

  private def appWithLayout: MeltKit[Future] =
    val a = MeltKit[Future]()
    a.layout("")(child => RenderResult("<shell>" + child().body + "</shell>", ""))
    a

  private def ctxWith(app: MeltKit[Future]): JvmMeltContext[Future, PathSpec.Empty, Unit] =
    new JvmMeltContext[Future, PathSpec.Empty, Unit](
      params      = PathSpec.emptyValue,
      requestPath = "/page",
      bodyDecoder = summon[BodyDecoder[Unit]],
      rawBody     = Future.successful(""),
      templateOpt = Some(template),
      app         = Some(app)
    )

  private val page = RenderResult("<p>page</p>", "")

  test("render composes the registered layout"):
    assert(ctxWith(appWithLayout).render(page).body.contains("<shell><p>page</p></shell>"))

  test("renderAsync composes the registered layout"):
    val res = Await.result(ctxWith(appWithLayout).renderAsync(page), 5.seconds)
    assert(res.body.contains("<shell><p>page</p></shell>"), res.body)

  test("renderStream composes the registered layout"):
    val res = Await.result(ctxWith(appWithLayout).renderStream(page), 5.seconds)
    assert(res.body.contains("<shell><p>page</p></shell>"), res.body)

  test("distinct queries run in the order they were registered"):
    val q     = ServerFn.query[Int, Int]("ordered.q")
    val order = scala.collection.mutable.ListBuffer.empty[Int]

    val a = MeltKit[Future]()
    a.serve(q) { (in, _) => Future { order += in; in } }

    def boundaryOf(r: melt.runtime.render.ServerRenderer, n: Int): Unit =
      val id = SsrRenderScope.current.map(_.nextId()).getOrElse("melt-sb-0")
      r.push("<!--melt:sb:" + id + "-->")
      r.push("<i></i>")
      r.push("<!--/melt:sb:" + id + "-->")
      SsrRenderScope.current.foreach(
        _.suspend(
          id,
          q(n),
          {
            case melt.runtime.Async.Done(v) => RenderResult(s"<v>$v</v>", "")
            case _                          => RenderResult("", "")
          }
        )
      )

    def page6: RenderResult =
      val r = melt.runtime.render.ServerRenderer()
      (1 to 6).foreach(n => boundaryOf(r, n))
      r.result()

    Await.result(ctxWith(a).renderAsync(page6), 5.seconds)
    assertEquals(order.toList, List(1, 2, 3, 4, 5, 6))
