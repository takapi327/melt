/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** A compile-time–checked HTTP status code.
  *
  * Only the standard codes listed in the union are accepted.
  * Passing an arbitrary `Int` (e.g. `999`) is a compile error.
  *
  * {{{
  * val ok:      StatusCode = 200  // ✅
  * val invalid: StatusCode = 999  // ❌ compile error
  * }}}
  *
  * `StatusCode` is a subtype of `Int`, so it can be passed wherever an `Int` is expected.
  */
type StatusCode =
  // 2xx Success
  200 | 201 | 202 | 204 |
    // 3xx Redirection
    301 | 302 | 303 | 307 | 308 |
    // 4xx Client Error
    400 | 401 | 403 | 404 | 405 | 409 | 410 | 415 | 422 | 429 |
    // 5xx Server Error
    500 | 501 | 502 | 503 | 504

object StatusCode:

  /** Every code the [[StatusCode]] union admits, as plain `Int`s. */
  val all: Set[Int] =
    Set(200, 201, 202, 204, 301, 302, 303, 307, 308, 400, 401, 403, 404, 405, 409, 410, 415, 422, 429, 500, 501, 502,
      503, 504)

  /** Brings a runtime `Int` into the [[StatusCode]] union, or `None` if it is not
    * one of the supported codes.
    *
    * Writing a literal (`withStatus(404)`) is checked at compile time; `fromInt` is
    * the safe entry point when the code is only known at runtime — e.g. a status
    * relayed from a downstream HTTP call or a controller that returns a plain `Int`:
    *
    * {{{
    * StatusCode.fromInt(code) match
    *   case Some(sc) => ctx.text(body).withStatus(sc)
    *   case None     => ctx.text(body).withStatus(500) // fall back safely
    * }}}
    */
  def fromInt(code: Int): Option[StatusCode] =
    // `StatusCode` erases to `Int`, so the cast is a no-op; `all` is the real guard.
    if all.contains(code) then Some(code.asInstanceOf[StatusCode]) else None
