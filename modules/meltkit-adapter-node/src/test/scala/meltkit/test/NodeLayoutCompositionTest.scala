/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import scala.concurrent.{ ExecutionContext, Future }

import melt.runtime.render.RenderResult

import meltkit.*
import meltkit.codec.BodyDecoder

/** Layout composition across the Node context's render entry points.
  *
  * The four server contexts carry the same shape, and all four dropped layouts on the async
  * paths at once. Pinning it per adapter is what keeps one of them from drifting back.
  */
class NodeLayoutCompositionTest extends munit.FunSuite:

  private given ExecutionContext = ExecutionContext.parasitic

  private val template =
    Template.fromString("<!doctype html><html><head>%melt.head%</head><body>%melt.body%</body></html>")

  private def appWithLayout: MeltKit[Future] =
    val a = MeltKit[Future]()
    a.layout("")(child => RenderResult("<shell>" + child().body + "</shell>", ""))
    a

  private def ctxWith(app: MeltKit[Future]): NodeMeltContext[PathSpec.Empty, Unit] =
    new NodeMeltContext[PathSpec.Empty, Unit](
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

  test("renderPage composes the registered layout"):
    assert(ctxWith(appWithLayout).renderPage(page).body.contains("<shell><p>page</p></shell>"))

  test("renderAsync composes the registered layout"):
    ctxWith(appWithLayout).renderAsync(page).map { res =>
      assert(res.body.contains("<shell><p>page</p></shell>"), res.body)
    }
