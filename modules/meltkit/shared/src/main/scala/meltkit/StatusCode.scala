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
  * `StatusCode` is a subtype of `Int`, so it can be passed wherever an `Int`
  * is expected (e.g. `org.http4s.Status.fromInt`).
  */
type StatusCode =
  // 2xx Success
  200 | 201 | 202 | 204 |
  // 3xx Redirection
  301 | 302 | 303 | 307 | 308 |
  // 4xx Client Error
  400 | 401 | 403 | 404 | 405 | 409 | 410 | 422 | 429 |
  // 5xx Server Error
  500 | 501 | 502 | 503 | 504
