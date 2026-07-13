/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.http4s.test

import munit.CatsEffectSuite

import melt.runtime.json.PropsCodec

import cats.effect.IO
import meltkit.*
import meltkit.adapter.http4s.FormProbe
import meltkit.adapter.http4s.Http4sAdapter.given
import meltkit.codec.FormDataDecoder

class FormProbeTest extends CatsEffectSuite:

  case class LoginForm(email: String, password: String, errors: List[String] = Nil) derives FormDataDecoder, PropsCodec

  /** A login page whose action validates the email; `render` throws so the tests
    * assert it is never reached on the redirect/enhance paths they exercise.
    */
  private def loginApp: MeltKit[IO] =
    val app = MeltKit[IO]()
    app.page("login")(
      render = (_, _: Option[LoginForm]) => throw new AssertionError("render should not be called"),
      action = ctx =>
        ctx.body.form[LoginForm].map {
          case Right(f) if f.email.contains("@") => ActionResult.Redirect("/dashboard")
          case Right(f)                          => meltkit.fail(422, f.copy(errors = List("invalid email")))
          case Left(e)                           => meltkit.fail(400, LoginForm("", "", List(e.message)))
        }
    )
    app

  test("probe.submit runs the action and returns the native redirect"):
    FormProbe(loginApp)
      .submit("login", fields = Map("email" -> "a@b.com", "password" -> "secret"))
      .map { r =>
        assertEquals(r.status, 303)
        assertEquals(r.location, Some("/dashboard"))
      }

  test("probe.submit(enhance = true) returns the JSON failure envelope"):
    FormProbe(loginApp)
      .submit("login", fields = Map("email" -> "bad", "password" -> "secret"), enhance = true)
      .map { r =>
        assertEquals(r.status, 200)
        assert(r.contains(""""type":"failure""""), r.body)
        assert(r.contains("invalid email"), r.body)
      }

  test("probe drives named actions via the ?/action query"):
    val app = MeltKit[IO]()
    app.page("posts")(
      render  = (_, _: Option[LoginForm]) => throw new AssertionError("render should not be called"),
      actions = {
        case ("save", _)    => IO.pure(ActionResult.Redirect("/result/draft"))
        case ("publish", _) => IO.pure(ActionResult.Redirect("/result/published"))
      }
    )
    FormProbe(app).submit("posts", action = "publish").map { r =>
      assertEquals(r.status, 303)
      assertEquals(r.location, Some("/result/published"))
    }

  test("probe respects the CSRF hook: origin required, mismatch rejected"):
    val app = MeltKit[IO]()
    app.use(ServerHook.csrf[IO]())
    app.page("login")(
      render = (_, _: Option[LoginForm]) => throw new AssertionError("render should not be called"),
      action = _ => IO.pure(ActionResult.Redirect("/dashboard"))
    )
    val probe = FormProbe(app)
    for
      noOrigin <- probe.submit("login", fields = Map("email" -> "a@b.com"))
      matching <- probe.submit("login", fields = Map("email" -> "a@b.com"), origin = Some("http://localhost:3000"))
      // cross-site: Origin from the attacker, Host still the real server
      evil <- probe.submit(
                "login",
                fields = Map("email" -> "a@b.com"),
                origin = Some("https://evil.example"),
                host   = Some("localhost:3000")
              )
    yield
      assertEquals(noOrigin.status, 403) // missing Origin
      assertEquals(matching.status, 303) // same-origin → action runs
      assertEquals(evil.status, 403)     // Origin ≠ server → rejected
