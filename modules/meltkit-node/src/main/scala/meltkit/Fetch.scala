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

/** HTTP client for Node.js v18+ — same API as the browser [[meltkit.Fetch]].
  *
  * Node.js v18+ provides `globalThis.fetch` which is compatible with the
  * Web Fetch API. `org.scalajs.dom.fetch` maps to `globalThis.fetch`, so
  * the implementation is identical to the browser-side client.
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

    dom.fetch(url, domInit).map { res =>
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
        _text = () => res.text().toFuture
      )
    }
