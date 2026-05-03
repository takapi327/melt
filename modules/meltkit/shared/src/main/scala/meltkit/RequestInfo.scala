/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** A read-only view of an incoming request, passed to each [[Middleware]].
  *
  * Abstracts over the adapter-specific request type and the route-specific
  * path-parameter / body type parameters of [[MeltContext]], so that
  * middleware can be written without depending on any particular adapter.
  */
trait RequestInfo:

  /** The HTTP method name in uppercase (e.g. `"GET"`, `"POST"`). */
  def method: String

  /** The path component of the request URL (e.g. `"/users/42"`). */
  def requestPath: String

  /** Returns the first value of the named query parameter, if present. */
  def query(name: String): Option[String]

  /** Returns all values of the named query parameter. */
  def queryAll(name: String): List[String]

  /** Returns all query parameters as a multi-valued map. */
  def queryParams: Map[String, List[String]]

  /** Returns the value of the named request header (case-insensitive).
    *
    * If the same header appears more than once, values are joined with `", "`.
    */
  def header(name: String): Option[String]

  /** Returns all request headers as a lowercase-keyed name→value map. */
  def headers: Map[String, String]

  /** Returns the value of the named cookie from the `Cookie` header, if present. */
  def cookie(name: String): Option[String]

  /** Returns all cookies from the `Cookie` header as a name→value map. */
  def cookies: Map[String, String]

  /** The request-scoped local store.
    *
    * Middleware writes values here before calling `next`;
    * they are visible to subsequent middleware and the route handler.
    *
    * Write operations (`set`, `remove`) are provided by the adapter as
    * `F[Unit]`-returning extension methods (e.g. `LocalsOps` in the http4s adapter).
    */
  def locals: Locals

/** A function that wraps a route handler effect.
  *
  * {{{
  * // Authentication middleware
  * val auth: Middleware[IO] = (info, next) =>
  *   info.cookie("session_id") match
  *     case None     => IO.pure(Response.badRequest("Unauthorized"))
  *     case Some(id) => next   // proceed to the actual handler
  *
  * // Logging middleware
  * val logging: Middleware[IO] = (info, next) =>
  *   IO.println(s"${info.method} ${info.requestPath}") *> next
  *
  * // Response-modification middleware
  * val cors: Middleware[IO] = (info, next) =>
  *   next.map(_.withHeaders(Map("Access-Control-Allow-Origin" -> "*")))
  *
  * app.use(logging)
  * app.use(auth)
  * app.use(cors)
  * }}}
  *
  * Middlewares are applied in registration order.
  * Use [[Middleware.sequence]] to compose multiple middlewares into one.
  */
type Middleware[F[_]] = (RequestInfo, F[Response]) => F[Response]

object Middleware:

  /** Composes multiple middlewares into a single middleware, applied in order.
    *
    * {{{
    * app.use(Middleware.sequence(logging, auth, cors))
    * // equivalent to:
    * // app.use(logging)
    * // app.use(auth)
    * // app.use(cors)
    * }}}
    */
  def sequence[F[_]](ms: Middleware[F]*): Middleware[F] =
    ms.toList.foldRight[Middleware[F]]((_, next) => next) { (mw, acc) => (info, next) => mw(info, acc(info, next)) }

  /** Middleware that validates the `Origin` header on form requests to prevent CSRF attacks.
    *
    * Validation logic:
    *   1. `config.enabled = false` → skip
    *   2. Not a form Content-Type (`application/json`, etc.) → skip (protected by CORS preflight)
    *   3. Safe method (`GET`/`HEAD`/`OPTIONS`/`TRACE`) → skip
    *   4. Path matches `exemptPaths` (path-separator-aware) → skip
    *   5. `Origin` header is absent → reject (browsers always send Origin on form submissions)
    *   6. `Origin` matches server origin (protocol + host + port) → allow
    *   7. `Origin` is in `trustedOrigins` → allow
    *   8. Otherwise → 403 Forbidden
    *
    * {{{
    * // Basic usage
    * app.use(Middleware.csrf())
    *
    * // Allow form submissions from an external SPA
    * app.use(Middleware.csrf(CsrfConfig(
    *   trustedOrigins = Set("https://app.example.com")
    * )))
    *
    * // Exclude webhook endpoints
    * app.use(Middleware.csrf(CsrfConfig(
    *   exemptPaths = List("/api/webhook/")
    * )))
    * }}}
    */
  def csrf[F[_]: Pure](config: CsrfConfig = CsrfConfig.default): Middleware[F] =
    (info, next) =>
      if !config.enabled then next
      else if !isFormContentType(info) then next // application/json and similar are protected by CORS preflight
      else
        val method = info.method.toUpperCase
        if !Set("POST", "PUT", "PATCH", "DELETE").contains(method) then next
        else if isExemptPath(info.requestPath, config.exemptPaths) then next
        else
          info.header("Origin") match
            case None =>
              // Browsers always include Origin on form submissions.
              // A missing Origin is not legitimate browser behaviour → reject.
              Pure[F].pure(Forbidden("CSRF check failed: missing Origin header"))
            case Some(origin) =>
              val serverOrigin = resolveServerOrigin(info, config.trustForwardedHost)
              if origin == serverOrigin || config.trustedOrigins.contains(origin) then next
              else Pure[F].pure(Forbidden("CSRF check failed: Origin mismatch"))

  /** Returns true if the request has a form Content-Type that is subject to CSRF attacks.
    *
    * Only Content-Types that browsers can send without a CORS preflight are CSRF targets:
    *   - `application/x-www-form-urlencoded`
    *   - `multipart/form-data`
    *   - `text/plain`
    *
    * `application/json` requires a CORS preflight, so it is not a CSRF target.
    */
  private def isFormContentType(info: RequestInfo): Boolean =
    info.header("Content-Type").exists { ct =>
      val base = ct.split(';').head.trim.toLowerCase
      base == "application/x-www-form-urlencoded" ||
      base == "multipart/form-data" ||
      base == "text/plain"
    }

  /** Returns true if `requestPath` matches any entry in `exemptPaths`.
    *
    * Uses prefix matching with path-separator awareness:
    *   - `exemptPath = "/api/webhook"` matches `"/api/webhook"` and `"/api/webhook/"`
    *   - `exemptPath = "/api/webhook"` does NOT match `"/api/webhook-other"`
    */
  private def isExemptPath(requestPath: String, exemptPaths: List[String]): Boolean =
    exemptPaths.exists { exemptPath =>
      requestPath == exemptPath ||
      requestPath.startsWith(exemptPath.stripSuffix("/") + "/")
    }

  /** Builds the server's own origin string (protocol + host + port).
    *
    * The `Origin` header uses the full form `"https://example.com"`.
    * The protocol is inferred from the `Host` header (`"example.com"` or `"example.com:8080"`).
    *
    * Protocol resolution order:
    *   - `X-Forwarded-Proto` header if present
    *   - `https` if the host ends with `:443`
    *   - `https` otherwise (production assumed)
    */
  private def resolveServerOrigin(info: RequestInfo, trustForwardedHost: Boolean): String =
    val host =
      if trustForwardedHost then info.header("X-Forwarded-Host").orElse(info.header("Host")).getOrElse("")
      else info.header("Host").getOrElse("")
    val proto = info
      .header("X-Forwarded-Proto")
      .orElse(if host.endsWith(":443") then Some("https") else None)
      .getOrElse("https")
    s"$proto://$host"
