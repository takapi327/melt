/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import scala.NamedTuple.AnyNamedTuple
import scala.NamedTuple.NamedTuple as NT

import meltkit.*

class PathSpecTest extends munit.FunSuite:

  val id     = param[Int]("id")
  val userId = param[Int]("userId")
  val postId = param[String]("postId")

  // ── PathParam ──────────────────────────────────────────────────────────────

  test("param preserves name at runtime"):
    assertEquals(id.paramName, "id")
    assertEquals(userId.paramName, "userId")
    assertEquals(postId.paramName, "postId")

  // ── PathSpec segment construction ─────────────────────────────────────────

  test("plain string produces one Static segment"):
    val spec = PathSpec.fromString("users")
    assertEquals(spec.segments, List(PathSegment.Static("users")))

  test("empty string produces no segments"):
    val spec = PathSpec.fromString("")
    assertEquals(spec.segments, Nil)

  test("string / param produces Static then Param segments"):
    val spec: PathSpec[NT["id" *: EmptyTuple, Int *: EmptyTuple]] = "users" / id
    assertEquals(spec.segments, List(PathSegment.Static("users"), PathSegment.Param("id")))

  test("string / param / static appends Static segment"):
    val spec: PathSpec[NT["userId" *: EmptyTuple, Int *: EmptyTuple]] =
      "users" / userId / "posts"
    assertEquals(
      spec.segments,
      List(
        PathSegment.Static("users"),
        PathSegment.Param("userId"),
        PathSegment.Static("posts")
      )
    )

  // ── NamedTuple.Concat type accumulation ───────────────────────────────────

  test("two params accumulate via NamedTuple.Concat"):
    // PathSpec[(userId: Int, postId: String)] after Concat
    val spec: PathSpec[NT["userId" *: "postId" *: EmptyTuple, Int *: String *: EmptyTuple]] =
      "users" / userId / "posts" / postId
    assertEquals(
      spec.segments,
      List(
        PathSegment.Static("users"),
        PathSegment.Param("userId"),
        PathSegment.Static("posts"),
        PathSegment.Param("postId")
      )
    )

  // ── ctx.params named field access ─────────────────────────────────────────

  test("ctx.params.id returns typed value"):
    type P = NT["id" *: EmptyTuple, Int *: EmptyTuple]
    val ctx = TestMeltContext[P]((id = 42))
    assertEquals(ctx.params.id, 42)

  test("ctx.params named access for multiple params"):
    type P = NT["userId" *: "postId" *: EmptyTuple, Int *: String *: EmptyTuple]
    val ctx = TestMeltContext[P]((userId = 1, postId = "hello"))
    assertEquals(ctx.params.userId, 1)
    assertEquals(ctx.params.postId, "hello")

  // ── MeltKit route registration ────────────────────────────────────────────

  test("MeltKit registers route with correct method and segments"):
    type Id = [A] =>> A
    val app = MeltKit[Id]()
    app.get("users" / id) { ctx => ctx.text(s"User ${ ctx.params.id }") }
    val routes = app.routes
    assertEquals(routes.size, 1)
    assertEquals(routes.head.method, "GET")
    assertEquals(
      routes.head.segments,
      List(PathSegment.Static("users"), PathSegment.Param("id"))
    )

  test("MeltKit route() mounts sub-router with prefix"):
    type Id = [A] =>> A
    val api = MeltKit[Id]()
    api.get("users" / id) { ctx => ctx.text("ok") }

    val app = MeltKit[Id]()
    app.route("api", api)

    val routes = app.routes
    assertEquals(routes.size, 1)
    assertEquals(
      routes.head.segments,
      List(PathSegment.Static("api"), PathSegment.Static("users"), PathSegment.Param("id"))
    )

// ── Minimal MeltContext stub for tests ────────────────────────────────────────

private class TestMeltContext[P <: AnyNamedTuple](val params: P) extends MeltContext[[A] =>> A, P]:
  def query(name:       String):                     Option[String] = None
  def text(value:       String):                     Response       = Response.text(value)
  def redirect(path:    String, permanent: Boolean): Response       = Response.redirect(path, permanent)
  def notFound(message: String):                     Response       = Response.notFound(message)
