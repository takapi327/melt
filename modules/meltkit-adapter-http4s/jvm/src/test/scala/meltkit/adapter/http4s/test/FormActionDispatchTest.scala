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
import meltkit.adapter.http4s.Http4sAdapter
import meltkit.adapter.http4s.Http4sAdapter.given
import org.http4s.{ Header, Headers, Method, Status, Uri }
import org.http4s.Request as HttpRequest
import org.http4s.Response as HttpResponse
import org.typelevel.ci.*

/** Regression coverage for `app.page(...)` form-action dispatch.
  *
  * Exercises the full `?/name` → `actionKey` → partial-function dispatch path
  * through real http4s query parsing. This guards the "pressing a named-action
  * button does nothing" class of bug: if the `?/name` convention stops mapping to
  * the right action, these tests fail instead of the app silently 400-ing.
  */
class FormActionDispatchTest extends CatsEffectSuite:

  case class ActionForm(value: String = "") derives PropsCodec

  private val enhance = Header.Raw(ci"x-melt-enhance", "true")

  /** A page with two named actions sharing one `ActionForm`. `render` throws so
    * the tests also assert it is never reached on the action paths.
    */
  private def postsApp: MeltKit[IO] =
    val app = MeltKit[IO]()
    app.page("posts")(
      render  = (_, _: Option[ActionForm]) => throw new AssertionError("render should not be called"),
      actions = {
        case ("save", _)    => IO.pure(ActionResult.Redirect("/saved"))
        case ("publish", _) => IO.pure(ActionResult.Success(ActionForm("published")))
      }
    )
    app

  private def post(uri: String, headers: Header.Raw*): HttpRequest[IO] =
    HttpRequest[IO](method = Method.POST, uri = Uri.unsafeFromString(uri)).withHeaders(Headers(headers.toList))

  private def locationOf(resp: HttpResponse[IO]): Option[String] =
    resp.headers.get(ci"Location").map(_.head.value)

  // ── named-action dispatch ───────────────────────────────────────────────────

  test("?/publish dispatches to the publish case (enhance → success JSON)"):
    Http4sAdapter
      .routes(postsApp)
      .run(post("/posts?/publish", enhance))
      .value
      .flatMap { resp =>
        assert(resp.isDefined)
        assertEquals(resp.get.status, Status.Ok)
        resp.get.as[String]
      }
      .map { body =>
        assert(body.contains(""""type":"success""""), body)
        assert(body.contains("published"), body)
      }

  test("?/save dispatches to the save case (enhance → redirect JSON)"):
    Http4sAdapter
      .routes(postsApp)
      .run(post("/posts?/save", enhance))
      .value
      .flatMap { resp =>
        assert(resp.isDefined)
        resp.get.as[String]
      }
      .map { body =>
        assert(body.contains(""""type":"redirect""""), body)
        assert(body.contains("/saved"), body)
      }

  // ── native (JS-off) path ────────────────────────────────────────────────────

  test("native ?/save (no enhance header) redirects 303 with Location"):
    Http4sAdapter
      .routes(postsApp)
      .run(post("/posts?/save"))
      .value
      .map { resp =>
        assert(resp.isDefined)
        assertEquals(resp.get.status, Status.SeeOther)
        assertEquals(locationOf(resp.get), Some("/saved"))
      }

  // ── unmatched / missing action name → 400 ───────────────────────────────────

  test("unknown action name responds 400"):
    Http4sAdapter
      .routes(postsApp)
      .run(post("/posts?/nope", enhance))
      .value
      .map { resp =>
        assert(resp.isDefined)
        assertEquals(resp.get.status, Status.BadRequest)
      }

  test("POST with no ?/name does not match named actions → 400"):
    Http4sAdapter
      .routes(postsApp)
      .run(post("/posts"))
      .value
      .map { resp =>
        assert(resp.isDefined)
        assertEquals(resp.get.status, Status.BadRequest)
      }

  // ── single default action ────────────────────────────────────────────────────

  test("single default action runs regardless of any ?/name"):
    val app = MeltKit[IO]()
    app.page("single")(
      render = (_, _: Option[ActionForm]) => throw new AssertionError("render should not be called"),
      action = _ => IO.pure(ActionResult.Redirect("/done"))
    )
    Http4sAdapter
      .routes(app)
      .run(post("/single?/whatever"))
      .value
      .map { resp =>
        assert(resp.isDefined)
        assertEquals(resp.get.status, Status.SeeOther)
        assertEquals(locationOf(resp.get), Some("/done"))
      }
