/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package server

import cats.effect.*
import com.comcast.ip4s.*
import components.*
import generated.AssetManifest
import meltkit.*
import meltkit.adapter.http4s.CirceBodyDecoder.given
import meltkit.adapter.http4s.CirceBodyEncoder.given
import meltkit.adapter.http4s.Http4sAdapter
import meltkit.adapter.http4s.Http4sAdapter.given
import meltkit.env.PrivateEnv
import org.http4s.ember.server.EmberServerBuilder

/** Server-only env demo. Reads a private value with [[meltkit.env.PrivateEnv]] (which
  * exists only in the JVM artifact, so the client physically cannot reach it) and
  * returns only a derived, non-secret greeting to the browser.
  *
  * {{{ GREETING="Hi there" sbt "server-env-server/run" }}}  // → http://localhost:8080
  */
object Server extends IOApp.Simple:

  private def buildApp(): MeltKit[IO] =
    val app = MeltKit[IO]()

    // A secret / private config value — read here on the server, never shipped to the
    // client. The response exposes only the non-secret greeting built from it.
    val salutation = PrivateEnv.optional[String]("GREETING").getOrElse("Hello")

    app.serve(Api.greeting) { (_, _) => IO.pure(s"$salutation from the server!") }
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
             .use(_ => IO.println("server-env → http://localhost:8080") *> IO.never)
    yield ()
