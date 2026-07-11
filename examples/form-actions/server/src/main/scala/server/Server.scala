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
  *   - GET `/`          renders the login form (`form = None`).
  *   - POST `/`         runs the default action: validates the submitted
  *                      `LoginForm`; on failure re-renders with `fail(422, …)`,
  *                      on success redirects (303) to `/dashboard`.
  *   - the same action serves both a native POST (JS off) and a `use:enhance`
  *     fetch (JS on, `x-melt-enhance` header → JSON envelope).
  *
  * {{{ sbt "form-actions-server/run" }}}
  */
object Server extends IOApp.Simple:

  private def buildApp(): MeltKit[IO] =
    val app = MeltKit[IO]()

    app.page("")(
      // annotate `form` so `A` infers as LoginForm (see design §0.10: the API
      // could infer this from `actions` if that parameter came first).
      render  = (_, form: Option[LoginForm]) => LoginPage(LoginPage.Props(form = form)),
      actions = Map("" -> { ctx =>
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
      })
    )

    app.get("dashboard") { ctx =>
      IO.pure(ctx.render(DashboardPage(DashboardPage.Props(email = ""))))
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
