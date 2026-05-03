/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import meltkit.*

class CspNonceTest extends munit.FunSuite:

  // ── generate ──────────────────────────────────────────────────────────────

  test("generate returns a non-empty string"):
    assert(CspNonce.generate().nonEmpty)

  test("generate returns only URL-safe Base64 characters (no +, /, or =)"):
    val nonce = CspNonce.generate()
    assert(nonce.forall(c => c.isLetterOrDigit || c == '-' || c == '_'), s"nonce contains unexpected character: $nonce")

  test("generate returns different values on each call"):
    val n1 = CspNonce.generate()
    val n2 = CspNonce.generate()
    assertNotEquals(n1, n2)

  test("generate returns a string of expected length (128-bit = 22 URL-safe Base64 chars without padding)"):
    // 16 bytes → ceil(16 * 4 / 3) = 22 chars (without padding)
    assertEquals(CspNonce.generate().length, 22)

  // ── localsKey ─────────────────────────────────────────────────────────────

  test("localsKey can store and retrieve a nonce from Locals"):
    val locals = new Locals()
    val nonce  = CspNonce.generate()
    locals.set(CspNonce.localsKey, nonce)
    assertEquals(locals.get(CspNonce.localsKey), Some(nonce))

  test("localsKey returns None when nonce is not set"):
    val locals = new Locals()
    assertEquals(locals.get(CspNonce.localsKey), None)
