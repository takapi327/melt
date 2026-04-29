/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.http4s.test

import munit.CatsEffectSuite

import cats.effect.IO
import io.circe.Codec
import melt.runtime.render.RenderResult
import meltkit.*
import meltkit.adapter.http4s.CirceBodyDecoder.given
import meltkit.adapter.http4s.CirceBodyEncoder.given
import meltkit.adapter.http4s.Http4sAdapter
import meltkit.adapter.http4s.Http4sAdapter.given
import meltkit.codec.*
import org.http4s.*
import org.http4s.implicits.*

/** Integration tests for [[Http4sAdapter.routes]] on Node.js.
  *
  * Exercises the same route-dispatch logic as [[Http4sAdapterTest]] on JVM,
  * confirming that `Http4sAdapter` (now in `shared/`) compiles and runs
  * correctly on the JS/Node.js platform.
  *
  * Note: `Http4sAdapter.apply` and `spaRoutes` (which use `fs2.io.file.Files`)
  * are exercised by the full SSR integration test; only `routes` is tested here.
  */
class Http4sAdapterNodeTest extends CatsEffectSuite:

  val id     = param[Int]("id")
  val postId = param[String]("postId")

  case class Item(id: Int, name: String) derives Codec

  // ── Router.currentPath scoping ─────────────────────────────────────────────

  test("Router.withPath scopes currentPath correctly on Node.js"):
    val result = meltkit.Router.withPath("/users/99") {
      meltkit.Router.currentPath.value
    }
    assertEquals(result, "/users/99")

  // ── static route ──────────────────────────────────────────────────────────

  test("GET /ping returns 200 text/plain"):
    val app = MeltKit[IO, RenderResult]()
    app.get("ping") { ctx => IO.pure(ctx.text("pong")) }

    val req = Request[IO](method = Method.GET, uri = uri"/ping")
    Http4sAdapter
      .routes(app)
      .run(req)
      .value
      .map { resp =>
        assert(resp.isDefined)
        assertEquals(resp.get.status, Status.Ok)
      }

  // ── typed path param ──────────────────────────────────────────────────────

  test("GET /users/42 extracts id as Int"):
    val app = MeltKit[IO, RenderResult]()
    app.get("users" / id) { ctx => IO.pure(ctx.text(s"User ${ ctx.params.id }")) }

    val req = Request[IO](method = Method.GET, uri = uri"/users/42")
    Http4sAdapter
      .routes(app)
      .run(req)
      .value
      .flatMap { resp =>
        assert(resp.isDefined)
        assertEquals(resp.get.status, Status.Ok)
        resp.get.as[String]
      }
      .map(body => assertEquals(body, "User 42"))

  // ── invalid param returns 404 (no match) ─────────────────────────────────

  test("GET /users/abc returns None when id is Int param"):
    val app = MeltKit[IO, RenderResult]()
    app.get("users" / id) { ctx => IO.pure(ctx.text(s"User ${ ctx.params.id }")) }

    val req = Request[IO](method = Method.GET, uri = uri"/users/abc")
    Http4sAdapter
      .routes(app)
      .run(req)
      .value
      .map(resp => assert(resp.isEmpty))

  // ── two typed params ──────────────────────────────────────────────────────

  test("GET /users/1/posts/hello extracts both params"):
    val app = MeltKit[IO, RenderResult]()
    app.get("users" / id / "posts" / postId) { ctx =>
      IO.pure(ctx.text(s"${ ctx.params.id }:${ ctx.params.postId }"))
    }

    val req = Request[IO](method = Method.GET, uri = uri"/users/1/posts/hello")
    Http4sAdapter
      .routes(app)
      .run(req)
      .value
      .flatMap { resp =>
        assert(resp.isDefined)
        resp.get.as[String]
      }
      .map(body => assertEquals(body, "1:hello"))

  // ── sub-router prefix ─────────────────────────────────────────────────────

  test("route() prefix is prepended to sub-router paths"):
    val api = MeltKit[IO, RenderResult]()
    api.get("users" / id) { ctx => IO.pure(ctx.text(s"${ ctx.params.id }")) }

    val app = MeltKit[IO, RenderResult]()
    app.route("api", api)

    val req = Request[IO](method = Method.GET, uri = uri"/api/users/7")
    Http4sAdapter
      .routes(app)
      .run(req)
      .value
      .flatMap { resp =>
        assert(resp.isDefined)
        resp.get.as[String]
      }
      .map(body => assertEquals(body, "7"))

  // ── Endpoint.on — typed endpoints ─────────────────────────────────────────

  test("Endpoint.get with response[Item] returns 200 JSON"):
    val getItem = Endpoint.get("items" / id).response[Item]
    val app     = MeltKit[IO, RenderResult]()
    app.on(getItem) { ctx => IO.pure(ctx.ok(Item(ctx.params.id, "apple"))) }

    val req = Request[IO](method = Method.GET, uri = uri"/items/1")
    Http4sAdapter
      .routes(app)
      .run(req)
      .value
      .flatMap { resp =>
        assert(resp.isDefined)
        assertEquals(resp.get.status, Status.Ok)
        resp.get.as[String]
      }
      .map(body => assertEquals(body, """{"id":1,"name":"apple"}"""))

  test("NodeApp type alias creates MeltKit[IO, RenderResult]"):
    val app: NodeApp[IO] = NodeApp[IO]()
    app.get("hello") { ctx => IO.pure(ctx.text("world")) }

    val req = Request[IO](method = Method.GET, uri = uri"/hello")
    Http4sAdapter
      .routes(app)
      .run(req)
      .value
      .flatMap { resp =>
        assert(resp.isDefined)
        resp.get.as[String]
      }
      .map(body => assertEquals(body, "world"))
