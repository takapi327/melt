/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.ziohttp

import meltkit.{ Response as MeltResponse, ResponseCookie, StreamingResponse }
import zio.http.{ Body, Cookie, Header, Headers, MediaType, Response as ZResponse, Status }

/** Converts `meltkit`'s transport-neutral [[meltkit.Response]] into a zio-http `Response`. */
private[ziohttp] object ResponseConversion:

  /** Maps a Melt response cookie onto zio-http's `Cookie.Response`. */
  def toZioCookie(c: ResponseCookie): Cookie.Response =
    Cookie.Response(
      name       = c.name,
      content    = c.value,
      domain     = c.options.domain,
      path       = Some(zio.http.Path.decode(c.options.path)),
      isSecure   = c.options.secure,
      isHttpOnly = c.options.httpOnly,
      maxAge     = c.options.maxAge.map(zio.Duration.fromSeconds),
      sameSite   = c.options.sameSite match
        case "Strict" => Some(Cookie.SameSite.Strict)
        case "Lax"    => Some(Cookie.SameSite.Lax)
        case "None"   => Some(Cookie.SameSite.None)
    )

  /** Converts a Melt response into a zio-http one.
    *
    * A streaming-SSR response carries a pre-built [[ZStreamBody]] and is emitted with chunked
    * transfer encoding; every other response emits its single `String` body as one chunk.
    *
    * @param r the Melt response to convert
    */
  def toZioResponse(r: MeltResponse): ZResponse =
    val status = Status.fromInt(r.status)
    val body   = r match
      case StreamingResponse(_, _, b: ZStreamBody, _, _) => Body.fromStreamChunked(b.stream)
      case _                                             => Body.fromString(r.body)

    val contentType =
      Header.ContentType(MediaType.forContentType(r.contentType).getOrElse(MediaType.text.plain))
    val custom  = r.headers.map((k, v) => Header.Custom(k, v)).toList
    val headers = Headers(contentType :: custom)

    r.responseCookies.foldLeft(ZResponse(status, headers, body))((res, c) => res.addCookie(toZioCookie(c)))
