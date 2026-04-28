/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** A framework-independent HTTP response.
  *
  * Adapters (e.g. `meltkit-adapter-http4s`) convert this to their own
  * framework-specific response type.
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
  def status:      StatusCode
  def contentType: String
  def body:        String
  def headers:     Map[String, String]

  /** Returns a copy with the given content-type. */
  def withContentType(ct: String): Response

  /** Returns a copy with the given headers (replaces all existing headers). */
  def withHeaders(h: Map[String, String]): Response

final case class NotFound(
  message:     String              = "Not Found",
  contentType: String              = "text/plain; charset=utf-8",
  headers:     Map[String, String] = Map.empty
) extends Response:
  val status: StatusCode = 404
  val body = message
  override def withContentType(ct: String):              NotFound = copy(contentType = ct)
  override def withHeaders(h:      Map[String, String]): NotFound = copy(headers = h)

final case class BadRequest(
  message:     String,
  contentType: String              = "text/plain; charset=utf-8",
  headers:     Map[String, String] = Map.empty
) extends Response:
  val status: StatusCode = 400
  val body = message
  override def withContentType(ct: String):              BadRequest = copy(contentType = ct)
  override def withHeaders(h:      Map[String, String]): BadRequest = copy(headers = h)

final case class Unauthorized(
  message:     String              = "Unauthorized",
  contentType: String              = "text/plain; charset=utf-8",
  headers:     Map[String, String] = Map.empty
) extends Response:
  val status: StatusCode = 401
  val body = message
  override def withContentType(ct: String):              Unauthorized = copy(contentType = ct)
  override def withHeaders(h:      Map[String, String]): Unauthorized = copy(headers = h)

final case class Forbidden(
  message:     String              = "Forbidden",
  contentType: String              = "text/plain; charset=utf-8",
  headers:     Map[String, String] = Map.empty
) extends Response:
  val status: StatusCode = 403
  val body = message
  override def withContentType(ct: String):              Forbidden = copy(contentType = ct)
  override def withHeaders(h:      Map[String, String]): Forbidden = copy(headers = h)

final case class Conflict(
  message:     String,
  contentType: String              = "text/plain; charset=utf-8",
  headers:     Map[String, String] = Map.empty
) extends Response:
  val status: StatusCode = 409
  val body = message
  override def withContentType(ct: String):              Conflict = copy(contentType = ct)
  override def withHeaders(h:      Map[String, String]): Conflict = copy(headers = h)

final case class UnprocessableEntity(
  message:     String,
  contentType: String              = "text/plain; charset=utf-8",
  headers:     Map[String, String] = Map.empty
) extends Response:
  val status: StatusCode = 422
  val body = message
  override def withContentType(ct: String):              UnprocessableEntity = copy(contentType = ct)
  override def withHeaders(h:      Map[String, String]): UnprocessableEntity = copy(headers = h)

/** A general-purpose response for cases not covered by the typed subtypes. */
final case class PlainResponse(
  status:      StatusCode,
  contentType: String,
  body:        String,
  headers:     Map[String, String] = Map.empty
) extends Response:
  override def withContentType(ct: String):              PlainResponse = copy(contentType = ct)
  override def withHeaders(h:      Map[String, String]): PlainResponse = copy(headers = h)

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

  /** Throws [[IllegalArgumentException]] if `path` looks like an absolute or
    * protocol-relative URL.
    *
    * Allowed: paths that start with `/` but not `//`.
    * Rejected: `http://...`, `https://...`, `//...`, `javascript:...`, etc.
    *
    * This mirrors the same-origin check in SvelteKit's `goto()` and prevents
    * open-redirect attacks when user-supplied input flows into `ctx.redirect`.
    */
  private[meltkit] def requireRelativePath(path: String): Unit =
    if path.startsWith("//") || path.contains("://") then
      throw new IllegalArgumentException(
        s"External redirects are not allowed: '$path'. " +
          "Use a relative path (e.g. \"/dashboard\") to prevent open-redirect attacks."
      )

  def badRequest(body: String): BadRequest =
    BadRequest(body)

  def notFound(body: String = "Not Found"): NotFound =
    NotFound(body)

  def unprocessableEntity(body: String): UnprocessableEntity =
    UnprocessableEntity(body)
