/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.http4s.test

import munit.CatsEffectSuite

import cats.effect.IO
import meltkit.*
import meltkit.adapter.http4s.Http4sAdapter
import meltkit.adapter.http4s.Http4sAdapter.given
import org.http4s.*
import org.http4s.implicits.*
import org.typelevel.ci.*

/** End-to-end dispatch of a [[ServerFn]] `command` through the http4s adapter:
  * the JSON body is decoded with the `In` PropsCodec, `impl` runs, and the `Out`
  * is encoded back — no circe codec needed for the function's types.
  */
class ServerFnDispatchTest extends CatsEffectSuite:

  case class PostId(value: Int)
  case class Greeting(msg: String)

  val greet = ServerFn.command[PostId, Greeting]("posts.greet")

  test("POST /_melt/fn/posts.greet decodes In, runs impl, encodes Out"):
    val app = MeltKit[IO]()
    app.serve(greet) { (in, _) => IO.pure(Greeting(s"hi ${ in.value }")) }

    val req = Request[IO](method = Method.POST, uri = uri"/_melt/fn/posts.greet").withEntity("""{"value":7}""")
    Http4sAdapter
      .routes(app)
      .run(req)
      .value
      .flatMap { resp =>
        assert(resp.isDefined)
        assertEquals(resp.get.status, Status.Ok)
        resp.get.as[String]
      }
      .map(body => assertEquals(body, """{"msg":"hi 7"}"""))

  test("a body that fails to decode yields 400 without invoking impl"):
    var invoked = false
    val app     = MeltKit[IO]()
    app.serve(greet) { (in, _) =>
      invoked = true
      IO.pure(Greeting(s"hi ${ in.value }"))
    }

    val req = Request[IO](method = Method.POST, uri = uri"/_melt/fn/posts.greet").withEntity("""{"value":"nope"}""")
    Http4sAdapter
      .routes(app)
      .run(req)
      .value
      .map { resp =>
        assert(resp.isDefined)
        assertEquals(resp.get.status, Status.BadRequest)
        assert(!invoked, "impl must not run when the body is invalid")
      }

  test("the server function is reachable only via its exact reserved path"):
    val app = MeltKit[IO]()
    app.serve(greet) { (in, _) => IO.pure(Greeting(s"hi ${ in.value }")) }

    val req = Request[IO](method = Method.POST, uri = uri"/_melt/fn/posts.other").withEntity("""{"value":1}""")
    Http4sAdapter.routes(app).run(req).value.map(resp => assert(resp.isEmpty))

  // ── single-flight: mutation re-runs requested queries in one round-trip ─────

  test("single-flight: a mutation runs, then re-runs a requested query and piggybacks its value"):
    val list  = ServerFn.query[Unit, List[Int]]("posts.list")
    val like  = ServerFn.command[Int, Int]("posts.like")
    var store = List(1, 2)
    val app   = MeltKit[IO]()
    app.serve(list) { (_, _) => IO(store) }
    app.serve(like) { (n, _) => IO { store = store :+ n; store.size } }

    // envelope: mutate with input 3, refresh the posts.list query (Unit args = "null")
    val body = """{"input":3,"refresh":[{"name":"posts.list","args":"null"}]}"""
    val req  = Request[IO](method = Method.POST, uri = uri"/_melt/fn/posts.like")
      .withEntity(body)
      .putHeaders(Header.Raw(ci"X-Melt-Sf", "1"))
    Http4sAdapter
      .routes(app)
      .run(req)
      .value
      .flatMap { resp =>
        assert(resp.isDefined)
        assertEquals(resp.get.status, Status.Ok)
        resp.get.as[String]
      }
      .map { text =>
        // mutation ran first (size 3), then the refreshed list is piggybacked
        assert(text.contains("\"result\":3"), text)
        assert(text.contains("\"name\":\"posts.list\""), text)
        assert(text.contains("\"args\":\"null\""), text)
        assert(text.contains("\"value\":[1,2,3]"), text)
      }

  test("single-flight with an empty refresh list returns just the result"):
    val like = ServerFn.command[Int, Int]("posts.like")
    val app  = MeltKit[IO]()
    app.serve(like) { (n, _) => IO.pure(n + 100) }

    val req = Request[IO](method = Method.POST, uri = uri"/_melt/fn/posts.like")
      .withEntity("""{"input":5,"refresh":[]}""")
      .putHeaders(Header.Raw(ci"X-Melt-Sf", "1"))
    Http4sAdapter.routes(app).run(req).value.flatMap(_.get.as[String]).map { text =>
      assertEquals(text, """{"result":105,"updates":[]}""")
    }
