/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package server

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import components.App
import generated.AssetManifest
import meltkit.*

/** SSR + Hydration server for the auto-derived full-stack app.
  *
  * This module is `meltkit-app-backend`, derived by `MeltkitAppPlugin` from the
  * single `meltkit-app` project. It compiles `frontend/`'s `.melt` components in
  * SSR mode and reads the frontend's asset manifest — no manual client/server
  * wiring in build.sbt.
  *
  * {{{
  * sbt "meltkit-app-frontend/fastLinkJS"   # hydration bundle
  * sbt "meltkit-app-backend/run"           # http://localhost:9095
  * }}}
  */
object Server:
  def main(args: Array[String]): Unit =
    val app = MeltKit[Future]()

    app.get("") { ctx =>
      Future.successful(ctx.render(App(App.Props(initial = 0))))
    }

    UndertowServer
      .builder(app)
      .withPort(9095)
      .withTemplate("<!doctype html><html lang=\"en\"><head><meta charset=\"UTF-8\">%melt.head%</head><body>%melt.body%</body></html>")
      .withManifest(AssetManifest.manifest)
      .withClientDistDir(AssetManifest.clientDistDir)
      .start()
      .foreach(server => println(s"meltkit-app running on http://localhost:${ server.port }"))

    Thread.currentThread().join()
