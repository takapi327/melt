/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.testkit

import scala.collection.mutable
import scala.scalajs.js

import org.scalajs.dom

/** Replaces the global `fetch` with a canned response for testing fetch-based
  * code — most notably a `use:enhance` submit, which jsdom cannot perform on its
  * own (it ships no `fetch`).
  *
  * Install a stub, drive the form, then read what was requested and restore:
  * {{{
  * val stub = FetchStub.install(body = EnhanceResult.failure(422, """{"errors":["bad"]}"""))
  * try
  *   c.userEvent.submit("form")
  *   // ...await the microtask, then assert the DOM / form state...
  *   assertEquals(stub.calls.head.method, "POST")
  * finally stub.restore()
  * }}}
  */
final class FetchStub private (private val restoreFn: () => Unit):

  private val _calls = mutable.ListBuffer.empty[FetchStub.Call]

  /** Every request the stub received, in order. */
  def calls: List[FetchStub.Call] = _calls.toList

  /** The most recent request, if any. */
  def lastCall: Option[FetchStub.Call] = _calls.lastOption

  /** Restores the real (pre-install) `fetch`. */
  def restore(): Unit = restoreFn()

  private[testkit] def record(call: FetchStub.Call): Unit = _calls += call

object FetchStub:

  /** A recorded request: the URL, HTTP method, headers and body of the fetch. */
  final case class Call(url: String, method: String, headers: Map[String, String], body: String)

  /** Installs a stub that answers every `fetch` with `status` + `body`. Returns a
    * handle to inspect [[FetchStub.calls]] and [[FetchStub.restore]] the original.
    */
  def install(status: Int = 200, body: String = ""): FetchStub =
    // `dom.fetch` resolves to the ambient `fetch`, which in a JSDOM test env may
    // live on the Node global and/or the JSDOM `window` — set (and restore) both.
    // Read `globalThis` as an OBJECT first: `js.Dynamic.global.selectDynamic("x")`
    // compiles to a bare `x` global read (ReferenceError when absent), whereas a
    // property access on the object is safe (yields `undefined`).
    val global = js.Dynamic.global.globalThis
    val window = dom.window.asInstanceOf[js.Dynamic]

    val originalGlobal = global.selectDynamic("fetch")
    val originalWindow = window.selectDynamic("fetch")

    val stub = new FetchStub(() =>
      global.updateDynamic("fetch")(originalGlobal)
      window.updateDynamic("fetch")(originalWindow)
    )

    val handler: js.Function2[js.Any, js.Any, js.Promise[dom.Response]] =
      (input: js.Any, init: js.Any) =>
        stub.record(readCall(input, init))
        js.Promise.resolve[dom.Response](fakeResponse(status, body))

    global.updateDynamic("fetch")(handler)
    window.updateDynamic("fetch")(handler)
    stub

  /** Builds the recorded [[Call]] from the fetch arguments. */
  private def readCall(input: js.Any, init: js.Any): Call =
    val url = input.toString
    val d   = init.asInstanceOf[js.Dynamic]
    val method =
      if js.isUndefined(d) || js.isUndefined(d.method) then "GET" else d.method.toString
    val body =
      if js.isUndefined(d) || js.isUndefined(d.body) then "" else d.body.toString
    val headers =
      if js.isUndefined(d) || js.isUndefined(d.headers) then Map.empty[String, String]
      else
        js.Object
          .entries(d.headers.asInstanceOf[js.Object])
          .map(e => e.asInstanceOf[js.Tuple2[String, js.Any]])
          .map(t => t._1 -> t._2.toString)
          .toMap
    Call(url, method, headers, body)

  /** A minimal `Response` exposing just the `status` and `text()` that callers
    * (e.g. the enhance action) read — avoids relying on a jsdom `Response`.
    */
  private def fakeResponse(status: Int, body: String): dom.Response =
    js.Dynamic
      .literal(
        status = status,
        statusText = "",
        ok = status >= 200 && status < 300,
        text = () => js.Promise.resolve[String](body)
      )
      .asInstanceOf[dom.Response]
