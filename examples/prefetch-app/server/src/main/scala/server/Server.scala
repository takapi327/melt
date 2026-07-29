/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package server

import scala.concurrent.duration.*

import cats.effect.*
import com.comcast.ip4s.*
import components.*
import generated.AssetManifest
import meltkit.*
import meltkit.adapter.http4s.CirceBodyDecoder.given
import meltkit.adapter.http4s.CirceBodyEncoder.given
import meltkit.adapter.http4s.Http4sAdapter
import meltkit.adapter.http4s.Http4sAdapter.given
import org.http4s.ember.server.EmberServerBuilder

/** Prefetch demo server: serves the `Api.items` query (with an artificial delay so
  * the difference between a warm navigation and a cold one is visible) plus the SPA
  * static assets and `index.html` shell. The client (`Main.scala`) mounts the router
  * and prefetches `Api.items` on hover.
  *
  * {{{ sbt "prefetch-app-server/run" }}}  // → http://localhost:8080
  */
object Server extends IOApp.Simple:

  private val catalogue = List(
    Item(1, "Widget", "A dependable little widget."),
    Item(2, "Gadget", "Now with more gadgetry."),
    Item(3, "Sprocket", "Fits most sprocket-shaped holes."),
    Item(4, "Cog", "An essential cog in the machine.")
  )

  private def buildApp(): MeltKit[IO] =
    val app = MeltKit[IO]()
    // Artificial delay so a cold /items shows the loading state, while a hover-warmed
    // navigation renders the list immediately from the prefetch cache.
    app.serve(Api.items) { (_, _) => IO.sleep(800.millis).as(catalogue) }
    app

  def run: IO[Unit] =
    for
      httpApp <- Http4sAdapter
                   .spaRoutes(
                     buildApp(),
                     fs2.io.file.Path(AssetManifest.clientDistDir),
                     AssetManifest.manifest
                   )
                   .map(_.orNotFound)
      _ <- EmberServerBuilder
             .default[IO]
             .withHost(host"0.0.0.0")
             .withPort(port"8080")
             .withHttpApp(httpApp)
             .build
             .use(_ => IO.println("prefetch-app → http://localhost:8080") *> IO.never)
    yield ()
