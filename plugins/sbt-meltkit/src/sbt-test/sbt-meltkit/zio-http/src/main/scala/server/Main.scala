package server

import zio.*
import zio.http.Server

import meltkit.*
import meltkit.adapter.ziohttp.ZioHttpAdapter
import meltkit.adapter.ziohttp.ZioInstances.given

/** Compiles only if the plugin wired meltkit + the zio-http adapter onto the classpath. */
object Main extends ZIOAppDefault:

  type App[A] = ZIO[Any, Throwable, A]

  val app = MeltKit[App]()
  app.get("api/ping") { ctx => ZIO.succeed(ctx.text("pong")) }

  def run = Server.serve(ZioHttpAdapter.routes(app)).provide(Server.default)
