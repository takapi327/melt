/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** Options for a Set-Cookie directive.
  *
  * @param httpOnly prevents JavaScript access to the cookie
  * @param secure   only sent over HTTPS
  * @param sameSite `"Strict"`, `"Lax"`, or `"None"`.
  *                 Note: `"None"` requires a secure context per RFC 6265bis;
  *                 the server adapter automatically adds `; Secure` when
  *                 `sameSite = "None"` regardless of the [[secure]] field.
  * @param maxAge   expiry in seconds; `None` means a session cookie
  * @param path     cookie scope path (default `"/"`)
  * @param domain   cookie scope domain; `None` means current host only
  */
final case class CookieOptions(
  httpOnly: Boolean        = false,
  secure:   Boolean        = false,
  sameSite: String         = "Lax",
  maxAge:   Option[Long]   = None,
  path:     String         = "/",
  domain:   Option[String] = None
)
