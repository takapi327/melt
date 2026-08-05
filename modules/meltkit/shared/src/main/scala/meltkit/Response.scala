/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** A framework-independent HTTP response.
  *
  * Adapter modules convert this to their own framework-specific response type.
  *
  * Built-in error subtypes (`NotFound`, `BadRequest`, etc.) can be used
  * directly as `errorOut` types in [[Endpoint]] definitions. User-defined
  * error types may also extend this trait.
  *
  * Every subtype exposes [[withContentType]] and [[withHeaders]] so that
  * callers can override the defaults without losing the concrete type:
  *
  * {{{
  * NotFound().withContentType("application/json").withHeaders(Map("X-Trace" -> "abc"))
  * }}}
  */
sealed trait Response:
  def status:          StatusCode
  def contentType:     String
  def body:            String
  def headers:         Map[String, String]
  def responseCookies: List[ResponseCookie]

  /** Returns a copy with the given content-type. */
  def withContentType(ct: String): Response

  /** Returns a copy with the given headers (replaces all existing headers). */
  def withHeaders(h: Map[String, String]): Response

  /** Returns a copy with a single header added, merged with the existing headers
    * (an existing header of the same name is overwritten).
    *
    * Unlike [[withHeaders]], this preserves other headers, so it is safe to combine
    * with `redirect` (which sets `Location`):
    * {{{
    * ctx.redirect("/next").withHeader("Content-Security-Policy", csp) // Location kept
    * }}}
    */
  def withHeader(name: String, value: String): Response =
    withHeaders(headers + (name -> value))

  /** Returns a copy with the given headers merged into (not replacing) the existing
    * ones; entries in `h` overwrite existing headers of the same name. */
  def addHeaders(h: Map[String, String]): Response =
    withHeaders(headers ++ h)

  /** Returns a copy with the given cookie appended to the response.
    *
    * Can be chained to add multiple cookies:
    * {{{
    * ctx.ok(data)
    *   .withCookie("session_id", token, CookieOptions(httpOnly = true, secure = true))
    *   .withCookie("csrf_token", csrf)
    * }}}
    */
  def withCookie(name: String, value: String, options: CookieOptions = CookieOptions()): Response

  /** Returns a copy with a cookie-deletion directive (`Max-Age=0`) appended.
    *
    * @param name cookie name to delete
    * @param path must match the `Path` used when the cookie was originally set
    */
  def withDeletedCookie(name: String, path: String = "/"): Response

  /** Returns a copy with the given HTTP status code — the single, uniform way to
    * set a status on any response, mirroring [[withContentType]] / [[withCookie]].
    *
    * The body builders default to `200` (`json` / `text` / `html` / `ok`) or their
    * natural code (`created` = 201); `withStatus` overrides it:
    *
    * {{{
    * ctx.json(err).withStatus(401)                 // 401 application/json
    * ctx.render(NotFoundPage()).withStatus(404)    // 404 SSR error page
    * }}}
    *
    * The general-purpose [[PlainResponse]] / [[StreamingResponse]] keep their
    * concrete type. The semantic subtypes ([[NotFound]], [[BadRequest]], …) have a
    * status fixed by design, so changing it returns a [[PlainResponse]] carrying
    * the same body / headers / cookies — it is no longer, say, "a NotFound".
    */
  def withStatus(status: StatusCode): Response =
    PlainResponse(status, contentType, body, headers, responseCookies)

final case class NotFound(
  message:         String               = "Not Found",
  contentType:     String               = "text/plain; charset=utf-8",
  headers:         Map[String, String]  = Map.empty,
  responseCookies: List[ResponseCookie] = List.empty
) extends Response:
  val status: StatusCode = 404
  val body = message
  override def withContentType(ct: String):                                     NotFound = copy(contentType = ct)
  override def withHeaders(h:      Map[String, String]):                        NotFound = copy(headers = h)
  override def withCookie(name: String, value: String, options: CookieOptions): NotFound =
    copy(responseCookies = responseCookies :+ ResponseCookie(name, value, options))
  override def withDeletedCookie(name: String, path: String): NotFound =
    copy(responseCookies = responseCookies :+ ResponseCookie.deleted(name, path))

