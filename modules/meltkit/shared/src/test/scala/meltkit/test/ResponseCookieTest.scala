/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import meltkit.*

class ResponseCookieTest extends munit.FunSuite:

  // ── ResponseCookie.deleted ────────────────────────────────────────────────

  test("deleted sets maxAge=0 and empty value"):
    val c = ResponseCookie.deleted("session_id")
    assertEquals(c.name, "session_id")
    assertEquals(c.value, "")
    assertEquals(c.options.maxAge, Some(0L))

  test("deleted uses default path /"):
    val c = ResponseCookie.deleted("session_id")
    assertEquals(c.options.path, "/")

  test("deleted accepts custom path"):
    val c = ResponseCookie.deleted("token", path = "/api")
    assertEquals(c.options.path, "/api")

  // ── CookieOptions defaults ────────────────────────────────────────────────

  test("CookieOptions default sameSite is Lax"):
    assertEquals(CookieOptions().sameSite, "Lax")

  test("CookieOptions default path is /"):
    assertEquals(CookieOptions().path, "/")

  test("CookieOptions default httpOnly is false"):
    assertEquals(CookieOptions().httpOnly, false)

  test("CookieOptions default secure is false"):
    assertEquals(CookieOptions().secure, false)

  test("CookieOptions default maxAge is None"):
    assertEquals(CookieOptions().maxAge, None)

  test("CookieOptions default domain is None"):
    assertEquals(CookieOptions().domain, None)

  // ── ResponseCookie data ───────────────────────────────────────────────────

  test("ResponseCookie stores name, value, and options"):
    val opts = CookieOptions(httpOnly = true, secure = true, maxAge = Some(3600L))
    val c    = ResponseCookie("token", "abc123", opts)
    assertEquals(c.name, "token")
    assertEquals(c.value, "abc123")
    assertEquals(c.options.httpOnly, true)
    assertEquals(c.options.secure, true)
    assertEquals(c.options.maxAge, Some(3600L))
