/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.ziohttp.test

import zio.*
import zio.test.*

import melt.runtime.render.RenderResult

import meltkit.*
import meltkit.adapter.ziohttp.ZioHttpMeltContext
import meltkit.adapter.ziohttp.ZioInstances.given
import meltkit.codec.BodyDecoder

/** Layout composition across the zio-http context's render entry points.
  *
  * The four server contexts carry the same shape, and all four dropped layouts on the async
  * paths at once. Pinning it per adapter is what keeps one of them from drifting back.
  */
object LayoutCompositionSpec extends ZIOSpecDefault:

  private type App[A] = ZIO[Any, Throwable, A]

  private val template =
    Template.fromString("<!doctype html><html><head>%melt.head%</head><body>%melt.body%</body></html>")

  private def appWithLayout: MeltKit[App] =
    val a = MeltKit[App]()
    a.layout("")(child => RenderResult("<shell>" + child().body + "</shell>", ""))
    a

  private def ctxWith(app: MeltKit[App]): ZioHttpMeltContext[Any, PathSpec.Empty, Unit] =
    new ZioHttpMeltContext[Any, PathSpec.Empty, Unit](
      PathSpec.emptyValue,
      zio.http.Request.get(zio.http.URL.decode("/page").toOption.get),
      summon[BodyDecoder[Unit]],
      new Locals(),
      Some(template),
      ViteManifest.empty,
      "en",
      "",
      None,
      Some(app),
      None
    )

  private val page = RenderResult("<p>page</p>", "")

  def spec = suite("layout composition over zio-http")(
    test("render composes the registered layout") {
      ZIO.succeed(assertTrue(ctxWith(appWithLayout).render(page).body.contains("<shell><p>page</p></shell>")))
    },
    test("renderAsync composes the registered layout") {
      ctxWith(appWithLayout).renderAsync(page).map { res =>
        assertTrue(res.body.contains("<shell><p>page</p></shell>"))
      }
    },
    test("renderPage composes the registered layout") {
      ZIO.succeed(assertTrue(ctxWith(appWithLayout).renderPage(page).body.contains("<shell><p>page</p></shell>")))
    }
  )
