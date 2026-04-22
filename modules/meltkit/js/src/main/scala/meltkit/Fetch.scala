/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, Future }
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.Thenable.Implicits.*

import org.scalajs.dom

import meltkit.fetch.{ Headers, RequestInit, Response }

/** Cross-platform HTTP client matching the Node.js `fetch` API — JS implementation.
  *
  * Wraps `org.scalajs.dom.fetch` and follows the same two-phase model as the
  * Node.js fetch API:
  *
  *  1. `Fetch(url)` resolves as soon as the response headers are received.
  *  2. `Response.text()` reads the body stream and resolves when fully consumed.
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

  def apply(url: String)(using ec: ExecutionContext): Future[Response] =
    apply(url, RequestInit())

  def apply(url: String, init: RequestInit)(using ec: ExecutionContext): Future[Response] =
    val opts = js.Dynamic.literal(
      method  = init.method,
      headers = init.headers.toJSDictionary
    )
    init.body.foreach(b => opts.updateDynamic("body")(b))
    val domInit = opts.asInstanceOf[dom.RequestInit]

    // Resolves when headers are received — body is not yet consumed.
    dom.fetch(url, domInit).map { res =>
      // org.scalajs.dom.Headers.jsIterator() yields js.Array[ByteString] entries
      // where index 0 is the header name and index 1 is the value.
      val headerMap = mutable.Map.empty[String, List[String]]
      val iter      = res.headers.jsIterator()
      var entry     = iter.next()
      while !entry.done do
        val key   = entry.value(0).toLowerCase
        val value = entry.value(1)
        headerMap.update(key, headerMap.getOrElse(key, Nil) :+ value)
        entry = iter.next()

      new Response(
        status     = res.status,
        statusText = res.statusText,
        ok         = res.ok,
        url        = res.url,
        headers    = Headers(headerMap.toMap),
        // Body is read lazily when text() is called, matching Node.js behaviour.
        _text = () => res.text().toFuture
      )
    }
