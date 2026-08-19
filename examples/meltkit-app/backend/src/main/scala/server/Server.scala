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

/** SSR + Hydration entry point for the auto-derived full-stack app.
  *
  * `meltkit-app-backend`, derived by `MeltkitAppPlugin` from the single
  * `meltkit-app` project: it compiles `frontend/`'s `.melt` components in SSR
  * mode and reads the frontend's asset manifest — no manual client/server wiring.
  *
  * Defined flat with the Scala 3 `@main` annotation (no `object` wrapper).
  *
  * {{{ sbt "meltkit-app/run"   # links the frontend + starts the backend → :9095 }}}
  */
@main def serve(): Unit =
  val app = MeltKit[Future]()

  app.get("") { ctx =>
    Future.successful(ctx.render(App(App.Props(initial = 0))))
  }

  val template = scala.io.Source.fromResource("index.html").mkString

  UndertowServer
    .builder(app)
    .withPort(9095)
    .withTemplate(template)
    .withManifest(AssetManifest.manifest)
    .withClientDistDir(AssetManifest.clientDistDir)
    .start()
    .foreach(server => println(s"meltkit-app running on http://localhost:${ server.port }"))

  Thread.currentThread().join()
