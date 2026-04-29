/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** A cookie to be added to an HTTP response via a `Set-Cookie` header.
  *
  * This is a framework-independent data representation (meltkit-shared).
  * Serialization is delegated to the adapter layer, which is responsible for
  * RFC-compliant rendering (e.g. automatic `; Secure` for `SameSite=None`).
  *
  * @param name    cookie name
  * @param value   cookie value; use an empty string for deletion cookies
  * @param options Set-Cookie directives
  */
final case class ResponseCookie(
  name:    String,
  value:   String,
  options: CookieOptions = CookieOptions()
)

object ResponseCookie:

  /** Creates a deletion cookie (`Max-Age=0`, empty value).
    *
    * Instruct the browser to immediately expire the named cookie.
    *
    * @param name cookie name to delete
    * @param path must match the `Path` used when the cookie was set (default `"/"`)
    */
  def deleted(name: String, path: String = "/"): ResponseCookie =
    ResponseCookie(name, "", CookieOptions(maxAge = Some(0L), path = path))
