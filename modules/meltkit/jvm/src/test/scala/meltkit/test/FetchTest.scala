/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import com.sun.net.httpserver.{ HttpExchange, HttpServer }

import java.net.InetSocketAddress

import meltkit.Fetch
import meltkit.fetch.RequestInit

import scala.compiletime.uninitialized
import scala.concurrent.ExecutionContext.Implicits.global

class FetchTest extends munit.FunSuite:

  private var server:  HttpServer = uninitialized
  private var baseUrl: String     = uninitialized

  override def beforeAll(): Unit =
    // Port 0 lets the OS pick an available ephemeral port.
    server = HttpServer.create(new InetSocketAddress(0), 0)
    val port = server.getAddress.getPort
    baseUrl = s"http://localhost:$port"

    server.createContext("/hello",    exchange => respond(exchange, 200, "hello"))
    server.createContext("/not-found", exchange => respond(exchange, 404, "not found"))
    server.createContext("/headers",  exchange =>
      exchange.getResponseHeaders.add("X-Custom", "test-value")
      respond(exchange, 200, "ok")
    )
    server.createContext("/multi-cookie", exchange =>
      exchange.getResponseHeaders.add("Set-Cookie", "a=1")
      exchange.getResponseHeaders.add("Set-Cookie", "b=2")
      respond(exchange, 200, "ok")
    )
    server.createContext("/echo", exchange =>
      val body = new String(exchange.getRequestBody.readAllBytes(), "UTF-8")
      respond(exchange, 200, body)
    )
    server.start()

  override def afterAll(): Unit =
    if server != null then server.stop(0)

  private def respond(exchange: HttpExchange, status: Int, body: String): Unit =
    val bytes = body.getBytes("UTF-8")
    exchange.sendResponseHeaders(status, bytes.length)
    exchange.getResponseBody.write(bytes)
    exchange.close()

  // ── status & ok ──────────────────────────────────────────────────────────────

  test("GET returns status 200 and ok=true"):
    Fetch(s"$baseUrl/hello").map { res =>
      assertEquals(res.status, 200)
      assert(res.ok)
    }

  test("GET returns status 404 and ok=false"):
    Fetch(s"$baseUrl/not-found").map { res =>
      assertEquals(res.status, 404)
      assert(!res.ok)
    }

  // ── text() ───────────────────────────────────────────────────────────────────

  test("text() returns the response body"):
    Fetch(s"$baseUrl/hello").flatMap(_.text()).map { body =>
      assertEquals(body, "hello")
    }

  test("text() for 404 still returns the body"):
    Fetch(s"$baseUrl/not-found").flatMap(_.text()).map { body =>
      assertEquals(body, "not found")
    }

  // ── url ──────────────────────────────────────────────────────────────────────

  test("url reflects the final request URL"):
    Fetch(s"$baseUrl/hello").map { res =>
      assert(res.url.endsWith("/hello"), s"expected url to end with /hello but got ${res.url}")
    }

  // ── headers ──────────────────────────────────────────────────────────────────

  test("response headers are accessible via get"):
    Fetch(s"$baseUrl/headers").map { res =>
      assertEquals(res.headers.get("x-custom"), Some("test-value"))
    }

  test("missing response header returns None"):
    Fetch(s"$baseUrl/hello").map { res =>
      assertEquals(res.headers.get("x-missing"), None)
    }

  test("multi-value response headers are preserved via getAll"):
    Fetch(s"$baseUrl/multi-cookie").map { res =>
      assertEquals(res.headers.getAll("set-cookie"), List("a=1", "b=2"))
    }

  // ── POST ─────────────────────────────────────────────────────────────────────

  test("POST sends body and server echoes it back"):
    Fetch(
      s"$baseUrl/echo",
      RequestInit(method = "POST", body = Some("ping"))
    ).flatMap(_.text()).map { body =>
      assertEquals(body, "ping")
    }

  test("POST with custom header reaches the server"):
    Fetch(
      s"$baseUrl/echo",
      RequestInit(
        method  = "POST",
        headers = Map("Content-Type" -> "application/json"),
        body    = Some("""{"msg":"hello"}""")
      )
    ).flatMap(_.text()).map { body =>
      assertEquals(body, """{"msg":"hello"}""")
    }

  // ── RequestInit defaults ──────────────────────────────────────────────────────

  test("Fetch with no RequestInit defaults to GET"):
    Fetch(s"$baseUrl/hello").map { res =>
      assertEquals(res.status, 200)
    }
