/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

import org.scalajs.dom

import meltkit.*

/** Client-side invocation of a [[ServerFn]] `command`: encodes the argument,
  * POSTs it to the generated endpoint, and decodes the response — verified by
  * stubbing the global `fetch` so no real server is needed.
  */
class ServerFnClientTest extends munit.FunSuite:

  case class PostId(value: Int)
  case class Greeting(msg: String)

  private val greet = ServerFn.command[PostId, Greeting]("posts.greet")

  // Reads `globalThis` as an object first, then installs `fetch` on it, so the
  // stub is never a bare global read (which would throw when undefined).
  private def globalThis: js.Dynamic = js.Dynamic.global.globalThis

  private var original: js.Any     = null
  private var lastUrl:  String     = null
  private var lastInit: js.Dynamic = null

  /** Installs a `fetch` stub returning `status`/`body`, recording the request. */
  private def installFetch(status: Int, body: String): Unit =
    original = globalThis.selectDynamic("fetch").asInstanceOf[js.Any]
    val stub: js.Function2[String, js.Dynamic, js.Promise[js.Any]] = (url, init) =>
      lastUrl  = url
      lastInit = init
      // A real (empty) Headers so scala-js-dom's `jsIterator()` — which reads
      // `[Symbol.iterator]` — works when meltkit.Fetch walks the response headers.
      val headers = new dom.Headers()
      val res     = js.Dynamic.literal(
        status     = status,
        statusText = "",
        ok         = status >= 200 && status < 300,
        url        = url,
        headers    = headers
      )
      res.updateDynamic("text")(() => js.Promise.resolve[String](body))
      js.Promise.resolve[js.Any](res)
    globalThis.updateDynamic("fetch")(stub)

  private def restoreFetch(): Unit =
    globalThis.updateDynamic("fetch")(original)

  test("apply encodes the argument, POSTs JSON, and decodes the result"):
    installFetch(200, """{"msg":"hi 7"}""")
    greet(PostId(7)).map { out =>
      assertEquals(out, Greeting("hi 7"))
      assertEquals(lastUrl, "/_melt/fn/posts.greet")
      assertEquals(lastInit.method.asInstanceOf[String], "POST")
      assertEquals(lastInit.body.asInstanceOf[String], """{"value":7}""")
      restoreFetch()
    }

  test("a non-2xx response fails the Future with ServerFnException"):
    installFetch(400, "Invalid request body")
    greet(PostId(7)).failed.map { err =>
      val e = err.asInstanceOf[ServerFnException]
      assertEquals(e.status, 400)
      assertEquals(e.body, "Invalid request body")
      restoreFetch()
    }
