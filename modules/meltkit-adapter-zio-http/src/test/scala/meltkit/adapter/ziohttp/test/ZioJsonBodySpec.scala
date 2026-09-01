/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.ziohttp.test

import zio.*
import zio.http.{ Body, Request, Response as ZResponse, Status, URL }
import zio.json.{ DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder }
import zio.test.*

import meltkit.*
import meltkit.adapter.ziohttp.ZioHttpAdapter
import meltkit.adapter.ziohttp.ZioInstances.given
import meltkit.adapter.ziohttp.ZioJsonBodyDecoder.given
import meltkit.adapter.ziohttp.ZioJsonBodyEncoder.given

/** The zio-json bridges, exercised through a hand-written endpoint.
  *
  * These are the only place a JSON library is needed: Server Functions go through `PropsCodec`
  * and form actions through `FormDataDecoder`, so an app that uses neither `ctx.body[T]` nor
  * `ctx.ok(value)` never pulls them in.
  */
object ZioJsonBodySpec extends ZIOSpecDefault:

  private case class CreateUser(name: String, age: Int)
  private object CreateUser:
    given JsonDecoder[CreateUser] = DeriveJsonDecoder.gen[CreateUser]

  private case class User(id: Int, name: String)
  private object User:
    given JsonEncoder[User] = DeriveJsonEncoder.gen[User]

  private type App[A] = ZIO[Any, Throwable, A]

  private def app: MeltKit[App] =
    val a = MeltKit[App]()
    a.post("users") { ctx =>
      ctx.body.json[CreateUser].map {
        case Right(u) => ctx.created(User(1, u.name))
        case Left(e)  => ctx.badRequest(e)
      }
    }
    a

  private final case class Out(status: Status, body: String)

  private def post(payload: String): ZIO[Any, Throwable, Out] =
    val routes  = ZioHttpAdapter.routes(app)
    val request = Request.post(URL.decode("/users").toOption.get, Body.fromString(payload))
    ZIO.scoped {
      routes.runZIO(request).flatMap(res => res.body.asString.map(Out(res.status, _)))
    }

  def spec = suite("zio-json body bridges")(
    test("a valid body decodes and the result encodes back to JSON") {
      post("""{"name":"Alice","age":30}""").map { o =>
        assertTrue(o.status == Status.Created, o.body == """{"id":1,"name":"Alice"}""")
      }
    },
    test("malformed JSON is a 400, not a 500") {
      post("{ not json").map(o => assertTrue(o.status == Status.BadRequest))
    },
    test("a missing field is a 400") {
      post("""{"name":"Alice"}""").map(o => assertTrue(o.status == Status.BadRequest))
    },
    test("the client-facing message stays generic while the library error goes to detail") {
      // zio-json's message names the failing path (`.age(missing)`), which belongs in a log
      // rather than in a response body.
      val decoder = summon[meltkit.codec.BodyDecoder[CreateUser]]
      val result  = decoder.decode("""{"name":"Alice"}""")
      ZIO.succeed(
        assertTrue(
          result.left.toOption.exists {
            case BodyError.DecodeError(msg, detail) => msg == "Invalid request body" && detail.exists(_.contains("age"))
            case _                                  => false
          }
        )
      )
    }
  )
