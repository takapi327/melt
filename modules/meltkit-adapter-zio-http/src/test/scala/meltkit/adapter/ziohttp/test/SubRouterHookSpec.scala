/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.ziohttp.test

import meltkit.*
import meltkit.adapter.ziohttp.ZioHttpAdapter
import meltkit.adapter.ziohttp.ZioInstances.given
import zio.*
import zio.http.{ Request, Response as ZResponse, Status, URL }
import zio.test.*

/** Hooks carried by `app.route(prefix, sub)`.
  *
  * A guard written on a sub-router has to keep guarding the routes it arrived with. The
  * scoping compares against `RequestEvent.pathSegments`, which every adapter derives from
  * the same expression it routes on — so a request the router accepts can never be one the
  * guard skipped.
  */
object SubRouterHookSpec extends ZIOSpecDefault:

  private type App[A] = ZIO[Any, Throwable, A]

  private def guardedApp: MeltKit[App] =
    val admin = MeltKit[App]()
    admin.use { (event, resolve) =>
      event.header("x-admin-token") match
        case Some("secret") => resolve()
        case _              => ZIO.succeed(Response.text("forbidden").withStatus(403))
    }
    admin.get("users")(ctx => ZIO.succeed(ctx.text("TOP SECRET")))

    val app = MeltKit[App]()
    app.get("public")(ctx => ZIO.succeed(ctx.text("open")))
    app.route("admin", admin)
    app

  private def get(path: String, token: Option[String] = None): ZIO[Any, Throwable, (Status, String)] =
    val base    = Request.get(URL.decode(path).toOption.get)
    val request = token.fold(base)(t => base.addHeader("x-admin-token", t))
    ZIO.scoped {
      ZioHttpAdapter.routes(guardedApp).runZIO(request).flatMap(res => res.body.asString.map((res.status, _)))
    }

  def spec = suite("sub-router hooks over zio-http")(
    test("a hook on a mounted sub-router still guards its routes") {
      get("/admin/users").map((status, _) => assertTrue(status == Status.Forbidden))
    },
    test("an authorised request passes the mounted guard") {
      get("/admin/users", Some("secret")).map { (status, body) =>
        assertTrue(status == Status.Ok, body == "TOP SECRET")
      }
    },
    test("the guard does not run outside its mount prefix") {
      get("/public").map((status, body) => assertTrue(status == Status.Ok, body == "open"))
    },
    test("a percent-encoded prefix cannot slip past the guard") {
      get("/%61dmin/users").map((status, body) => assertTrue(status != Status.Ok, body != "TOP SECRET"))
    }
  )
