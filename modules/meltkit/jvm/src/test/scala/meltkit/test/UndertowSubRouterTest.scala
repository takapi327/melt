/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import scala.compiletime.uninitialized
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.concurrent.{ Await, Future }

import meltkit.*
import meltkit.fetch.RequestInit

/** A guard declared on a mounted sub-router, over the built-in Undertow server.
  *
  * The scoping itself lives in shared code and is covered by the http4s and zio-http suites.
  * What is adapter-specific is the path the guard compares against: it has to be the one the
  * router matched on. Undertow and the Node binding both take it from [[Url.pathSegments]],
  * so this exercises that wiring end to end rather than in isolation.
  */
class UndertowSubRouterTest extends munit.FunSuite:

  private var server: RunningServer[Future] = uninitialized
  private var base:   String                = ""

  /** Undertow's builder takes the port up front and reports back what it was given, so an
    * ephemeral `0` never resolves to the bound port. A fixed high port keeps the test
    * self-contained. */
  private val port = 19099

  private def app: MeltKit[Future] =
    val admin = MeltKit[Future]()
    admin.use { (event, resolve) =>
      event.header("x-admin-token") match
        case Some("secret") => resolve()
        case _              => Future.successful(Response.text("forbidden").withStatus(403))
    }
    admin.get("users") { ctx => Future.successful(ctx.text("TOP SECRET")) }

    val root = MeltKit[Future]()
    root.get("public") { ctx => Future.successful(ctx.text("open")) }
    root.route("admin", admin)
    root

  override def beforeAll(): Unit =
    server = Await.result(UndertowServer.builder(app).withHost("127.0.0.1").withPort(port).start(), 30.seconds)
    base = s"http://127.0.0.1:$port"

  override def afterAll(): Unit =
    if server != null then Await.result(server.stop(), 30.seconds)

  private def get(path: String, token: Option[String] = None): (Int, String) =
    val headers = token.fold(Map.empty[String, String])(t => Map("x-admin-token" -> t))
    val res     = Await.result(Fetch(base + path, RequestInit(headers = headers)), 30.seconds)
    (res.status, Await.result(res.text(), 30.seconds))

  test("the sub-router's guard blocks an unauthenticated request"):
    assertEquals(get("/admin/users")._1, 403)

  test("the sub-router's guard admits an authenticated request"):
    assertEquals(get("/admin/users", Some("secret")), (200, "TOP SECRET"))

  test("the guard does not reach a sibling route outside the mount"):
    assertEquals(get("/public"), (200, "open"))

  test("a percent-encoded mount prefix does not slip past the guard"):
    assert(get("/%61dmin/users")._1 != 200)