final case class BadRequest(
  message:         String,
  contentType:     String               = "text/plain; charset=utf-8",
  headers:         Map[String, String]  = Map.empty,
  responseCookies: List[ResponseCookie] = List.empty
) extends Response:
  val status: StatusCode = 400
  val body = message
  override def withContentType(ct: String):                                     BadRequest = copy(contentType = ct)
  override def withHeaders(h:      Map[String, String]):                        BadRequest = copy(headers = h)
  override def withCookie(name: String, value: String, options: CookieOptions): BadRequest =
    copy(responseCookies = responseCookies :+ ResponseCookie(name, value, options))
  override def withDeletedCookie(name: String, path: String): BadRequest =
    copy(responseCookies = responseCookies :+ ResponseCookie.deleted(name, path))

final case class Unauthorized(
  message:         String               = "Unauthorized",
  contentType:     String               = "text/plain; charset=utf-8",
  headers:         Map[String, String]  = Map.empty,
  responseCookies: List[ResponseCookie] = List.empty
) extends Response:
  val status: StatusCode = 401
  val body = message
  override def withContentType(ct: String):                                     Unauthorized = copy(contentType = ct)
  override def withHeaders(h:      Map[String, String]):                        Unauthorized = copy(headers = h)
  override def withCookie(name: String, value: String, options: CookieOptions): Unauthorized =
    copy(responseCookies = responseCookies :+ ResponseCookie(name, value, options))
  override def withDeletedCookie(name: String, path: String): Unauthorized =
    copy(responseCookies = responseCookies :+ ResponseCookie.deleted(name, path))

final case class Forbidden(
  message:         String               = "Forbidden",
  contentType:     String               = "text/plain; charset=utf-8",
  headers:         Map[String, String]  = Map.empty,
  responseCookies: List[ResponseCookie] = List.empty
) extends Response:
  val status: StatusCode = 403
  val body = message
  override def withContentType(ct: String):                                     Forbidden = copy(contentType = ct)
  override def withHeaders(h:      Map[String, String]):                        Forbidden = copy(headers = h)
  override def withCookie(name: String, value: String, options: CookieOptions): Forbidden =
    copy(responseCookies = responseCookies :+ ResponseCookie(name, value, options))
  override def withDeletedCookie(name: String, path: String): Forbidden =
    copy(responseCookies = responseCookies :+ ResponseCookie.deleted(name, path))

final case class Conflict(
  message:         String,
  contentType:     String               = "text/plain; charset=utf-8",
  headers:         Map[String, String]  = Map.empty,
  responseCookies: List[ResponseCookie] = List.empty
) extends Response:
  val status: StatusCode = 409
  val body = message
  override def withContentType(ct: String):                                     Conflict = copy(contentType = ct)
  override def withHeaders(h:      Map[String, String]):                        Conflict = copy(headers = h)
  override def withCookie(name: String, value: String, options: CookieOptions): Conflict =
    copy(responseCookies = responseCookies :+ ResponseCookie(name, value, options))
  override def withDeletedCookie(name: String, path: String): Conflict =
    copy(responseCookies = responseCookies :+ ResponseCookie.deleted(name, path))

final case class UnprocessableEntity(
  message:         String,
  contentType:     String               = "text/plain; charset=utf-8",
  headers:         Map[String, String]  = Map.empty,
  responseCookies: List[ResponseCookie] = List.empty
) extends Response:
  val status: StatusCode = 422
  val body = message
  override def withContentType(ct: String):              UnprocessableEntity = copy(contentType = ct)
  override def withHeaders(h:      Map[String, String]): UnprocessableEntity = copy(headers = h)
  override def withCookie(name: String, value: String, options: CookieOptions): UnprocessableEntity =
    copy(responseCookies = responseCookies :+ ResponseCookie(name, value, options))
  override def withDeletedCookie(name: String, path: String): UnprocessableEntity =
    copy(responseCookies = responseCookies :+ ResponseCookie.deleted(name, path))

/** A general-purpose response for cases not covered by the typed subtypes. */
final case class PlainResponse(
  status:          StatusCode,
  contentType:     String,
  body:            String,
  headers:         Map[String, String]  = Map.empty,
  responseCookies: List[ResponseCookie] = List.empty
) extends Response:
  override def withContentType(ct: String):                                     PlainResponse = copy(contentType = ct)
  override def withHeaders(h:      Map[String, String]):                        PlainResponse = copy(headers = h)
  override def withCookie(name: String, value: String, options: CookieOptions): PlainResponse =
    copy(responseCookies = responseCookies :+ ResponseCookie(name, value, options))
  override def withDeletedCookie(name: String, path: String): PlainResponse =
    copy(responseCookies = responseCookies :+ ResponseCookie.deleted(name, path))
  override def withStatus(status: StatusCode): PlainResponse = copy(status = status)

