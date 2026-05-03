/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import meltkit.*

class MiddlewareCsrfTest extends munit.FunSuite:

  // ── Test helpers ──────────────────────────────────────────────────────────

  /** Identity effect for synchronous testing. */
  type Id[A] = A

  given Pure[Id] with
    override def pure[A](a: A): Id[A] = a

  val ok: Id[Response] = Response.text("ok")

  /** Builds a minimal RequestInfo stub. */
  def makeInfo(
    method:      String,
    path:        String               = "/",
    headers:     Map[String, String]  = Map.empty
  ): RequestInfo =
    val m = method.toUpperCase
    val h = headers.map((k, v) => k.toLowerCase -> v)
    new RequestInfo:
      override val method:      String                    = m
      override val requestPath: String                    = path
      override val locals:      Locals                    = new Locals()
      override val headers:     Map[String, String]       = h
      override val queryParams: Map[String, List[String]] = Map.empty
      override def query(name:    String): Option[String] = None
      override def queryAll(name: String): List[String]   = Nil
      override def header(name:   String): Option[String] = h.get(name.toLowerCase)
      override def cookie(name:   String): Option[String] = None
      override val cookies: Map[String, String]           = Map.empty

  def run(middleware: Middleware[Id], info: RequestInfo): Response =
    middleware(info, ok)

  // ── CsrfConfig.disabled ───────────────────────────────────────────────────

  test("disabled config skips all checks"):
    val info = makeInfo("POST", "/", Map(
      "Content-Type" -> "application/x-www-form-urlencoded"
      // no Origin header
    ))
    val result = run(Middleware.csrf(CsrfConfig.disabled), info)
    assertEquals(result, ok)

  // ── Non-form Content-Type ─────────────────────────────────────────────────

  test("application/json is skipped (protected by CORS)"):
    val info = makeInfo("POST", "/", Map("Content-Type" -> "application/json"))
    assertEquals(run(Middleware.csrf(), info), ok)

  test("missing Content-Type header is skipped"):
    val info = makeInfo("POST", "/")
    assertEquals(run(Middleware.csrf(), info), ok)

  test("Content-Type with charset param is still recognised as form"):
    val info = makeInfo("POST", "/", Map(
      "Content-Type" -> "application/x-www-form-urlencoded; charset=UTF-8",
      "Origin"       -> "https://example.com",
      "Host"         -> "example.com"
    ))
    assertEquals(run(Middleware.csrf(), info), ok)

  // ── Safe HTTP methods ─────────────────────────────────────────────────────

  test("GET with form Content-Type is skipped"):
    val info = makeInfo("GET", "/", Map("Content-Type" -> "application/x-www-form-urlencoded"))
    assertEquals(run(Middleware.csrf(), info), ok)

  test("HEAD is skipped"):
    val info = makeInfo("HEAD", "/", Map("Content-Type" -> "application/x-www-form-urlencoded"))
    assertEquals(run(Middleware.csrf(), info), ok)

  test("OPTIONS is skipped"):
    val info = makeInfo("OPTIONS", "/", Map("Content-Type" -> "application/x-www-form-urlencoded"))
    assertEquals(run(Middleware.csrf(), info), ok)

  test("TRACE is skipped"):
    val info = makeInfo("TRACE", "/", Map("Content-Type" -> "application/x-www-form-urlencoded"))
    assertEquals(run(Middleware.csrf(), info), ok)

  // ── Mutation methods are checked ──────────────────────────────────────────

  test("POST without Origin is rejected"):
    val info = makeInfo("POST", "/", Map("Content-Type" -> "application/x-www-form-urlencoded"))
    assertEquals(run(Middleware.csrf(), info).status, (403: StatusCode))

  test("PUT without Origin is rejected"):
    val info = makeInfo("PUT", "/", Map("Content-Type" -> "application/x-www-form-urlencoded"))
    assertEquals(run(Middleware.csrf(), info).status, (403: StatusCode))

  test("PATCH without Origin is rejected"):
    val info = makeInfo("PATCH", "/", Map("Content-Type" -> "application/x-www-form-urlencoded"))
    assertEquals(run(Middleware.csrf(), info).status, (403: StatusCode))

  test("DELETE without Origin is rejected"):
    val info = makeInfo("DELETE", "/", Map("Content-Type" -> "application/x-www-form-urlencoded"))
    assertEquals(run(Middleware.csrf(), info).status, (403: StatusCode))

  // ── Exempt paths ──────────────────────────────────────────────────────────

  test("exempt path exact match is skipped"):
    val config = CsrfConfig(exemptPaths = List("/api/webhook"))
    val info   = makeInfo("POST", "/api/webhook", Map("Content-Type" -> "application/x-www-form-urlencoded"))
    assertEquals(run(Middleware.csrf(config), info), ok)

  test("exempt path with trailing slash is skipped"):
    val config = CsrfConfig(exemptPaths = List("/api/webhook"))
    val info   = makeInfo("POST", "/api/webhook/github", Map("Content-Type" -> "application/x-www-form-urlencoded"))
    assertEquals(run(Middleware.csrf(config), info), ok)

  test("exempt path does not match path with same prefix but different separator"):
    val config = CsrfConfig(exemptPaths = List("/api/webhook"))
    val info   = makeInfo("POST", "/api/webhook-other", Map(
      "Content-Type" -> "application/x-www-form-urlencoded"
      // no Origin — would be rejected if not exempt
    ))
    // Not exempt → rejected for missing Origin
    assertEquals(run(Middleware.csrf(config), info).status, (403: StatusCode))

  // ── Origin validation ─────────────────────────────────────────────────────

  test("Origin matches server origin → allowed"):
    val info = makeInfo("POST", "/", Map(
      "Content-Type" -> "application/x-www-form-urlencoded",
      "Origin"       -> "https://example.com",
      "Host"         -> "example.com"
    ))
    assertEquals(run(Middleware.csrf(), info), ok)

  test("Origin mismatch → 403"):
    val info = makeInfo("POST", "/", Map(
      "Content-Type" -> "application/x-www-form-urlencoded",
      "Origin"       -> "https://evil.com",
      "Host"         -> "example.com"
    ))
    assertEquals(run(Middleware.csrf(), info).status, (403: StatusCode))

  test("Origin in trustedOrigins → allowed"):
    val config = CsrfConfig(trustedOrigins = Set("https://app.example.com"))
    val info   = makeInfo("POST", "/", Map(
      "Content-Type" -> "application/x-www-form-urlencoded",
      "Origin"       -> "https://app.example.com",
      "Host"         -> "example.com"
    ))
    assertEquals(run(Middleware.csrf(config), info), ok)

  test("Origin not in trustedOrigins → 403"):
    val config = CsrfConfig(trustedOrigins = Set("https://app.example.com"))
    val info   = makeInfo("POST", "/", Map(
      "Content-Type" -> "application/x-www-form-urlencoded",
      "Origin"       -> "https://other.example.com",
      "Host"         -> "example.com"
    ))
    assertEquals(run(Middleware.csrf(config), info).status, (403: StatusCode))

  test("missing Origin header → 403"):
    val info = makeInfo("POST", "/", Map(
      "Content-Type" -> "application/x-www-form-urlencoded",
      "Host"         -> "example.com"
    ))
    val result = run(Middleware.csrf(), info)
    assertEquals(result.status, (403: StatusCode))
    assertEquals(result.body, "CSRF check failed: missing Origin header")

  // ── multipart/form-data and text/plain ────────────────────────────────────

  test("multipart/form-data without Origin is rejected"):
    val info = makeInfo("POST", "/", Map("Content-Type" -> "multipart/form-data; boundary=----boundary"))
    assertEquals(run(Middleware.csrf(), info).status, (403: StatusCode))

  test("text/plain without Origin is rejected"):
    val info = makeInfo("POST", "/", Map("Content-Type" -> "text/plain"))
    assertEquals(run(Middleware.csrf(), info).status, (403: StatusCode))

  // ── trustForwardedHost ────────────────────────────────────────────────────

  test("trustForwardedHost uses X-Forwarded-Host for origin resolution"):
    val config = CsrfConfig(trustForwardedHost = true)
    val info   = makeInfo("POST", "/", Map(
      "Content-Type"     -> "application/x-www-form-urlencoded",
      "Origin"           -> "https://proxied.example.com",
      "Host"             -> "internal-host",
      "X-Forwarded-Host" -> "proxied.example.com",
      "X-Forwarded-Proto"-> "https"
    ))
    assertEquals(run(Middleware.csrf(config), info), ok)

  test("without trustForwardedHost, X-Forwarded-Host is ignored"):
    val config = CsrfConfig(trustForwardedHost = false)
    val info   = makeInfo("POST", "/", Map(
      "Content-Type"     -> "application/x-www-form-urlencoded",
      "Origin"           -> "https://proxied.example.com",
      "Host"             -> "internal-host",
      "X-Forwarded-Host" -> "proxied.example.com",
      "X-Forwarded-Proto"-> "https"
    ))
    // server origin = https://internal-host, origin = https://proxied.example.com → mismatch
    assertEquals(run(Middleware.csrf(config), info).status, (403: StatusCode))

  test("X-Forwarded-Proto overrides protocol inference"):
    val config = CsrfConfig(trustForwardedHost = true)
    val info   = makeInfo("POST", "/", Map(
      "Content-Type"     -> "application/x-www-form-urlencoded",
      "Origin"           -> "http://example.com",
      "Host"             -> "example.com",
      "X-Forwarded-Proto"-> "http"
    ))
    assertEquals(run(Middleware.csrf(config), info), ok)

  test("port 443 in Host infers https"):
    val info = makeInfo("POST", "/", Map(
      "Content-Type" -> "application/x-www-form-urlencoded",
      "Origin"       -> "https://example.com:443",
      "Host"         -> "example.com:443"
    ))
    assertEquals(run(Middleware.csrf(), info), ok)
