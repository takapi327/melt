/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.http4s.test

import munit.CatsEffectSuite

import melt.runtime.render.RenderResult

import cats.effect.IO
import meltkit.*
import meltkit.adapter.http4s.Http4sAdapter
import meltkit.adapter.http4s.Http4sAdapter.given
import org.http4s.*
import org.http4s.headers.`Content-Type`
import org.http4s.implicits.*
import org.typelevel.ci.*

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

  test("a sub-router's guard also protects the server functions it declared"):
    val sub = MeltKit[IO]()
    sub.use { (event, resolve) =>
      event.header("x-admin-token") match
        case Some("secret") => resolve()
        case _              => IO.pure(meltkit.Response.text("forbidden").withStatus(403))
    }
    sub.serve(fn) { (in, _) => IO.pure(Pong(s"pong ${ in.n }")) }

    val app = MeltKit[IO]()
    app.route("admin", sub)

    Http4sAdapter
      .routes(app)
      .run(jsonPost(uri"/_melt/fn/sub.ping", """{"n":1}"""))
      .value
      .map(resp => assertEquals(resp.map(_.status.code), Some(403)))

  test("layouts survive a mount on the base router too"):
    val sub = MeltKit[IO]()
    sub.layout("")(c => RenderResult("<div>" + c().body + "</div>", ""))

    val app = MeltKit[IO]()
    app.route("admin", sub)
    assertEquals(app.layoutsFor("/admin/users").size, 1)

  test("a server-function guard survives a second level of mounting"):
    val inner = MeltKit[IO]()
    inner.use { (event, resolve) =>
      event.header("x-admin-token") match
        case Some("secret") => resolve()
        case _              => IO.pure(meltkit.Response.text("forbidden").withStatus(403))
    }
    inner.serve(fn) { (in, _) => IO.pure(Pong(s"pong ${ in.n }")) }

    val middle = MeltKit[IO]()
    middle.route("v1", inner)

    val app = MeltKit[IO]()
    app.route("api", middle)

    val body = """{"n":1}"""
    for
      blocked <- Http4sAdapter.routes(app).run(jsonPost(uri"/_melt/fn/sub.ping", body)).value
      allowed <- Http4sAdapter
                   .routes(app)
                   .run(jsonPost(uri"/_melt/fn/sub.ping", body).putHeaders(Header.Raw(ci"X-Admin-Token", "secret")))
                   .value
    yield
      assertEquals(blocked.map(_.status.code), Some(403))
      assertEquals(allowed.map(_.status.code), Some(200))

  private def guarded(sub: MeltKit[IO]): MeltKit[IO] =
    sub.use { (event, resolve) =>
      event.header("x-admin-token") match
        case Some("secret") => resolve()
        case _              => IO.pure(meltkit.Response.text("forbidden").withStatus(403))
    }
    sub

  test("a multi-segment mount prefix is split like every other path string"):
    val sub = MeltKit[IO]()
    sub.get("thing") { ctx => IO.pure(ctx.text("deep")) }

    val app = MeltKit[IO]()
    app.route("api/v1", sub)

    Http4sAdapter
      .routes(app)
      .run(org.http4s.Request[IO](Method.GET, uri"/api/v1/thing"))
      .value
      .flatMap { resp =>
        assertEquals(resp.map(_.status.code), Some(200))
        resp.get.as[String]
      }
      .map(body => assertEquals(body, "deep"))

  test("a guard scopes to the whole multi-segment mount prefix"):
    val sub = guarded(MeltKit[IO]())
    sub.get("thing") { ctx => IO.pure(ctx.text("deep")) }

    val app = MeltKit[IO]()
    app.get("api/open") { ctx => IO.pure(ctx.text("open")) }
    app.route("api/v1", sub)

    for
      blocked <- Http4sAdapter.routes(app).run(org.http4s.Request[IO](Method.GET, uri"/api/v1/thing")).value
      sibling <- Http4sAdapter.routes(app).run(org.http4s.Request[IO](Method.GET, uri"/api/open")).value
    yield
      assertEquals(blocked.map(_.status.code), Some(403))
      assertEquals(sibling.map(_.status.code), Some(200))

  test("an empty mount prefix mounts at the root"):
    val sub = MeltKit[IO]()
    sub.get("thing") { ctx => IO.pure(ctx.text("root")) }

    val app = MeltKit[IO]()
    app.route("", sub)

    Http4sAdapter
      .routes(app)
      .run(org.http4s.Request[IO](Method.GET, uri"/thing"))
      .value
      .flatMap { resp =>
        assertEquals(resp.map(_.status.code), Some(200))
        resp.get.as[String]
      }
      .map(body => assertEquals(body, "root"))

  test("a guard on a root-mounted sub-router applies to its routes"):
    val sub = guarded(MeltKit[IO]())
    sub.get("thing") { ctx => IO.pure(ctx.text("root")) }

    val app = MeltKit[IO]()
    app.route("", sub)

    Http4sAdapter
      .routes(app)
      .run(org.http4s.Request[IO](Method.GET, uri"/thing"))
      .value
      .map(resp => assertEquals(resp.map(_.status.code), Some(403)))

  test("mounting a guarded router at the root protects the whole app"):
    val sub = guarded(MeltKit[IO]())
    sub.get("thing") { ctx => IO.pure(ctx.text("sub")) }

    val app = MeltKit[IO]()
    app.get("parent") { ctx => IO.pure(ctx.text("parent")) }
    app.route("", sub)

    Http4sAdapter
      .routes(app)
      .run(org.http4s.Request[IO](Method.GET, uri"/parent"))
      .value
      .map(resp => assertEquals(resp.map(_.status.code), Some(403)))

  test("a guarded mount protects its prefix even for another router mounted there"):
    val secured = guarded(MeltKit[IO]())
    secured.get("secret") { ctx => IO.pure(ctx.text("secret")) }

    val open = MeltKit[IO]()
    open.get("open") { ctx => IO.pure(ctx.text("open")) }

    val app = MeltKit[IO]()
    app.route("api", secured)
    app.route("api", open)

    Http4sAdapter
      .routes(app)
      .run(org.http4s.Request[IO](Method.GET, uri"/api/open"))
      .value
      .map(resp => assertEquals(resp.map(_.status.code), Some(403)))

  test("a route added to a sub-router after mounting is still served"):
    val sub = MeltKit[IO]()
    sub.get("early") { ctx => IO.pure(ctx.text("early")) }

    val app = MeltKit[IO]()
    app.route("admin", sub)
    sub.get("late") { ctx => IO.pure(ctx.text("late")) }

    Http4sAdapter
      .routes(app)
      .run(org.http4s.Request[IO](Method.GET, uri"/admin/late"))
      .value
      .map(resp => assertEquals(resp.map(_.status.code), Some(200)))

  test("a guard added to a sub-router after mounting still guards it"):
    val sub = MeltKit[IO]()
    sub.get("thing") { ctx => IO.pure(ctx.text("thing")) }

    val app = MeltKit[IO]()
    app.route("admin", sub)
    guarded(sub)

    Http4sAdapter
      .routes(app)
      .run(org.http4s.Request[IO](Method.GET, uri"/admin/thing"))
      .value
      .map(resp => assertEquals(resp.map(_.status.code), Some(403)))

  test("mounting a sub-router with server functions twice is rejected"):
    val sub = MeltKit[IO]()
    sub.serve(fn) { (in, _) => IO.pure(Pong(s"pong ${ in.n }")) }

    val app = MeltKit[IO]()
    app.route("a", sub)
    intercept[IllegalArgumentException](app.route("b", sub))

  test("a leading slash in the mount prefix is ignored"):
    val sub = MeltKit[IO]()
    sub.get("thing") { ctx => IO.pure(ctx.text("ok")) }

    val app = MeltKit[IO]()
    app.route("/admin/", sub)

    Http4sAdapter
      .routes(app)
      .run(org.http4s.Request[IO](Method.GET, uri"/admin/thing"))
      .value
      .map(resp => assertEquals(resp.map(_.status.code), Some(200)))

  test("a server function added to a sub-router after mounting is served"):
    val sub = MeltKit[IO]()

    val app = MeltKit[IO]()
    app.route("admin", sub)
    sub.serve(fn) { (in, _) => IO.pure(Pong(s"pong ${ in.n }")) }

    Http4sAdapter
      .routes(app)
      .run(jsonPost(uri"/_melt/fn/sub.ping", """{"n":1}"""))
      .value
      .map(resp => assertEquals(resp.map(_.status.code), Some(200)))

  test("a router cannot be mounted into itself"):
    val app = MeltKit[IO]()
    intercept[IllegalArgumentException](app.route("self", app))

  private def catchAllApp(guard: Boolean): MeltKit[IO] =
    val sub = MeltKit[IO]()
    if guard then guarded(sub)
    sub.getAll { ctx => IO.pure(ctx.text("caught " + ctx.requestPath)) }

    val app = MeltKit[IO]()
    app.get("other") { ctx => IO.pure(ctx.text("other")) }
    app.route("admin", sub)
    app

  private def status(app: MeltKit[IO], path: String): IO[Option[Int]] =
    Http4sAdapter
      .routes(app)
      .run(org.http4s.Request[IO](Method.GET, Uri.unsafeFromString(path)))
      .value
      .map(_.map(_.status.code))

  test("a mounted catch-all claims one level under its mount"):
    status(catchAllApp(guard = false), "/admin/x").map(assertEquals(_, Some(200)))

  test("a mounted catch-all claims paths deeper than one level"):
    status(catchAllApp(guard = false), "/admin/x/y/z").map(assertEquals(_, Some(200)))

  test("a mounted catch-all claims the mount root itself"):
    status(catchAllApp(guard = false), "/admin").map(assertEquals(_, Some(200)))

  test("a mounted catch-all does not claim paths outside its mount"):
    status(catchAllApp(guard = false), "/other").map(assertEquals(_, Some(200)))

  test("a guard covers everything its mounted catch-all claims"):
    val app = catchAllApp(guard = true)
    for
      shallow <- status(app, "/admin/x")
      deep    <- status(app, "/admin/x/y/z")
      root    <- status(app, "/admin")
      outside <- status(app, "/other")
    yield
      assertEquals(shallow, Some(403))
      assertEquals(deep, Some(403))
      assertEquals(root, Some(403))
      assertEquals(outside, Some(200))

  test("a catch-all on the served router still claims every path"):
    val app = MeltKit[IO]()
    app.getAll { ctx => IO.pure(ctx.text("all")) }
    for
      shallow <- status(app, "/x")
      deep    <- status(app, "/x/y/z")
      root    <- status(app, "/")
    yield
      assertEquals(shallow, Some(200))
      assertEquals(deep, Some(200))
      assertEquals(root, Some(200))

  test("a specific route in the sub-router still beats its own catch-all"):
    val sub = MeltKit[IO]()
    sub.get("users") { ctx => IO.pure(ctx.text("users")) }
    sub.getAll { ctx => IO.pure(ctx.text("caught")) }

    val app = MeltKit[IO]()
    app.route("admin", sub)

    Http4sAdapter
      .routes(app)
      .run(org.http4s.Request[IO](Method.GET, uri"/admin/users"))
      .value
      .flatMap(resp => resp.get.as[String])
      .map(assertEquals(_, "users"))

  test("a root-mounted catch-all does not shadow the mounting router's own routes"):
    val sub = MeltKit[IO]()
    sub.getAll { ctx => IO.pure(ctx.text("caught")) }

    val app = MeltKit[IO]()
    app.route("", sub)
    app.get("parent") { ctx => IO.pure(ctx.text("parent")) }

    Http4sAdapter
      .routes(app)
      .run(org.http4s.Request[IO](Method.GET, uri"/parent"))
      .value
      .flatMap(resp => resp.get.as[String])
      .map(assertEquals(_, "parent"))

  test("two routers cannot be mounted into each other"):
    val a = MeltKit[IO]()
    val b = MeltKit[IO]()
    a.route("b", b)
    intercept[IllegalArgumentException](b.route("a", a))

  test("a guarded mount protects a path the mounting router declares itself"):
    val sub = guarded(MeltKit[IO]())
    sub.get("users") { ctx => IO.pure(ctx.text("sub")) }

    val app = MeltKit[IO]()
    app.get("admin/users") { ctx => IO.pure(ctx.text("parent")) }
    app.route("admin", sub)

    status(app, "/admin/users").map(assertEquals(_, Some(403)))

  test("a guarded mount protects every method under its prefix"):
    val sub = guarded(MeltKit[IO]())
    sub.post("users") { ctx => IO.pure(ctx.text("created")) }

    val app = MeltKit[IO]()
    app.get("admin/users") { ctx => IO.pure(ctx.text("read")) }
    app.route("admin", sub)

    status(app, "/admin/users").map(assertEquals(_, Some(403)))

  private val secret = ServerFn.query[Ping, Pong]("sub.secret")
  private val poke   = ServerFn.command[Ping, Pong]("parent.poke")

  /** A guarded sub-router owning a query, mounted under an app with an open command. */
  private def mixedApp: MeltKit[IO] =
    val sub = guarded(MeltKit[IO]())
    sub.serve(secret) { (in, _) => IO.pure(Pong(s"classified ${ in.n }")) }

    val app = MeltKit[IO]()
    app.serve(poke) { (in, _) => IO.pure(Pong(s"ok ${ in.n }")) }
    app.route("admin", sub)
    app

  test("a single-flight refresh cannot reach a guarded router's query"):
    val envelope =
      """{"input":{"n":1},"refresh":[{"name":"sub.secret","args":"{\"n\":2}"}]}"""
    Http4sAdapter
      .routes(mixedApp)
      .run(jsonPost(uri"/_melt/fn/parent.poke", envelope).putHeaders(Header.Raw(ci"X-Melt-Sf", "1")))
      .value
      .flatMap(resp => resp.get.as[String])
      .map(body => assert(!body.contains("classified"), s"leaked through single-flight: $body"))

  test("a guarded router's query is still refreshable from its own router"):
    val sub = MeltKit[IO]()
    sub.serve(secret) { (in, _) => IO.pure(Pong(s"classified ${ in.n }")) }
    sub.serve(poke) { (in, _) => IO.pure(Pong(s"ok ${ in.n }")) }

    val app = MeltKit[IO]()
    app.route("admin", sub)

    val envelope =
      """{"input":{"n":1},"refresh":[{"name":"sub.secret","args":"{\"n\":2}"}]}"""
    Http4sAdapter
      .routes(app)
      .run(jsonPost(uri"/_melt/fn/parent.poke", envelope).putHeaders(Header.Raw(ci"X-Melt-Sf", "1")))
      .value
      .flatMap(resp => resp.get.as[String])
      .map(body => assert(body.contains("classified"), s"mounted query not refreshable: $body"))

  /** A guarded router mounted at `admin`, alongside an open sibling route. */
  private def adminMount: MeltKit[IO] =
    val sub = guarded(MeltKit[IO]())
    sub.get("users") { ctx => IO.pure(ctx.text("TOP SECRET")) }
    val app = MeltKit[IO]()
    app.get("public") { ctx => IO.pure(ctx.text("open")) }
    app.route("admin", sub)
    app

  test("a guarded mount answers for a path that does not exist under it"):
    val app = adminMount
    for
      exists  <- status(app, "/admin/users")
      missing <- status(app, "/admin/does-not-exist")
      deep    <- status(app, "/admin/a/b/c")
    yield
      assertEquals(exists, Some(403))
      assertEquals(missing, Some(403))
      assertEquals(deep, Some(403))

  test("an authorised caller still gets 404 for a path that does not exist"):
    val app = adminMount
    Http4sAdapter
      .routes(app)
      .run(
        org.http4s
          .Request[IO](Method.GET, uri"/admin/does-not-exist")
          .putHeaders(Header.Raw(ci"X-Admin-Token", "secret"))
      )
      .value
      .map(resp => assertEquals(resp.map(_.status.code), None))
