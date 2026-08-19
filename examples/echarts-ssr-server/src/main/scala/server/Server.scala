/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package server

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import components.ChartPage
import generated.AssetManifest
import meltkit.*

/** MeltKit[Future] SSR + Hydration server on the built-in Undertow (JVM).
  *
  * Serves the ECharts page: the server renders the static shell (chart
  * placeholder + fallback list + button) via SSR, and the client hydration
  * bundle draws the echarts chart in `onMount` after hydrating. The browser-only
  * echarts is isolated in the platform-split `ChartHost` (see the crossProject
  * `echarts-ssr`), and the bare `"echarts"` import is resolved at runtime by the
  * import map in `index.html` (no bundler needed in dev).
  *
  * {{{
  * sbt "echarts-ssrJS/fastLinkJS"   // link the client hydration bundle
  * sbt "echarts-ssr-server/run"     // then open http://localhost:9092
  * }}}
  */
object Server:

  def main(args: Array[String]): Unit =
    val app = MeltKit[Future]()

    val categories = List("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val initial    = List(120, 200, 150, 80, 70, 110, 130)

    app.get("") { ctx =>
      Future.successful(ctx.render(ChartPage(ChartPage.Props(categories, initial))))
    }

    val template = scala.io.Source.fromResource("index.html").mkString

    UndertowServer
      .builder(app)
      .withHost("0.0.0.0")
      .withPort(9092)
      .withTemplate(template)
      .withManifest(AssetManifest.manifest)
      .withClientDistDir(AssetManifest.clientDistDir)
      .start()
      .foreach(server => println(s"echarts-ssr running on http://localhost:${ server.port }"))

    Thread.currentThread().join()
