/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array

/** Utilities for per-request CSP nonce.
  *
  * When [[meltkit.adapter.http4s.Http4sAdapter]] is configured with a [[CspConfig]],
  * it generates a nonce for every request and stores it under [[localsKey]].
  *
  * Access the nonce from middleware or route handlers:
  * {{{
  * app.use { (info, next) =>
  *   val nonce: Option[String] = info.locals.get(CspNonce.localsKey)
  *   next
  * }
  * }}}
  */
object CspNonce:

  /** The [[LocalKey]] under which the per-request nonce is stored in [[Locals]]. */
  val localsKey: LocalKey[String] = LocalKey.make[String]

  /** Generates a cryptographically random nonce (128-bit, URL-safe Base64, no padding).
    *
    * Uses URL-safe Base64 (`-` and `_` instead of `+` and `/`) so the result is safe
    * in HTML attributes, URLs, and HTTP headers without escaping.
    * Backed by the Web Crypto API (`crypto.getRandomValues`) on Scala.js.
    *
    * Example output: `"dGhpcyBpcyBhIG5vbmNl"`
    */
  def generate(): String =
    val uint8 = new Uint8Array(16)
    js.Dynamic.global.crypto.getRandomValues(uint8)
    val bytes = new Array[Byte](16)
    for i <- 0 until 16 do bytes(i) = (uint8(i) & 0xFF).toByte
    java.util.Base64.getUrlEncoder.withoutPadding().encodeToString(bytes)
