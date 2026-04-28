/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import meltkit.*
import meltkit.codec.*

class EndpointTest extends munit.FunSuite:

  given BodyDecoder[String] with
    override def decode(body: String): Either[BodyError, String] = Right(body)

  val id     = param[Int]("id")
  val postId = param[String]("postId")

  // ── HTTP method ───────────────────────────────────────────────────────────

  test("Endpoint.get sets method to GET"):
    assertEquals(Endpoint.get("ping").method, ("GET": HttpMethod))

  test("Endpoint.post sets method to POST"):
    assertEquals(Endpoint.post("users").method, ("POST": HttpMethod))

  test("Endpoint.put sets method to PUT"):
    assertEquals(Endpoint.put("users" / id).method, ("PUT": HttpMethod))

  test("Endpoint.delete sets method to DELETE"):
    assertEquals(Endpoint.delete("users" / id).method, ("DELETE": HttpMethod))

  test("Endpoint.patch sets method to PATCH"):
    assertEquals(Endpoint.patch("users" / id).method, ("PATCH": HttpMethod))

  // ── Default status code ───────────────────────────────────────────────────

  test("Endpoint default statusCode is 200"):
    assertEquals(Endpoint.get("ping").statusCode, (200: StatusCode))

  test("Endpoint.status changes statusCode"):
    assertEquals(Endpoint.post("users").status(201).statusCode, (201: StatusCode))

  test("Endpoint.delete with status 204"):
    assertEquals(Endpoint.delete("users" / id).status(204).statusCode, (204: StatusCode))

  // ── url generation ────────────────────────────────────────────────────────

  test("url with no params"):
    val ep = Endpoint.get("api/todos").response[String]
    assertEquals(ep.url(EmptyTuple), "/api/todos")

  test("url with single Int param"):
    val ep = Endpoint.get("users" / id).response[String]
    assertEquals(ep.url((id = 42)), "/users/42")

  test("url with two params"):
    val ep = Endpoint.get("users" / id / "posts" / postId).response[String]
    assertEquals(ep.url((id = 1, postId = "hello")), "/users/1/posts/hello")

  test("url with Long param"):
    val longId = param[Long]("longId")
    val ep     = Endpoint.get("items" / longId).response[String]
    assertEquals(ep.url((longId = 123456789L)), "/items/123456789")

  // ── builder methods preserve type params ──────────────────────────────────

  test("status preserves other type params"):
    val ep = Endpoint.post("todos").body[String].status(201).response[String]
    assertEquals(ep.statusCode, (201: StatusCode))
    assertEquals(ep.method, ("POST": HttpMethod))

  test("errorOut changes E type param"):
    val ep = Endpoint.get("users" / id).errorOut[NotFound].response[String]
    assertEquals(ep.method, ("GET": HttpMethod))

  // ── segments ──────────────────────────────────────────────────────────────

  test("Endpoint stores correct segments for plain path"):
    val ep = Endpoint.get("api/todos")
    assertEquals(ep.spec.segments, List(PathSegment.Static("api"), PathSegment.Static("todos")))

  test("Endpoint stores correct segments for parameterised path"):
    val ep = Endpoint.get("users" / id)
    assertEquals(ep.spec.segments, List(PathSegment.Static("users"), PathSegment.Param("id")))
