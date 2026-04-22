/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** A compile-time–checked HTTP method.
  *
  * Only the standard methods listed in the union are accepted.
  * Passing an arbitrary `String` (e.g. `"FOOBAR"`) is a compile error.
  *
  * {{{
  * val m: HttpMethod = "GET"     // ✅
  * val x: HttpMethod = "FOOBAR" // ❌ compile error
  * }}}
  *
  * `HttpMethod` is a subtype of `String`, so it can be passed wherever a
  * `String` is expected (e.g. comparison with `request.method.name`).
  */
type HttpMethod = "GET" | "POST" | "PUT" | "DELETE" | "PATCH"

object HttpMethod:
  /** Parses a string into an [[HttpMethod]], returning `None` for unrecognised values. */
  def fromString(s: String): Option[HttpMethod] = s match
    case m @ ("GET" | "POST" | "PUT" | "DELETE" | "PATCH") => Some(m)
    case _                                                  => None
