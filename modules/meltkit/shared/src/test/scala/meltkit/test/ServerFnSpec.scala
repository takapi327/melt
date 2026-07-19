/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import melt.runtime.json.SimpleJson

import meltkit.{ PathSpec, ServerFn, ServerFnContract }

class ServerFnSpec extends munit.FunSuite:

  case class PostId(value: Int)
  case class Greeting(msg: String)

  test("command builds an endpoint under the reserved /_melt/fn prefix"):
    val greet = ServerFn.command[PostId, Greeting]("posts.greet")
    assertEquals(greet.name, "posts.greet")
    assertEquals(greet.endpoint.method, "POST")
    assertEquals(greet.endpoint.url(PathSpec.emptyValue), "/_melt/fn/posts.greet")

  test("the endpoint's body decoder round-trips through the In PropsCodec"):
    val greet   = ServerFn.command[PostId, Greeting]("posts.greet")
    val encoded = greet.inCodec.encodeToString(PostId(42))
    assertEquals(encoded, """{"value":42}""")
    assertEquals(greet.endpoint.bodyDecoder.decode(encoded), Right(PostId(42)))

  test("the endpoint's response encoder emits the Out PropsCodec JSON"):
    val greet = ServerFn.command[PostId, Greeting]("posts.greet")
    assertEquals(greet.endpoint.responseEncoder.encode(Greeting("hi")), """{"msg":"hi"}""")

  test("a malformed or type-mismatched body decodes to a client-safe BodyError"):
    val greet = ServerFn.command[PostId, Greeting]("posts.greet")
    assert(greet.endpoint.bodyDecoder.decode("""{"value":"abc"}""").isLeft)
    assert(greet.endpoint.bodyDecoder.decode("not json").isLeft)

  test("Unit input encodes to null and decodes back"):
    val ping = ServerFn.command[Unit, Greeting]("ping")
    assertEquals(ping.inCodec.encodeToString(()), "null")
    assertEquals(ping.endpoint.bodyDecoder.decode("null"), Right(()))
    assertEquals(SimpleJson.parse("null"), SimpleJson.JsonValue.Null)

  test("query builds the same endpoint contract as command and is servable via the base"):
    val list = ServerFn.query[Unit, List[Int]]("posts.list")
    assertEquals(list.name, "posts.list")
    assertEquals(list.endpoint.method, "POST")
    assertEquals(list.endpoint.url(PathSpec.emptyValue), "/_melt/fn/posts.list")
    assertEquals(list.endpoint.responseEncoder.encode(List(1, 2, 3)), "[1,2,3]")
    // both fn kinds unify under the base the server registers against
    val cmd: ServerFnContract[Unit, List[Int]] = list
    val gr:  ServerFnContract[Unit, List[Int]] = ServerFn.command[Unit, List[Int]]("posts.list2")
    assertEquals(cmd.name, "posts.list")
    assertEquals(gr.name, "posts.list2")
