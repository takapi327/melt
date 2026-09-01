/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.ziohttp.test

import melt.runtime.json.PropsCodec

import meltkit.*
import meltkit.adapter.ziohttp.{ ZioHttpAdapter, ZioInstances }
import zio.*
import zio.http.{ Body, Request, Response as ZResponse, Status, URL }
import zio.test.*
import ZioInstances.given

/** Server Functions over the zio-http adapter.
  *
  * These need no adapter-specific code: `app.serve` registers an ordinary route under
  * `_melt/fn/<name>`, and the wire format is produced by `melt-runtime`'s `PropsCodec`, not by a
  * JSON library the adapter has to supply. This spec pins that down — a regression here would
  * mean the transport has started to matter.
  */
object ServerFnSpec extends ZIOSpecDefault:

  private case class Post(id: Int, title: String, likes: Int) derives PropsCodec

  private type Env    = PostStore
  private type App[A] = ZIO[Env, Throwable, A]

  private trait PostStore:
    def all:           UIO[List[Post]]
    def like(id: Int): UIO[Post]

  private val storeLayer: ULayer[PostStore] =
    ZLayer.succeed(
      new PostStore:
        def all           = ZIO.succeed(List(Post(1, "Hello", 3), Post(2, "Melt", 7)))
        def like(id: Int) = ZIO.succeed(Post(id, "Hello", 4))
    )

  private val list   = ServerFn.query[Unit, List[Post]]("posts.list")
  private val like   = ServerFn.command[Int, Post]("posts.like")
  private val broken = ServerFn.query[Unit, Int]("posts.broken")

  private def app: MeltKit[App] =
    val a = MeltKit[App]()
    a.serve(list) { (_, _) => ZIO.serviceWithZIO[PostStore](_.all) }
    a.serve(like) { (id, _) => ZIO.serviceWithZIO[PostStore](_.like(id)) }
    a.serve(broken) { (_, _) => ZIO.fail(new RuntimeException("store unavailable")) }
    a

  private final case class Out(status: Status, body: String)

  /** Posts `payload` to a server-function endpoint, draining the response inside the scope.
    *
    * `Content-Type: application/json` is required by Melt itself — the server-function handler
    * rejects anything else with a 415, independent of the transport.
    */
  private def call(name: String, payload: String): ZIO[Env, Throwable, Out] =
    val routes  = ZioHttpAdapter.routes(app)
    val request = Request
      .post(URL.decode(s"/_melt/fn/$name").toOption.get, Body.fromString(payload))
      .addHeader("Content-Type", "application/json")
    ZIO.scoped {
      routes.runZIO(request).flatMap(res => res.body.asString.map(Out(res.status, _)))
    }

  def spec = suite("Server Functions over zio-http")(
    test("a query returns its result as PropsCodec JSON") {
      call("posts.list", "null").map { o =>
        assertTrue(
          o.status == Status.Ok,
          o.body == """[{"id":1,"title":"Hello","likes":3},{"id":2,"title":"Melt","likes":7}]"""
        )
      }
    },
    test("a command decodes its input and returns the updated value") {
      call("posts.like", "1").map { o =>
        assertTrue(o.status == Status.Ok, o.body == """{"id":1,"title":"Hello","likes":4}""")
      }
    },
    test("the handler can read its environment") {
      // The whole point of keeping `R`: `ZIO.serviceWithZIO` works inside a server function.
      call("posts.list", "null").map(o => assertTrue(o.body.contains("\"title\":\"Melt\"")))
    },
    test("a failing server function becomes a 500 rather than a lost request") {
      call("posts.broken", "null").map { o =>
        assertTrue(o.status == Status.InternalServerError, o.body.contains("store unavailable"))
      }
    },
    test("an unregistered server function is a 404") {
      call("posts.missing", "null").map(o => assertTrue(o.status == Status.NotFound))
    }
  ).provideLayer(storeLayer)
