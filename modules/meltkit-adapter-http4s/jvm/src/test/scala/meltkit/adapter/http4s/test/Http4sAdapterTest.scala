/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.http4s.test

import munit.CatsEffectSuite

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
import org.typelevel.ci.*

class Http4sAdapterTest extends CatsEffectSuite:

  val id     = param[Int]("id")
  val postId = param[String]("postId")

  case class Item(id: Int, name: String) derives Codec

  // ── static route ──────────────────────────────────────────────────────────

  test("GET /ping returns 200 text/plain"):
    val app = MeltKit[IO]()
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
    val app = MeltKit[IO]()
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
    val app = MeltKit[IO]()
    app.get("users" / id) { ctx => IO.pure(ctx.text(s"User ${ ctx.params.id }")) }

    val req = Request[IO](method = Method.GET, uri = uri"/users/abc")
    Http4sAdapter
      .routes(app)
      .run(req)
      .value
      .map(resp => assert(resp.isEmpty))

  // ── two typed params ──────────────────────────────────────────────────────

  test("GET /users/1/posts/hello extracts both params"):
    val app = MeltKit[IO]()
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
    val app = MeltKit[IO]()
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
    val api = MeltKit[IO]()
    api.get("users" / id) { ctx => IO.pure(ctx.text(s"${ ctx.params.id }")) }

    val app = MeltKit[IO]()
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
    val app     = MeltKit[IO]()
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
    val app     = MeltKit[IO]()
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
    val app     = MeltKit[IO]()
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
    val app        = MeltKit[IO]()
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

  // ── Cookie: Set-Cookie in response ────────────────────────────────────────

  test("withCookie adds Set-Cookie header to http4s response"):
    val app = MeltKit[IO]()
    app.get("login") { ctx =>
      IO.pure(ctx.text("ok").withCookie("session_id", "tok", CookieOptions(httpOnly = true)))
    }

    val req = Request[IO](method = Method.GET, uri = uri"/login")
    Http4sAdapter
      .routes(app)
      .run(req)
      .value
      .map { resp =>
        assert(resp.isDefined)
        val cookies = resp.get.headers.get[org.http4s.headers.`Set-Cookie`].map(_.toList).getOrElse(Nil)
        assertEquals(cookies.size, 1)
        assertEquals(cookies.head.cookie.name, "session_id")
        assertEquals(cookies.head.cookie.content, "tok")
        assertEquals(cookies.head.cookie.httpOnly, true)
      }

  test("multiple withCookie calls produce multiple Set-Cookie headers"):
    val app = MeltKit[IO]()
    app.get("multi") { ctx =>
      IO.pure(ctx.text("ok").withCookie("a", "1").withCookie("b", "2"))
    }

    val req = Request[IO](method = Method.GET, uri = uri"/multi")
    Http4sAdapter
      .routes(app)
      .run(req)
      .value
      .map { resp =>
        assert(resp.isDefined)
        val cookies = resp.get.headers.get[org.http4s.headers.`Set-Cookie`].map(_.toList).getOrElse(Nil)
        assertEquals(cookies.size, 2)
        assertEquals(cookies.map(_.cookie.name).toSet, Set("a", "b"))
      }

  test("withDeletedCookie sets Max-Age=0 in Set-Cookie header"):
    val app = MeltKit[IO]()
    app.post("logout") { ctx =>
      IO.pure(ctx.redirect("/login").withDeletedCookie("session_id"))
    }

    val req = Request[IO](method = Method.POST, uri = uri"/logout")
    Http4sAdapter
      .routes(app)
      .run(req)
      .value
      .map { resp =>
        assert(resp.isDefined)
        val cookies = resp.get.headers.get[org.http4s.headers.`Set-Cookie`].map(_.toList).getOrElse(Nil)
        assertEquals(cookies.size, 1)
        assertEquals(cookies.head.cookie.name, "session_id")
        assertEquals(cookies.head.cookie.maxAge, Some(0L))
      }

  test("SameSite=None cookie gets Secure automatically"):
    val app = MeltKit[IO]()
    app.get("secure") { ctx =>
      IO.pure(ctx.text("ok").withCookie("x", "y", CookieOptions(sameSite = "None", secure = false)))
    }

    val req = Request[IO](method = Method.GET, uri = uri"/secure")
    Http4sAdapter
      .routes(app)
      .run(req)
      .value
      .map { resp =>
        assert(resp.isDefined)
        val cookies = resp.get.headers.get[org.http4s.headers.`Set-Cookie`].map(_.toList).getOrElse(Nil)
        assertEquals(cookies.size, 1)
        // http4s automatically adds Secure for SameSite=None per RFC 6265bis
        assert(cookies.head.cookie.renderString.contains("Secure"), cookies.head.cookie.renderString)
      }

  // ── Cookie: reading from request ──────────────────────────────────────────

  test("ctx.cookie reads named cookie from request Cookie header"):
    val app = MeltKit[IO]()
    app.on(Endpoint.get("whoami").response[String]) { ctx =>
      IO.pure(ctx.ok(ctx.cookie("user").getOrElse("anonymous")))
    }

    val req = Request[IO](method = Method.GET, uri = uri"/whoami")
      .withHeaders(org.http4s.headers.Cookie(org.http4s.RequestCookie("user", "alice")))

    Http4sAdapter
      .routes(app)
      .run(req)
      .value
      .flatMap { resp =>
        assert(resp.isDefined)
        resp.get.as[String].map(body => assert(body.contains("alice")))
      }

  test("ctx.cookie returns None when cookie is absent"):
    val app = MeltKit[IO]()
    app.on(Endpoint.get("whoami").response[String]) { ctx =>
      IO.pure(ctx.ok(ctx.cookie("user").getOrElse("anonymous")))
    }

    val req = Request[IO](method = Method.GET, uri = uri"/whoami")

    Http4sAdapter
      .routes(app)
      .run(req)
      .value
      .flatMap { resp =>
        assert(resp.isDefined)
        resp.get.as[String].map(body => assertEquals(body, """"anonymous""""))
      }

  test("ctx.cookies returns all cookies as Map"):
    val app = MeltKit[IO]()
    app.on(Endpoint.get("cookies").response[String]) { ctx =>
      IO.pure(ctx.ok(ctx.cookies.toList.sortBy(_._1).mkString(",")))
    }

    val req = Request[IO](method = Method.GET, uri = uri"/cookies")
      .withHeaders(
        org.http4s.headers.Cookie(
          org.http4s.RequestCookie("a", "1"),
          org.http4s.RequestCookie("b", "2")
        )
      )

    Http4sAdapter
      .routes(app)
      .run(req)
      .value
      .flatMap { resp =>
        assert(resp.isDefined)
        resp.get.as[String].map { body =>
          assert(body.contains("a"))
          assert(body.contains("b"))
        }
      }

  // ── Request header reading ────────────────────────────────────────────────

  test("ctx.header reads named header from request"):
    val app = MeltKit[IO]()
    app.on(Endpoint.get("auth").response[String]) { ctx =>
      IO.pure(ctx.ok(ctx.header("Authorization").getOrElse("none")))
    }

    val req = Request[IO](method = Method.GET, uri = uri"/auth")
      .withHeaders(Header.Raw(ci"Authorization", "Bearer token123"))

    Http4sAdapter
      .routes(app)
      .run(req)
      .value
      .flatMap { resp =>
        assert(resp.isDefined)
        resp.get.as[String].map(body => assert(body.contains("token123")))
      }

  test("ctx.header is case-insensitive"):
    val app = MeltKit[IO]()
    app.on(Endpoint.get("auth2").response[String]) { ctx =>
      val lower = ctx.header("authorization").getOrElse("none")
      val upper = ctx.header("AUTHORIZATION").getOrElse("none")
      IO.pure(ctx.ok(s"$lower:$upper"))
    }

    val req = Request[IO](method = Method.GET, uri = uri"/auth2")
      .withHeaders(Header.Raw(ci"Authorization", "Bearer abc"))

    Http4sAdapter
      .routes(app)
      .run(req)
      .value
      .flatMap { resp =>
        assert(resp.isDefined)
        resp.get.as[String].map(body => assert(body.contains("Bearer abc:Bearer abc")))
      }

  test("ctx.header returns None when header is absent"):
    val app = MeltKit[IO]()
    app.on(Endpoint.get("check").response[String]) { ctx =>
      IO.pure(ctx.ok(ctx.header("X-Missing").getOrElse("none")))
    }

    val req = Request[IO](method = Method.GET, uri = uri"/check")

    Http4sAdapter
      .routes(app)
      .run(req)
      .value
      .flatMap { resp =>
        assert(resp.isDefined)
        resp.get.as[String].map(body => assert(body.contains("none")))
      }

  test("ctx.headers returns all headers as lowercase-keyed Map"):
    val app = MeltKit[IO]()
    app.on(Endpoint.get("hdrs").response[String]) { ctx =>
      IO.pure(ctx.ok(ctx.headers.toList.sortBy(_._1).map(_._1).mkString(",")))
    }

    val req = Request[IO](method = Method.GET, uri = uri"/hdrs")
      .withHeaders(
        Header.Raw(ci"Authorization", "Bearer tok"),
        Header.Raw(ci"X-Request-Id", "req-1")
      )

    Http4sAdapter
      .routes(app)
      .run(req)
      .value
      .flatMap { resp =>
        assert(resp.isDefined)
        resp.get.as[String].map { body =>
          assert(body.contains("authorization"))
          assert(body.contains("x-request-id"))
        }
      }

  test("ctx.header joins duplicate header values with comma"):
    val app = MeltKit[IO]()
    app.on(Endpoint.get("accept").response[String]) { ctx =>
      IO.pure(ctx.ok(ctx.header("accept").getOrElse("none")))
    }

    val req = Request[IO](method = Method.GET, uri = uri"/accept")
      .withHeaders(
        Header.Raw(ci"Accept", "text/html"),
        Header.Raw(ci"Accept", "application/json")
      )

    Http4sAdapter
      .routes(app)
      .run(req)
      .value
      .flatMap { resp =>
        assert(resp.isDefined)
        resp.get.as[String].map { body =>
          assert(body.contains("text/html"))
          assert(body.contains("application/json"))
        }
      }
