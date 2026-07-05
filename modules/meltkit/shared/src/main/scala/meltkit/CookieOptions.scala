/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** Options for a Set-Cookie directive.
  *
  * The defaults are chosen to fail safe: `httpOnly` is `true`, so a cookie
  * set with no options cannot be read by client-side JavaScript (mitigating
  * cookie theft via XSS). For a cookie that must be readable by JS, set
  * `httpOnly = false` explicitly.
  *
  * `secure` defaults to `false` so that local HTTP development is not
  * silently broken (a `Secure` cookie is never sent over plain HTTP). For
  * production authentication cookies, use [[CookieOptions.session]] or set
  * `secure = true`.
  *
  * @param httpOnly prevents JavaScript access to the cookie (default `true`)
  * @param secure   only sent over HTTPS (default `false`; see note above)
  * @param sameSite one of `"Strict"`, `"Lax"`, or `"None"` (compile-time checked).
  *                 Note: `"None"` requires a secure context per RFC 6265bis;
  *                 the server adapter automatically adds `; Secure` when
  *                 `sameSite = "None"` regardless of the [[secure]] field.
  * @param maxAge   expiry in seconds; `None` means a session cookie
  * @param path     cookie scope path (default `"/"`)
  * @param domain   cookie scope domain; `None` means current host only
  */
final case class CookieOptions(
  httpOnly: Boolean                   = true,
  secure:   Boolean                   = false,
  sameSite: "Strict" | "Lax" | "None" = "Lax",
  maxAge:   Option[Long]              = None,
  path:     String                    = "/",
  domain:   Option[String]            = None
)

object CookieOptions:

  /** Recommended options for authentication / session cookies:
    * `HttpOnly` (no JS access), `Secure` (HTTPS only), and `SameSite=Lax`.
    * Prefer this for anything sensitive:
    *
    * {{{
    * ctx.text("ok").withCookie("session_id", token, CookieOptions.session)
    * }}}
    */
  val session: CookieOptions =
    CookieOptions(httpOnly = true, secure = true, sameSite = "Lax")
