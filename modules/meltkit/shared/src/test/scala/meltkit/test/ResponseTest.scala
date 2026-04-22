/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import meltkit.*

class ResponseTest extends munit.FunSuite:

  // ── Response factory methods ───────────────────────────────────────────────

  test("Response.text produces 200 text/plain"):
    val r = Response.text("hello")
    assertEquals(r.status,      (200: StatusCode))
    assertEquals(r.contentType, "text/plain; charset=utf-8")
    assertEquals(r.body,        "hello")

  test("Response.json produces 200 application/json"):
    val r = Response.json("""{"id":1}""")
    assertEquals(r.status,      (200: StatusCode))
    assertEquals(r.contentType, "application/json")
    assertEquals(r.body,        """{"id":1}""")

  test("Response.html produces 200 text/html"):
    val r = Response.html("<h1>Hi</h1>")
    assertEquals(r.status,      (200: StatusCode))
    assertEquals(r.contentType, "text/html; charset=utf-8")

  test("Response.noContent produces 204"):
    val r = Response.noContent
    assertEquals(r.status, (204: StatusCode))
    assertEquals(r.body,   "")

  test("Response.redirect produces 302 with Location header"):
    val r = Response.redirect("/home")
    assertEquals(r.status,             (302: StatusCode))
    assertEquals(r.headers("Location"), "/home")

  test("Response.redirect permanent produces 301"):
    val r = Response.redirect("/home", permanent = true)
    assertEquals(r.status, (301: StatusCode))

  test("Response.badRequest produces 400"):
    val r = Response.badRequest("invalid input")
    assertEquals(r.status, (400: StatusCode))
    assertEquals(r.body,   "invalid input")

  test("Response.notFound produces 404"):
    val r = Response.notFound("not found")
    assertEquals(r.status, (404: StatusCode))

  test("Response.notFound uses default message"):
    val r = Response.notFound()
    assertEquals(r.body, "Not Found")

  test("Response.unprocessableEntity produces 422"):
    val r = Response.unprocessableEntity("validation failed")
    assertEquals(r.status, (422: StatusCode))

  // ── withContentType ───────────────────────────────────────────────────────

  test("NotFound.withContentType returns NotFound with updated contentType"):
    val r: NotFound = NotFound().withContentType("application/json")
    assertEquals(r.contentType, "application/json")
    assertEquals(r.status,      (404: StatusCode))

  test("BadRequest.withContentType returns BadRequest"):
    val r: BadRequest = BadRequest("err").withContentType("application/json")
    assertEquals(r.contentType, "application/json")

  test("PlainResponse.withContentType returns PlainResponse"):
    val r: PlainResponse = Response.text("hi").withContentType("text/csv")
    assertEquals(r.contentType, "text/csv")

  // ── withHeaders ───────────────────────────────────────────────────────────

  test("NotFound.withHeaders returns NotFound with updated headers"):
    val r: NotFound = NotFound().withHeaders(Map("X-Trace" -> "abc"))
    assertEquals(r.headers, Map("X-Trace" -> "abc"))
    assertEquals(r.status,  (404: StatusCode))

  test("PlainResponse.withHeaders returns PlainResponse"):
    val r: PlainResponse = Response.json("{}").withHeaders(Map("X-Request-Id" -> "123"))
    assertEquals(r.headers("X-Request-Id"), "123")

  test("withContentType and withHeaders can be chained"):
    val r: NotFound = NotFound()
      .withContentType("application/json")
      .withHeaders(Map("X-Trace" -> "xyz"))
    assertEquals(r.contentType,       "application/json")
    assertEquals(r.headers("X-Trace"), "xyz")
    assertEquals(r.status,            (404: StatusCode))

  // ── Typed subtypes preserve concrete return type ───────────────────────────

  test("Unauthorized.withContentType preserves Unauthorized type"):
    val r: Unauthorized = Unauthorized().withContentType("application/json")
    assertEquals(r.status, (401: StatusCode))

  test("Forbidden.withHeaders preserves Forbidden type"):
    val r: Forbidden = Forbidden().withHeaders(Map("X-Reason" -> "no access"))
    assertEquals(r.status, (403: StatusCode))

  test("Conflict.withContentType preserves Conflict type"):
    val r: Conflict = Conflict("duplicate").withContentType("application/json")
    assertEquals(r.status, (409: StatusCode))

  test("UnprocessableEntity.withHeaders preserves UnprocessableEntity type"):
    val r: UnprocessableEntity = UnprocessableEntity("invalid").withHeaders(Map("X-Field" -> "email"))
    assertEquals(r.status, (422: StatusCode))
