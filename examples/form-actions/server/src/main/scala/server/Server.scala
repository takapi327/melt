/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package server

import cats.effect.*
import com.comcast.ip4s.*
import components.*
import generated.AssetManifest
import meltkit.*
import meltkit.adapter.http4s.CirceBodyDecoder.given
import meltkit.adapter.http4s.CirceBodyEncoder.given
import meltkit.adapter.http4s.Http4sAdapter
import meltkit.adapter.http4s.Http4sAdapter.given
import org.http4s.ember.server.EmberServerBuilder

/** Form actions + progressive enhancement example.
  *
  * '''Single default action''' (`/`, one form → `action = ctx => …`):
  *   - GET `/`  renders the login form (`form = None`).
  *   - POST `/` validates the `LoginForm`; failure re-renders with `fail(422, …)`,
  *              success redirects (303) to `/dashboard`.
  *
  * '''Named actions''' (`/posts`, one form + two submit buttons):
  *   - GET  `/posts`          renders the post editor.
  *   - POST `/posts?/save`    ("Save draft" button, `formaction="?/save"`)
  *   - POST `/posts?/publish` ("Publish" button, `formaction="?/publish"`)
  *     Both are dispatched by the `{ case ("save", ctx) => … }` partial function
  *     and share the same `PostForm`; each redirects to `/result/:kind`.
  *
  * Every action serves both a native POST (JS off) and a `use:enhance` fetch (JS
  * on, `x-melt-enhance` header → JSON envelope); the enhance action honours the
  * clicked button's `formaction`, so named actions work with JS on or off.
  *
  * {{{ sbt "form-actions-server/run" }}}
  */
object Server extends IOApp.Simple:

  /** Path parameter for `/result/:kind` (echoes which named action ran). */
  private val kind = param[String]("kind")

  private def buildApp(): MeltKit[IO] =
    val app = MeltKit[IO]()

    // Single default action: one form, so `action = ctx => …` (no named-action
    // dispatch). Annotate `form` so `A` infers as LoginForm (see design §0.10:
    // the API could infer this from `action` if that parameter came first).
    app.page("")(
      render = (_, form: Option[LoginForm]) => LoginPage(LoginPage.Props(form = form)),
      action = ctx =>
        ctx.body.form[LoginForm].map {
          case Right(f) if !f.email.contains("@") =>
            fail(422, f.copy(errors = List("Enter a valid email address")))
          case Right(f) if f.password.length < 6 =>
            fail(422, f.copy(errors = List("Password must be at least 6 characters")))
          case Right(_) =>
            ActionResult.Redirect("/dashboard")
          case Left(err) =>
            fail(400, LoginForm("", "", List(err.message)))
        }
    )

    // Named actions: one form, two submit buttons (`?/save`, `?/publish`). The
    // partial function matches on `(actionName, ctx)`; both cases operate on the
    // same shared `PostForm`.
    app.page("posts")(
      render  = (_, form: Option[PostForm]) => PostEditorPage(PostEditorPage.Props(form = form)),
      actions = {
        case ("save", ctx) =>
          ctx.body.form[PostForm].map {
            case Right(f) if f.title.trim.isEmpty =>
              fail(422, f.copy(errors = List("Title is required")))
            case Right(_) =>
              ActionResult.Redirect("/result/draft")
            case Left(err) =>
              fail(400, PostForm("", "", errors = List(err.message)))
          }
        case ("publish", ctx) =>
          ctx.body.form[PostForm].map {
            case Right(f) if f.title.trim.isEmpty =>
              fail(422, f.copy(errors = List("Title is required")))
            case Right(f) if f.body.trim.length < 10 =>
              fail(422, f.copy(errors = List("Body must be at least 10 characters to publish")))
            case Right(_) =>
              ActionResult.Redirect("/result/published")
            case Left(err) =>
              fail(400, PostForm("", "", errors = List(err.message)))
          }
      }
    )

    app.get("dashboard") { ctx =>
      IO.pure(ctx.render(DashboardPage(DashboardPage.Props(email = ""))))
    }

    app.get("result" / kind) { ctx =>
      IO.pure(ctx.render(PostResultPage(PostResultPage.Props(action = ctx.params.kind))))
    }

    app

  def run: IO[Unit] =
    val app    = buildApp()
    val routes = Http4sAdapter
      .ssrRoutes(
        app,
        fs2.io.file.Path(AssetManifest.clientDistDir),
        AssetManifest.manifest
      )
      .map(_.orNotFound)

    routes.flatMap { httpApp =>
      EmberServerBuilder
        .default[IO]
        .withHost(host"0.0.0.0")
        .withPort(port"3000")
        .withHttpApp(httpApp)
        .build
        .use(_ => IO.println("form-actions server → http://localhost:3000") *> IO.never)
    }
