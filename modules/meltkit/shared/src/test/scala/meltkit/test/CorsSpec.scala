/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import scala.concurrent.duration.*

import meltkit.*

/** Pure CORS logic: origin allow decision, preflight detection, and the two
  * header sets (preflight 204 / actual response). Adapter wiring is tested
  * separately in `Http4sAdapterCorsTest`.
  */
class CorsSpec extends munit.FunSuite:

  /** Minimal request view for tests: case-insensitive header lookup. */
  private def req(m: String, hs: (String, String)*): CorsRequestView =
    new CorsRequestView:
      def method: String = m
      def header(name: String): Option[String] =
        hs.find(_._1.equalsIgnoreCase(name)).map(_._2)

  private val allowlist =
    CorsConfig(allowedOrigins = CorsOrigins.allowlist("https://app.example.com"))

  // ── origin decision ─────────────────────────────────────────────────────

  test("allowOrigin: Any yields wildcard"):
    assertEquals(Cors.allowOrigin(CorsConfig.permissiveDev, "https://x.com"), Some("*"))

  test("allowOrigin: allowlist reflects a member, rejects a non-member"):
    assertEquals(Cors.allowOrigin(allowlist, "https://app.example.com"), Some("https://app.example.com"))
    assertEquals(Cors.allowOrigin(allowlist, "https://evil.com"), None)

  // ── preflight detection ─────────────────────────────────────────────────

  test("isPreflight: OPTIONS + Access-Control-Request-Method"):
    assert(Cors.isPreflight(req("OPTIONS", "Access-Control-Request-Method" -> "POST")))
    assert(!Cors.isPreflight(req("OPTIONS"))) // plain OPTIONS is not a preflight
    assert(!Cors.isPreflight(req("POST", "Access-Control-Request-Method" -> "POST")))

  // ── actual-request headers ──────────────────────────────────────────────

  test("actualHeaders: no Origin -> no CORS headers"):
    assertEquals(Cors.actualHeaders(allowlist, req("GET")), Map.empty[String, String])

  test("actualHeaders: disallowed origin -> no CORS headers"):
    assertEquals(Cors.actualHeaders(allowlist, req("GET", "Origin" -> "https://evil.com")), Map.empty[String, String])

  test("actualHeaders: allowed origin -> ACAO + Vary: Origin"):
    val hs = Cors.actualHeaders(allowlist, req("GET", "Origin" -> "https://app.example.com"))
    assertEquals(hs.get("Access-Control-Allow-Origin"), Some("https://app.example.com"))
    assertEquals(hs.get("Vary"), Some("Origin"))

  test("actualHeaders: Any origin -> ACAO '*', no Vary"):
    val hs = Cors.actualHeaders(CorsConfig.permissiveDev, req("GET", "Origin" -> "https://x.com"))
    assertEquals(hs.get("Access-Control-Allow-Origin"), Some("*"))
    assertEquals(hs.get("Vary"), None)

  test("actualHeaders: credentials + exposed headers"):
    val cfg = CorsConfig(
      allowedOrigins   = CorsOrigins.allowlist("https://app.example.com"),
      allowCredentials = true,
      exposedHeaders   = Set("X-Total-Count")
    )
    val hs = Cors.actualHeaders(cfg, req("GET", "Origin" -> "https://app.example.com"))
    assertEquals(hs.get("Access-Control-Allow-Credentials"), Some("true"))
    assertEquals(hs.get("Access-Control-Expose-Headers"), Some("X-Total-Count"))

  // ── preflight headers ───────────────────────────────────────────────────

  test("preflightHeaders: allowed origin -> full set incl OPTIONS and reflected ACRH"):
    val hs = Cors.preflightHeaders(
      allowlist,
      req(
        "OPTIONS",
        "Origin"                         -> "https://app.example.com",
        "Access-Control-Request-Method"  -> "POST",
        "Access-Control-Request-Headers" -> "content-type,x-custom"
      )
    )
    assertEquals(hs.get("Access-Control-Allow-Origin"), Some("https://app.example.com"))
    assert(hs.get("Access-Control-Allow-Methods").exists(_.contains("OPTIONS")))
    assert(hs.get("Access-Control-Allow-Methods").exists(_.contains("POST")))
    assertEquals(hs.get("Access-Control-Allow-Headers"), Some("content-type,x-custom"))
    assertEquals(hs.get("Access-Control-Max-Age"), Some("3600"))
    assert(hs.get("Vary").exists(_.contains("Origin")))

  test("preflightHeaders: Reflect with no ACRH -> Allow-Headers omitted"):
    val hs = Cors.preflightHeaders(
      allowlist,
      req("OPTIONS", "Origin" -> "https://app.example.com", "Access-Control-Request-Method" -> "GET")
    )
    assertEquals(hs.get("Access-Control-Allow-Headers"), None)

  test("preflightHeaders: Explicit allowed headers"):
    val cfg = allowlist.copy(allowedHeaders = CorsHeaders.Explicit(Set("Authorization")))
    val hs  = Cors.preflightHeaders(
      cfg,
      req("OPTIONS", "Origin" -> "https://app.example.com", "Access-Control-Request-Method" -> "GET")
    )
    assertEquals(hs.get("Access-Control-Allow-Headers"), Some("Authorization"))

  test("preflightHeaders: disallowed origin -> empty"):
    val hs = Cors.preflightHeaders(
      allowlist,
      req("OPTIONS", "Origin" -> "https://evil.com", "Access-Control-Request-Method" -> "POST")
    )
    assertEquals(hs, Map.empty[String, String])

  // ── config safety invariant ─────────────────────────────────────────────

  test("CorsConfig rejects credentials + wildcard origin at construction"):
    intercept[IllegalArgumentException] {
      CorsConfig(allowedOrigins = CorsOrigins.Any, allowCredentials = true)
    }

  test("maxAge is rendered in seconds"):
    val cfg = allowlist.copy(maxAge = Some(10.minutes))
    val hs  = Cors.preflightHeaders(
      cfg,
      req("OPTIONS", "Origin" -> "https://app.example.com", "Access-Control-Request-Method" -> "GET")
    )
    assertEquals(hs.get("Access-Control-Max-Age"), Some("600"))
