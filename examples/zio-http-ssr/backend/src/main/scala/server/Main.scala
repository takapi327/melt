/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package server

import java.io.File

import zio.*
import zio.http.Server

import components.App
import generated.AssetManifest
import meltkit.*
import meltkit.adapter.ziohttp.ZioHttpAdapter
import meltkit.adapter.ziohttp.ZioInstances.given

/** A service resolved from the ZIO environment inside route handlers. */
trait Greeter:
  def greet:      UIO[String]
  def nextVisit:  UIO[Int]

object Greeter:
  val live: ULayer[Greeter] =
    ZLayer.fromZIO(
      Ref.make(0).map { visits =>
        new Greeter:
          def greet     = ZIO.succeed("Melt on zio-http")
          def nextVisit = visits.updateAndGet(_ + 1)
      }
    )

/** SSR + hydration on zio-http, derived by `MeltkitAppPlugin`.
  *
  * The point of interest is `App`'s type: `MeltKit` takes a unary type constructor, so ZIO is
  * passed as `[A] =>> ZIO[Env, Throwable, A]`. That one alias is all it takes to keep the
  * environment, and handlers use `ZIO.serviceWithZIO` / `ZLayer` as they would anywhere else —
  * Melt's core is unchanged.
  *
  * Named `Main` rather than `Server` so it does not shadow `zio.http.Server`.
  *
  * {{{ sbt "zio-http-ssr/run"   # links the frontend + starts the backend → :9094 }}}
  */
object Main extends ZIOAppDefault:

  type Env    = Greeter
  type App[A] = ZIO[Env, Throwable, A]

  private def buildApp(): MeltKit[App] =
    val app = MeltKit[App]()

    app.get("") { ctx =>
      for
        greeting <- ZIO.serviceWithZIO[Greeter](_.greet)
        visitors <- ZIO.serviceWithZIO[Greeter](_.nextVisit)
      yield ctx.render(App(App.Props(greeting = greeting, visitors = visitors)))
    }

    app.get("api/health") { ctx => ZIO.succeed(ctx.text("ok")) }

    app

  def run =
    ZioHttpAdapter
      .ssrRoutes(buildApp(), new File(AssetManifest.clientDistDir), AssetManifest.manifest)
      .flatMap { routes =>
        Console.printLine("zio-http-ssr running on http://localhost:9094") *>
          Server.serve(routes)
      }
      .provide(Server.defaultWithPort(9094), Greeter.live)
