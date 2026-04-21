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
  */
final class Response(
  val status:      Int,
  val contentType: String,
  val body:        String,
  val headers:     Map[String, String]
)

object Response:

  def apply(
    status:      Int,
    contentType: String,
    body:        String,
    headers:     Map[String, String] = Map.empty
  ): Response = new Response(status, contentType, body, headers)

  def text(body: String): Response =
    Response(200, "text/plain; charset=utf-8", body)

  def html(body: String): Response =
    Response(200, "text/html; charset=utf-8", body)

  def json(body: String): Response =
    Response(200, "application/json", body)

  def redirect(location: String, permanent: Boolean = false): Response =
    Response(if permanent then 301 else 302, "text/plain", "", Map("Location" -> location))

  def badRequest(body: String): Response =
    Response(400, "text/plain; charset=utf-8", body)

  def notFound(body: String = "Not Found"): Response =
    Response(404, "text/plain; charset=utf-8", body)
