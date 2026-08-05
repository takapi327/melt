/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.concurrent.duration.*

/** Which cross-origin requesters are allowed.
  *
  * There is deliberately no "reflect any origin" mode — the choice is between an
  * explicit wildcard ([[Any]]) and an [[Allowlist]]. An allowlist reflects the
  * matching request `Origin` back (not `*`), so it also works with credentials.
  */
enum CorsOrigins:
  /** `Access-Control-Allow-Origin: *`. Incompatible with credentials. */
  case Any

  /** Allow only these exact origins (scheme+host+port, no trailing slash). A
    * matching request `Origin` is reflected back; others receive no CORS headers.
    * Include the literal `"null"` to allow `Origin: null` (e.g. sandboxed iframes). */
  case Allowlist(origins: Set[String])

object CorsOrigins:
  /** No origin allowed (effectively disables CORS). */
  val none: CorsOrigins = Allowlist(Set.empty)

  /** Convenience constructor: `CorsOrigins.allowlist("https://a.com", "https://b.com")`. */
  def allowlist(origins: String*): CorsOrigins = Allowlist(origins.toSet)

/** Which request headers a preflight permits on the actual request. */
enum CorsHeaders:
  /** Echo the browser's `Access-Control-Request-Headers` (practical default). */
  case Reflect

  /** Permit exactly these header names (case-insensitive on the wire). */
  case Explicit(names: Set[String])

/** Declarative CORS policy, applied by the server adapters (mirrors [[CspConfig]]).
  *
  * Pass via `app.cors(...)` (read by every adapter) or as the http4s adapter's
  * `corsConfig` parameter. Safe by construction: no blind origin reflection, and a
  * credentialed wildcard origin is rejected (browsers reject `*` with credentials).
  *
  * {{{
  * app.cors(CorsConfig(
  *   allowedOrigins   = CorsOrigins.allowlist("https://app.example.com"),
  *   allowCredentials = true
  * ))
  * }}}
  *
  * @param allowedOrigins   allowed requesters ([[CorsOrigins.Any]] or an allowlist)
  * @param allowedMethods   methods advertised in `Access-Control-Allow-Methods` (OPTIONS is always added;
  *                         HEAD is CORS-safelisted and need not be listed)
  * @param allowedHeaders   `Access-Control-Allow-Headers` policy ([[CorsHeaders.Reflect]] or explicit)
  * @param exposedHeaders   response headers JS may read (`Access-Control-Expose-Headers`)
  * @param allowCredentials whether to send `Access-Control-Allow-Credentials: true`
  * @param maxAge           preflight cache lifetime (`Access-Control-Max-Age`, in seconds)
  */
case class CorsConfig(
  allowedOrigins:   CorsOrigins            = CorsOrigins.none,
  allowedMethods:   Set[HttpMethod]        = CorsConfig.defaultMethods,
  allowedHeaders:   CorsHeaders            = CorsHeaders.Reflect,
  exposedHeaders:   Set[String]            = Set.empty,
  allowCredentials: Boolean                = false,
  maxAge:           Option[FiniteDuration] = Some(1.hour)
):
  // Browsers reject `Access-Control-Allow-Origin: *` together with credentials.
  require(
    !(allowCredentials && allowedOrigins == CorsOrigins.Any),
    "CORS: allowCredentials=true is incompatible with a wildcard origin ('*'). Use CorsOrigins.allowlist(...)."
  )

object CorsConfig:
  /** Routable methods MeltKit supports (OPTIONS is added automatically to the header). */
  val defaultMethods: Set[HttpMethod] = Set[HttpMethod]("GET", "POST", "PUT", "PATCH", "DELETE")

  /** Development-only: reflect any origin with the wildcard, no credentials. */
  val permissiveDev: CorsConfig = CorsConfig(allowedOrigins = CorsOrigins.Any)
