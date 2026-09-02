/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.http4s.test

import munit.CatsEffectSuite

import cats.effect.IO
import melt.runtime.render.RenderResult
import meltkit.*
import meltkit.adapter.http4s.Http4sAdapter
import meltkit.adapter.http4s.Http4sAdapter.given
import org.http4s.*
import org.http4s.headers.`Content-Type`
import org.http4s.implicits.*

/** What `app.route(prefix, sub)` has to carry across the mount.
  *
  * The router only ever moved `sub.routes`, so everything else a sub-router declared was
  * dropped without a word: its hooks (an auth bypass, covered in `Http4sAdapterTest`), its
  * server functions, its layouts and its page options. Whatever cannot be carried has to
  * fail at mount time rather than disappear.
  */
class SubRouterMountTest extends CatsEffectSuite:

  private case class Ping(n: Int)
  private case class Pong(msg: String)

  private val fn    = ServerFn.command[Ping, Pong]("sub.ping")
  private val query = ServerFn.query[Ping, Pong]("sub.look")

  private def jsonPost(path: Uri, body: String): org.http4s.Request[IO] =
    org.http4s
      .Request[IO](Method.POST, path)
      .withEntity(body)
      .withContentType(`Content-Type`(MediaType.application.json))

  test("a server function declared on a mounted sub-router keeps its wire path"):
    val sub = MeltKit[IO]()
    sub.serve(fn) { (in, _) => IO.pure(Pong(s"pong ${ in.n }")) }

    val app = MeltKit[IO]()
    app.route("admin", sub)

    Http4sAdapter
      .routes(app)
      .run(jsonPost(uri"/_melt/fn/sub.ping", """{"n":1}"""))
      .value
      .flatMap { resp =>
        assertEquals(resp.map(_.status), Some(Status.Ok))
        resp.get.as[String]
      }
      .map(body => assertEquals(body, """{"msg":"pong 1"}"""))

  test("a query declared on a mounted sub-router joins the refresh registry"):
    val sub = MeltKit[IO]()
    sub.serve(query) { (in, _) => IO.pure(Pong(s"look ${ in.n }")) }

    val app = MeltKit[IO]()
    app.route("admin", sub)

    assert(app.serverFnNames.contains("sub.look"))

  test("a server function name colliding across the mount is rejected"):
    val sub = MeltKit[IO]()
    sub.serve(fn) { (in, _) => IO.pure(Pong(s"sub ${ in.n }")) }

    val app = MeltKit[IO]()
    app.serve(fn) { (in, _) => IO.pure(Pong(s"parent ${ in.n }")) }

    intercept[IllegalArgumentException](app.route("admin", sub))

  test("layouts declared on a mounted sub-router apply under the prefix"):
    val sub = MeltKit[IO]()
    sub.layout("")(c => RenderResult("<div>" + c().body + "</div>", ""))

    val app = MeltKit[IO]()
    app.route("admin", sub)

    assertEquals(app.layoutsFor("/admin/users").size, 1)
    assertEquals(app.layoutsFor("/public").size, 0)

  test("page options declared on a mounted sub-router apply under the prefix"):
    val sub = MeltKit[IO]()
    sub.get("report", PageOptions(prerender = PrerenderOption.On)) { ctx => IO.pure(ctx.text("r")) }

    val app = MeltKit[IO]()
    app.route("admin", sub)

    assertEquals(
      app.pageOptionsFor(List(PathSegment.Static("admin"), PathSegment.Static("report"))).map(_.prerender),
      Some(PrerenderOption.On)
    )

  test("mounting a sub-router that sets an error handler fails instead of dropping it"):
    val sub = MeltKit[IO]()
    sub.onError((ctx, _) => IO.pure(ctx.text("boom")))
    intercept[IllegalArgumentException](MeltKit[IO]().route("admin", sub))

  test("mounting a sub-router that sets a not-found handler fails instead of dropping it"):
    val sub = MeltKit[IO]()
    sub.onNotFound(ctx => IO.pure(ctx.text("nope")))
    intercept[IllegalArgumentException](MeltKit[IO]().route("admin", sub))

  test("mounting a sub-router that configures CSP fails instead of dropping it"):
    val sub = MeltKit[IO]()
    sub.csp(CspConfig.default)
    intercept[IllegalArgumentException](MeltKit[IO]().route("admin", sub))

  test("mounting a sub-router that configures CORS fails instead of dropping it"):
    val sub = MeltKit[IO]()
    sub.cors(CorsConfig(allowedOrigins = CorsOrigins.allowlist("https://example.com")))
    intercept[IllegalArgumentException](MeltKit[IO]().route("admin", sub))
