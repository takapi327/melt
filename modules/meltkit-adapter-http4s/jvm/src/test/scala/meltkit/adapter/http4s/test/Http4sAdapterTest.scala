/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.http4s.test

import munit.CatsEffectSuite

import melt.runtime.render.RenderResult

import cats.effect.IO
import io.circe.Codec
import meltkit.*
import meltkit.adapter.http4s.CirceBodyDecoder.given
import meltkit.adapter.http4s.CirceBodyEncoder.given
import meltkit.adapter.http4s.Http4sAdapter
import meltkit.adapter.http4s.Http4sAdapter.given
import meltkit.codec.*
import org.http4s.*
import org.http4s.implicits.*

class Http4sAdapterTest extends CatsEffectSuite:

  val id     = param[Int]("id")
  val postId = param[String]("postId")

  case class Item(id: Int, name: String) derives Codec

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

  // ── slash-separated string path ───────────────────────────────────────────

  test("GET /api/todos matches route defined as \"api/todos\""):
    val app = MeltKit[IO, RenderResult]()
    app.get("api/todos") { ctx => IO.pure(ctx.json("[1,2,3]")) }

    val req = Request[IO](method = Method.GET, uri = uri"/api/todos")
    Http4sAdapter
      .routes(app)
      .run(req)
      .value
      .map { resp =>
        assert(resp.isDefined)
        assertEquals(resp.get.status, Status.Ok)
      }

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

  test("Endpoint.get with errorOut returns 200 on Right"):
    val getItem = Endpoint.get("items" / id).errorOut[NotFound].response[Item]
    val app     = MeltKit[IO, RenderResult]()
    app.on(getItem) { ctx => IO.pure(Right(ctx.ok(Item(ctx.params.id, "banana")))) }

    val req = Request[IO](method = Method.GET, uri = uri"/items/2")
    Http4sAdapter
      .routes(app)
      .run(req)
      .value
      .flatMap { resp =>
        assert(resp.isDefined)
        assertEquals(resp.get.status, Status.Ok)
        resp.get.as[String]
      }
      .map(body => assertEquals(body, """{"id":2,"name":"banana"}"""))

  test("Endpoint.get with errorOut returns 404 on Left(NotFound)"):
    val getItem = Endpoint.get("items" / id).errorOut[NotFound].response[Item]
    val app     = MeltKit[IO, RenderResult]()
    app.on(getItem) { ctx => IO.pure(Left(ctx.notFound(s"item ${ ctx.params.id } not found"))) }

    val req = Request[IO](method = Method.GET, uri = uri"/items/99")
    Http4sAdapter
      .routes(app)
      .run(req)
      .value
      .map { resp =>
        assert(resp.isDefined)
        assertEquals(resp.get.status, Status.NotFound)
      }

  test("Endpoint.delete returns 204 No Content"):
    val todoId     = param[String]("todoId")
    val deleteTodo = Endpoint.delete("todos" / todoId)
    val app        = MeltKit[IO, RenderResult]()
    app.on(deleteTodo) { ctx => IO.pure(ctx.noContent) }

    val req = Request[IO](method = Method.DELETE, uri = uri"/todos/abc")
    Http4sAdapter
      .routes(app)
      .run(req)
      .value
      .map { resp =>
        assert(resp.isDefined)
        assertEquals(resp.get.status, Status.NoContent)
      }

  test("Endpoint.url generates correct path"):
    val getItem = Endpoint.get("items" / id).response[Item]
    assertEquals(getItem.url((id = 42)), "/items/42")

  test("Endpoint.url with no params"):
    val getAll = Endpoint.get("items").response[List[Item]]
    assertEquals(getAll.url(EmptyTuple), "/items")
