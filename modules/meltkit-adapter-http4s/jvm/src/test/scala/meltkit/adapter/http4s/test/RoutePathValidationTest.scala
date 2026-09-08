/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.http4s.test

import melt.runtime.render.RenderResult

import cats.effect.IO
import meltkit.*
import meltkit.adapter.http4s.Http4sAdapter.given

/** Placeholder syntax borrowed from other routers in a path string.
  *
  * `app.get("users/:id")` reads as a parameter but registers a literal segment named `:id`,
  * so the route never matches a real request. Nothing failed: it compiled, it started, and
  * every call to it answered 404. Melt spells parameters with `param[T]("name")`, so a path
  * string carrying `:id`, `[id]` or `{id}` is a mistake worth naming at registration.
  */
class RoutePathValidationTest extends munit.FunSuite:

  private def app = MeltKit[IO]()

  private def rejected(f: => Unit): String =
    intercept[IllegalArgumentException](f).getMessage

  test("a colon placeholder in a path string is rejected"):
    val msg = rejected(app.get("users/:id")(ctx => IO.pure(ctx.text("x"))))
    assert(msg.contains(":id"), msg)
    assert(msg.contains("param"), msg)

  test("a bracket placeholder in a path string is rejected"):
    assert(rejected(app.get("users/[id]")(ctx => IO.pure(ctx.text("x")))).contains("[id]"))

  test("a brace placeholder in a path string is rejected"):
    assert(rejected(app.get("users/{id}")(ctx => IO.pure(ctx.text("x")))).contains("{id}"))

  test("a placeholder is rejected on the mutation verbs too"):
    rejected(app.post("users/:id")(ctx => IO.pure(ctx.text("x"))))
    rejected(app.put("users/:id")(ctx => IO.pure(ctx.text("x"))))
    rejected(app.patch("users/:id")(ctx => IO.pure(ctx.text("x"))))
    rejected(app.delete("users/:id")(ctx => IO.pure(ctx.text("x"))))

  test("a placeholder in a mount prefix is rejected"):
    assert(rejected(app.route("tenants/:id", MeltKit[IO]())).contains(":id"))

  test("a placeholder in a layout prefix is rejected"):
    assert(rejected(app.layout(":tenant")(c => c())).contains(":tenant"))

  test("a placeholder in a page path is rejected"):
    assert(
      rejected(
        app.page("posts/:id")(
          render = (_, _: Option[String]) => RenderResult(body = "", head = ""),
          action = ctx => IO.pure(ActionResult.Redirect("/"))
        )
      ).contains(":id")
    )

  test("ordinary static paths are unaffected"):
    val a = MeltKit[IO]()
    a.get("api/todos")(ctx => IO.pure(ctx.text("ok")))
    a.route("api", MeltKit[IO]())
    a.layout("dashboard")(c => c())
    assert(a.routes.nonEmpty)

  test("the typed parameter DSL is unaffected"):
    val a  = MeltKit[IO]()
    val id = param[Int]("id")
    a.get("users" / id)(ctx => IO.pure(ctx.text(s"${ ctx.params.id }")))
    assert(a.routes.nonEmpty)

  test("a server function name containing punctuation is unaffected"):
    val a  = MeltKit[IO]()
    val fn = ServerFn.command[Int, Int]("posts.like")
    a.serve(fn)((in, _) => IO.pure(in + 1))
    assert(a.serverFnNames.contains("posts.like"))

  test("a placeholder embedded in a longer segment is rejected"):
    assert(rejected(app.get("users/user-:id")(ctx => IO.pure(ctx.text("x")))).contains("user-:id"))

  test("an unbalanced bracket is rejected"):
    assert(rejected(app.get("users/[id")(ctx => IO.pure(ctx.text("x")))).contains("[id"))

  test("an unbalanced brace is rejected"):
    assert(rejected(app.get("users/{id")(ctx => IO.pure(ctx.text("x")))).contains("{id"))

  test("a wildcard segment is rejected"):
    assert(rejected(app.get("files/*")(ctx => IO.pure(ctx.text("x")))).contains("*"))

  test("a placeholder in a page path with PageOptions is rejected"):
    assert(
      rejected(app.get("posts/:id", PageOptions(prerender = PrerenderOption.On))(ctx => IO.pure(ctx.text("x"))))
        .contains(":id")
    )

  test("a placeholder in a page path with named actions is rejected"):
    assert(
      rejected(
        app.page("posts/:id")(
          render  = (_, _: Option[String]) => RenderResult(body = "", head = ""),
          actions = { case (_, _) => IO.pure(ActionResult.Redirect("/")) }
        )
      ).contains(":id")
    )
