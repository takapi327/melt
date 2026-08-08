/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.http4s.test

import munit.CatsEffectSuite

import cats.effect.IO
import meltkit.*
import meltkit.adapter.http4s.Http4sAdapter
import meltkit.adapter.http4s.Http4sAdapter.given
import org.http4s.*
import org.http4s.implicits.*
import org.typelevel.ci.*

/** CORS wiring on the API-only `Http4sAdapter.routes(app)` path — the path the
  * OIDC server uses. Config comes from `app.cors(...)`. Pure decisions are covered
  * by `meltkit.test.CorsSpec`.
  */
class Http4sAdapterCorsTest extends CatsEffectSuite:

  private def appWith(cfg: CorsConfig): MeltKit[IO] =
    val app = MeltKit[IO]()
    app.cors(cfg)
    app.get("api/x") { ctx => IO.pure(ctx.text("ok")) }
    app

  private val allowlist =
    CorsConfig(allowedOrigins = CorsOrigins.allowlist("https://app.example.com"))

  private def hdr(resp: org.http4s.Response[IO], name: String): Option[String] =
    resp.headers.get(CIString(name)).map(_.head.value)

  private val origin = Header.Raw(ci"Origin", "https://app.example.com")

  // ── preflight ─────────────────────────────────────────────────────────────

  test("preflight OPTIONS with allowed origin → 204 + CORS headers, handler not routed"):
    val req = Request[IO](Method.OPTIONS, uri"/api/x")
      .putHeaders(
        origin,
        Header.Raw(ci"Access-Control-Request-Method", "POST"),
        Header.Raw(ci"Access-Control-Request-Headers", "content-type")
      )
    Http4sAdapter.routes(appWith(allowlist)).run(req).value.map { resp =>
      assert(resp.isDefined)
      val r = resp.get
      assertEquals(r.status.code, 204)
      assertEquals(hdr(r, "Access-Control-Allow-Origin"), Some("https://app.example.com"))
      assert(hdr(r, "Access-Control-Allow-Methods").exists(_.contains("OPTIONS")))
      assertEquals(hdr(r, "Access-Control-Allow-Headers"), Some("content-type"))
      assert(hdr(r, "Access-Control-Max-Age").isDefined)
      assert(hdr(r, "Vary").exists(_.contains("Origin")))
    }

  test("preflight for an unregistered path is still answered (no route needed)"):
    val req = Request[IO](Method.OPTIONS, uri"/does/not/exist")
      .putHeaders(origin, Header.Raw(ci"Access-Control-Request-Method", "POST"))
    Http4sAdapter.routes(appWith(allowlist)).run(req).value.map { resp =>
      assertEquals(resp.map(_.status.code), Some(204))
      assertEquals(resp.flatMap(hdr(_, "Access-Control-Allow-Origin")), Some("https://app.example.com"))
    }

  test("preflight from a disallowed origin → 204 without CORS headers"):
    val req = Request[IO](Method.OPTIONS, uri"/api/x")
      .putHeaders(Header.Raw(ci"Origin", "https://evil.com"), Header.Raw(ci"Access-Control-Request-Method", "POST"))
    Http4sAdapter.routes(appWith(allowlist)).run(req).value.map { resp =>
      assertEquals(resp.map(_.status.code), Some(204))
      assertEquals(resp.flatMap(hdr(_, "Access-Control-Allow-Origin")), None)
    }

  // ── actual request ──────────────────────────────────────────────────────

  test("actual GET with allowed origin → 200 body + ACAO + Vary"):
    val req = Request[IO](Method.GET, uri"/api/x").putHeaders(origin)
    Http4sAdapter
      .routes(appWith(allowlist))
      .run(req)
      .value
      .flatMap { resp =>
        val r = resp.get
        assertEquals(r.status.code, 200)
        assertEquals(hdr(r, "Access-Control-Allow-Origin"), Some("https://app.example.com"))
        assertEquals(hdr(r, "Vary"), Some("Origin"))
        r.as[String]
      }
      .map(body => assertEquals(body, "ok"))

  test("actual GET without Origin → no CORS headers (unchanged behaviour)"):
    val req = Request[IO](Method.GET, uri"/api/x")
    Http4sAdapter.routes(appWith(allowlist)).run(req).value.map { resp =>
      val r = resp.get
      assertEquals(r.status.code, 200)
      assertEquals(hdr(r, "Access-Control-Allow-Origin"), None)
    }

  test("credentials → Access-Control-Allow-Credentials: true on the actual response"):
    val cfg = CorsConfig(allowedOrigins = CorsOrigins.allowlist("https://app.example.com"), allowCredentials = true)
    val req = Request[IO](Method.GET, uri"/api/x").putHeaders(origin)
    Http4sAdapter.routes(appWith(cfg)).run(req).value.map { resp =>
      assertEquals(hdr(resp.get, "Access-Control-Allow-Credentials"), Some("true"))
    }
