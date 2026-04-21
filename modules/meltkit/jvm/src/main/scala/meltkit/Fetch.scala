/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import meltkit.fetch.{ Headers, RequestInit, Response }

import java.net.URI
import java.net.http.{ HttpClient, HttpRequest, HttpResponse }
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.nio.charset.StandardCharsets

import scala.concurrent.{ ExecutionContext, Future }
import scala.jdk.CollectionConverters.*
import scala.jdk.FutureConverters.*

/** Cross-platform HTTP client matching the Node.js `fetch` API — JVM implementation.
  *
  * Uses `java.net.http.HttpClient` (Java 11+) and follows the same two-phase model
  * as the Node.js fetch API:
  *
  *  1. `Fetch(url)` resolves as soon as the response headers are received
  *     (`BodyHandlers.ofInputStream()` is used so the body is not yet consumed).
  *  2. `Response.text()` reads the `InputStream` and resolves when fully consumed.
  *
  * Other properties:
  *  - Redirect policy: `NORMAL` — safe redirects are followed automatically,
  *    matching the default `redirect: "follow"` behaviour of the Node.js fetch API.
  *  - `statusText` is always empty because `java.net.http.HttpClient` does not
  *    expose the HTTP status reason phrase.
  *  - The `HttpClient` instance is shared across all calls; it is immutable and
  *    thread-safe after construction.
  *
  * {{{
  * import scala.concurrent.ExecutionContext.Implicits.global
  *
  * // GET
  * Fetch("https://api.example.com/users").flatMap(_.text())
  *
  * // POST
  * Fetch(
  *   "https://api.example.com/users",
  *   RequestInit(
  *     method  = "POST",
  *     headers = Map("Content-Type" -> "application/json"),
  *     body    = Some("""{"name":"Alice"}""")
  *   )
  * ).flatMap(_.text())
  * }}}
  */
object Fetch:

  private val client: HttpClient =
    HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build()

  def apply(url: String)(using ec: ExecutionContext): Future[Response] =
    apply(url, RequestInit())

  def apply(url: String, init: RequestInit)(using ec: ExecutionContext): Future[Response] =
    val bodyPublisher = init.body match
      case Some(b) => BodyPublishers.ofString(b)
      case None    => BodyPublishers.noBody()

    val builder = HttpRequest.newBuilder(URI.create(url))
      .method(init.method, bodyPublisher)
    init.headers.foreach((k, v) => builder.header(k, v))

    // BodyHandlers.ofInputStream() resolves when headers are received.
    // The body InputStream is left open and read lazily when text() is called.
    client.sendAsync(builder.build(), BodyHandlers.ofInputStream()).asScala.map { res =>
      val headerMap: Map[String, List[String]] = res.headers.map.asScala
        .map((k, vs) => k.toLowerCase -> vs.asScala.toList)
        .toMap

      new Response(
        status     = res.statusCode,
        statusText = "",
        ok         = res.statusCode >= 200 && res.statusCode < 300,
        url        = res.uri.toString,
        headers    = Headers(headerMap),
        // Body is read lazily when text() is called, matching Node.js behaviour.
        // readAllBytes() blocks the thread, so it runs on the ExecutionContext.
        _text      = () => Future(new String(res.body.readAllBytes(), StandardCharsets.UTF_8))
      )
    }