/** Opaque, effect-library-neutral marker for a streaming response body.
  *
  * Core stays free of fs2/cats-effect, so the concrete carrier lives in the
  * adapter that can stream: the http4s adapter provides `Fs2StreamBody[F]`
  * wrapping an `fs2.Stream[F, Byte]`, and only that adapter constructs and reads
  * a [[StreamingResponse]]. Other transports never receive one — `renderStream`
  * degrades to `renderAsync` off http4s. */
trait StreamBody

/** A chunked-transfer response whose body is produced incrementally by a
  * [[StreamBody]] (streaming SSR). `body` is empty because the payload is the
  * stream; only the http4s adapter interprets [[stream]]. */
final case class StreamingResponse(
  status:          StatusCode,
  contentType:     String,
  stream:          StreamBody,
  headers:         Map[String, String]  = Map.empty,
  responseCookies: List[ResponseCookie] = List.empty
) extends Response:
  val body:                                              String            = ""
  override def withContentType(ct: String):              StreamingResponse = copy(contentType = ct)
  override def withHeaders(h:      Map[String, String]): StreamingResponse = copy(headers = h)
  override def withCookie(name: String, value: String, options: CookieOptions): StreamingResponse =
    copy(responseCookies = responseCookies :+ ResponseCookie(name, value, options))
  override def withDeletedCookie(name: String, path: String): StreamingResponse =
    copy(responseCookies = responseCookies :+ ResponseCookie.deleted(name, path))
  override def withStatus(status: StatusCode): StreamingResponse = copy(status = status)

object Response:
  def text(value: String): PlainResponse =
    PlainResponse(200, "text/plain; charset=utf-8", value)

  def html(value: String): PlainResponse =
    PlainResponse(200, "text/html; charset=utf-8", value)

  def json(value: String): PlainResponse =
    PlainResponse(200, "application/json", value)

  def noContent: PlainResponse =
    PlainResponse(204, "text/plain", "")

  def redirect(location: String, permanent: Boolean = false): PlainResponse =
    requireRelativePath(location)
    PlainResponse(
      if permanent then 301 else 302,
      "text/plain",
      "",
      Map("Location" -> location)
    )

  /** `303 See Other` — the correct redirect after a form POST (Post/Redirect/Get).
    *
    * Unlike 302, 303 explicitly tells the browser to issue a `GET` for the
    * target, so refreshing the destination never re-submits the form. Same
    * relative-path / open-redirect protection as [[redirect]].
    */
  def seeOther(location: String): PlainResponse =
    requireRelativePath(location)
    PlainResponse(303, "text/plain", "", Map("Location" -> location))

  /** Throws [[IllegalArgumentException]] if `path` is not a safe relative path.
    *
    * Uses an allowlist approach: only paths starting with `/` (but not `//` or `/\`)
    * and fragment-only references starting with `#` are accepted.
    * Everything else — including `javascript:`, `data:`, `vbscript:`, `//evil.com`,
    * `/\evil.com` (backslash open redirect), and empty strings — is rejected.
    *
    * Prevents open-redirect attacks when user-supplied input flows into `ctx.redirect`.
    */
  private[meltkit] def requireRelativePath(path: String): Unit =
    val isAbsPath = path.startsWith("/") && !path.startsWith("//") && !path.startsWith("/\\")
    val isAnchor  = path.startsWith("#")
    if !isAbsPath && !isAnchor then
      throw new IllegalArgumentException(
        s"Only relative paths are allowed for redirects: '$path'. " +
          "Use a path starting with '/' (e.g. \"/dashboard\") to prevent open-redirect attacks."
      )

  def badRequest(body: String): BadRequest =
    BadRequest(body)

  def notFound(body: String = "Not Found"): NotFound =
    NotFound(body)

  def unprocessableEntity(body: String): UnprocessableEntity =
    UnprocessableEntity(body)
