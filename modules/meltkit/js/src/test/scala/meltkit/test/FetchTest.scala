/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import meltkit.Fetch
import meltkit.fetch.RequestInit

import scala.concurrent.{ Future, Promise }
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{ global => g }

/** Integration tests for [[meltkit.Fetch]] on the JS platform.
  *
  * Uses Node.js's built-in `http` module to start an in-process HTTP server,
  * then exercises `Fetch` (which uses the native global `fetch`) against it.
  * Requires Node.js 18+ where `fetch` is available as a global.
  */
class FetchTest extends munit.FunSuite:

  // Completes with the ephemeral port once the server is listening.
  private val serverReady             = Promise[Int]()
  private var server: js.Dynamic      = js.undefined.asInstanceOf[js.Dynamic]

  override def beforeAll(): Unit =
    server = g.require("http").createServer { (req: js.Dynamic, res: js.Dynamic) =>
      req.url.asInstanceOf[String] match
        case "/hello" =>
          res.writeHead(200)
          res.end("hello")
        case "/not-found" =>
          res.writeHead(404)
          res.end("not found")
        case "/headers" =>
          res.writeHead(200, js.Dynamic.literal("X-Custom" -> "test-value"))
          res.end("ok")
        case "/multi-cookie" =>
          res.setHeader("Set-Cookie", js.Array("a=1", "b=2"))
          res.writeHead(200)
          res.end("ok")
        case "/echo" =>
          var body = ""
          req.on("data", (chunk: js.Dynamic) => body += chunk.toString())
          req.on("end", () => { res.writeHead(200); res.end(body) })
        case _ =>
          res.writeHead(404)
          res.end("not found")
    }
    server.listen(0, "127.0.0.1", () =>
      serverReady.success(server.address().port.asInstanceOf[Int])
    )

  override def afterAll(): Unit =
    if server != null then server.close()

  private def baseUrl: Future[String] =
    serverReady.future.map(port => s"http://127.0.0.1:$port")

  // ── status & ok ──────────────────────────────────────────────────────────────

  test("GET returns status 200 and ok=true"):
    baseUrl.flatMap(url => Fetch(s"$url/hello")).map { res =>
      assertEquals(res.status, 200)
      assert(res.ok)
    }

  test("GET returns status 404 and ok=false"):
    baseUrl.flatMap(url => Fetch(s"$url/not-found")).map { res =>
      assertEquals(res.status, 404)
      assert(!res.ok)
    }

  // ── text() ───────────────────────────────────────────────────────────────────

  test("text() returns the response body"):
    for
      url  <- baseUrl
      res  <- Fetch(s"$url/hello")
      body <- res.text()
    yield assertEquals(body, "hello")

  test("text() for 404 still returns the body"):
    for
      url  <- baseUrl
      res  <- Fetch(s"$url/not-found")
      body <- res.text()
    yield assertEquals(body, "not found")

  // ── url ──────────────────────────────────────────────────────────────────────

  test("url reflects the final request URL"):
    baseUrl.flatMap(url => Fetch(s"$url/hello")).map { res =>
      assert(res.url.endsWith("/hello"), s"expected url to end with /hello but got ${res.url}")
    }

  // ── headers ──────────────────────────────────────────────────────────────────

  test("response headers are accessible via get"):
    baseUrl.flatMap(url => Fetch(s"$url/headers")).map { res =>
      assertEquals(res.headers.get("x-custom"), Some("test-value"))
    }

  test("missing response header returns None"):
    baseUrl.flatMap(url => Fetch(s"$url/hello")).map { res =>
      assertEquals(res.headers.get("x-missing"), None)
    }

  test("multi-value response headers are preserved via getAll"):
    baseUrl.flatMap(url => Fetch(s"$url/multi-cookie")).map { res =>
      assertEquals(res.headers.getAll("set-cookie"), List("a=1", "b=2"))
    }

  // ── POST ─────────────────────────────────────────────────────────────────────

  test("POST sends body and server echoes it back"):
    for
      url  <- baseUrl
      res  <- Fetch(s"$url/echo", RequestInit(method = "POST", body = Some("ping")))
      body <- res.text()
    yield assertEquals(body, "ping")

  test("POST with custom header reaches the server"):
    for
      url  <- baseUrl
      res  <- Fetch(
                s"$url/echo",
                RequestInit(
                  method  = "POST",
                  headers = Map("Content-Type" -> "application/json"),
                  body    = Some("""{"msg":"hello"}""")
                )
              )
      body <- res.text()
    yield assertEquals(body, """{"msg":"hello"}""")

  // ── RequestInit defaults ──────────────────────────────────────────────────────

  test("Fetch with no RequestInit defaults to GET"):
    baseUrl.flatMap(url => Fetch(s"$url/hello")).map { res =>
      assertEquals(res.status, 200)
    }
