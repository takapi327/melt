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
import org.http4s.headers.`Content-Type`
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

  /** A POST with `Content-Type: application/json` (required by the dispatcher). */
  private def jsonPost(path: Uri, body: String, headers: Header.Raw*): org.http4s.Request[IO] =
    val base = org.http4s
      .Request[IO](Method.POST, path)
      .withEntity(body)
      .withContentType(`Content-Type`(MediaType.application.json))
    headers.foldLeft(base)((r, h) => r.putHeaders(h))

  private def run(app: MeltKit[IO], req: org.http4s.Request[IO]) =
    Http4sAdapter.routes(app).run(req).value

  test("POST /_melt/fn/posts.greet decodes In, runs impl, encodes Out"):
    val app = MeltKit[IO]()
    app.serve(greet) { (in, _) => IO.pure(Greeting(s"hi ${ in.value }")) }

    run(app, jsonPost(uri"/_melt/fn/posts.greet", """{"value":7}"""))
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

    run(app, jsonPost(uri"/_melt/fn/posts.greet", """{"value":"nope"}"""))
      .map { resp =>
        assert(resp.isDefined)
        assertEquals(resp.get.status, Status.BadRequest)
        assert(!invoked, "impl must not run when the body is invalid")
      }

  test("a request without Content-Type application/json is rejected with 415"):
    val app = MeltKit[IO]()
    app.serve(greet) { (in, _) => IO.pure(Greeting(s"hi ${ in.value }")) }

    // text/plain simple request (the cross-site CSRF vector) — must be blocked.
    val req = org.http4s.Request[IO](Method.POST, uri"/_melt/fn/posts.greet").withEntity("""{"value":7}""")
    run(app, req).map { resp =>
      assert(resp.isDefined)
      assertEquals(resp.get.status, Status.UnsupportedMediaType)
    }

  test("the server function is reachable only via its exact reserved path"):
    val app = MeltKit[IO]()
    app.serve(greet) { (in, _) => IO.pure(Greeting(s"hi ${ in.value }")) }

    run(app, jsonPost(uri"/_melt/fn/posts.other", """{"value":1}""")).map(resp => assert(resp.isEmpty))

  test("registering two server functions with the same name fails fast"):
    val a   = ServerFn.command[Int, Int]("dup.name")
    val b   = ServerFn.query[Int, Int]("dup.name")
    val app = MeltKit[IO]()
    app.serve(a) { (n, _) => IO.pure(n) }
    intercept[IllegalArgumentException] {
      app.serve(b) { (n, _) => IO.pure(n) }
    }

  // ── single-flight: mutation re-runs requested queries in one round-trip ─────

  test("single-flight: a mutation runs, then re-runs a requested query and piggybacks its value"):
    val list  = ServerFn.query[Unit, List[Int]]("posts.list")
    val like  = ServerFn.command[Int, Int]("posts.like")
    var store = List(1, 2)
    val app   = MeltKit[IO]()
    app.serve(list) { (_, _) => IO(store) }
    app.serve(like) { (n, _) => IO { store = store :+ n; store.size } }

    val body = """{"input":3,"refresh":[{"name":"posts.list","args":"null"}]}"""
    run(app, jsonPost(uri"/_melt/fn/posts.like", body, Header.Raw(ci"X-Melt-Sf", "1")))
      .flatMap { resp =>
        assert(resp.isDefined)
        assertEquals(resp.get.status, Status.Ok)
        resp.get.as[String]
      }
      .map { text =>
        assert(text.contains("\"result\":3"), text)
        assert(text.contains("\"name\":\"posts.list\""), text)
        assert(text.contains("\"args\":\"null\""), text)
        assert(text.contains("\"value\":[1,2,3]"), text)
      }

  test("single-flight with an empty refresh list returns just the result"):
    val like = ServerFn.command[Int, Int]("posts.like")
    val app  = MeltKit[IO]()
    app.serve(like) { (n, _) => IO.pure(n + 100) }

    run(app, jsonPost(uri"/_melt/fn/posts.like", """{"input":5,"refresh":[]}""", Header.Raw(ci"X-Melt-Sf", "1")))
      .flatMap(_.get.as[String])
      .map(text => assertEquals(text, """{"result":105,"updates":[]}"""))

  test("single-flight refresh CANNOT invoke a command (only queries are refreshable)"):
    var dangerRan = false
    val danger    = ServerFn.command[Int, Int]("posts.danger")
    val like      = ServerFn.command[Int, Int]("posts.like")
    val app       = MeltKit[IO]()
    app.serve(danger) { (_, _) => IO { dangerRan = true; 0 } }
    app.serve(like) { (n, _) => IO.pure(n) }

    // A malicious refresh naming the command must be ignored — not executed.
    val body = """{"input":1,"refresh":[{"name":"posts.danger","args":"9"}]}"""
    run(app, jsonPost(uri"/_melt/fn/posts.like", body, Header.Raw(ci"X-Melt-Sf", "1")))
      .flatMap(_.get.as[String])
      .map { text =>
        assert(!dangerRan, "a command must never run via the single-flight refresh path")
        assertEquals(text, """{"result":1,"updates":[]}""")
      }

  test("single-flight isolates a failing refresh query — mutation result is still returned"):
    val boom = ServerFn.query[Unit, List[Int]]("posts.boom")
    val like = ServerFn.command[Int, Int]("posts.like")
    val app  = MeltKit[IO]()
    app.serve(boom) { (_, _) => IO.raiseError(new RuntimeException("db down")) }
    app.serve(like) { (n, _) => IO.pure(n + 100) }

    val body = """{"input":5,"refresh":[{"name":"posts.boom","args":"null"}]}"""
    run(app, jsonPost(uri"/_melt/fn/posts.like", body, Header.Raw(ci"X-Melt-Sf", "1")))
      .flatMap { resp =>
        assertEquals(resp.get.status, Status.Ok) // NOT 500
        resp.get.as[String]
      }
      .map(text => assertEquals(text, """{"result":105,"updates":[]}""")) // failed refresh skipped
