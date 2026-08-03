/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import meltkit.{ NotFound, Response }

/** Tests for additive response-header helpers `withHeader` / `addHeaders`, which
  * merge rather than replace (unlike `withHeaders`) — so they are safe to combine
  * with `redirect` (which sets `Location`).
  */
class ResponseHeaderTest extends munit.FunSuite:

  test("withHeader adds a header, preserving existing ones"):
    val r = NotFound().withHeaders(Map("A" -> "1")).withHeader("B", "2")
    assertEquals(r.headers, Map("A" -> "1", "B" -> "2"))

  test("withHeader on a redirect preserves Location (no footgun)"):
    val r = Response.redirect("/next").withHeader("Content-Security-Policy", "default-src 'self'")
    assertEquals(r.headers.get("Location"), Some("/next"))
    assertEquals(r.headers.get("Content-Security-Policy"), Some("default-src 'self'"))

  test("withHeader overwrites an existing header of the same name"):
    val r = NotFound().withHeaders(Map("A" -> "1")).withHeader("A", "2")
    assertEquals(r.headers, Map("A" -> "2"))

  test("addHeaders merges into existing headers"):
    val r = Response.redirect("/x").addHeaders(Map("Cache-Control" -> "no-store", "X" -> "y"))
    assertEquals(r.headers, Map("Location" -> "/x", "Cache-Control" -> "no-store", "X" -> "y"))

  test("withHeaders still replaces all headers (documented behaviour, unchanged)"):
    val r = Response.redirect("/x").withHeaders(Map("Only" -> "this"))
    assertEquals(r.headers, Map("Only" -> "this")) // Location intentionally dropped
