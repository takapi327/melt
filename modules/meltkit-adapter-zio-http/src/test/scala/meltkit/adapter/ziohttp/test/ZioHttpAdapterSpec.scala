/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.ziohttp.test

import java.io.File
import java.nio.file.Files

import zio.*
import zio.http.{ Method, Request, Response as ZResponse, Status, URL }
import zio.test.*

import melt.runtime.render.RenderResult

import meltkit.*
import meltkit.adapter.ziohttp.{ ZioHttpAdapter, ZioInstances }
import ZioInstances.given

/** Request dispatch through the adapter, exercised without starting a server.
  *
  * `Routes#runZIO` runs one request end to end, so these cover the same ground as the manual
  * server checks: route matching, the 404 path, defect handling, SSR rendering, static files,
  * hooks, CORS and CSP.
  */
object ZioHttpAdapterSpec extends ZIOSpecDefault:

  private type App[A] = ZIO[Any, Throwable, A]

  private def get(path: String): Request =
    Request.get(URL.decode(path).toOption.get)

  /** A client build with an `index.html` shell and one asset, as `ssrRoutes` expects. */
  private def withDist[A](f: File => ZIO[Any, Throwable, A]): ZIO[Any, Throwable, A] =
    ZIO.acquireReleaseWith(
      ZIO.attempt {
        val dir = Files.createTempDirectory("melt-zio-http").toFile
        Files.writeString(
          new File(dir, "index.html").toPath,
          """<!doctype html><html lang="%melt.lang%"><head>%melt.head%</head><body>%melt.body%</body></html>"""
        )
        Files.writeString(new File(dir, "app.js").toPath, "console.log('asset')")
        dir
      }
    )(dir => ZIO.attempt(dir.listFiles.foreach(_.delete())).ignore *> ZIO.attempt(dir.delete()).ignore)(f)

  /** One request's outcome, captured while zio-http's `Scope` is still open. */
  private final case class Out(status: Status, body: String, headers: Map[String, String]):
    def header(name: String): Option[String] = headers.get(name.toLowerCase)

  /** Runs one request against `routes` and drains the response inside the scope.
    *
    * `Routes#runZIO` is `ZIO[Scope & Env, Nothing, Response]`. That `Scope` is zio-http's, for
    * resources a handler opens — a file-backed body holds an open handle — so the body has to be
    * read before the scope closes. A real server gets its scope from `ZIOAppDefault` and keeps it
    * open for the response's lifetime; a test has to be explicit about the same window.
    */
  private def run(routes: zio.http.Routes[Any, ZResponse], request: Request): ZIO[Any, Throwable, Out] =
    ZIO.scoped {
      routes.runZIO(request).flatMap { res =>
        res.body.asString.map { body =>
          Out(res.status, body, res.headers.map(h => h.headerName.toLowerCase -> h.renderedValue).toMap)
        }
      }
    }

  private def apiApp: MeltKit[App] =
    val app = MeltKit[App]()
    app.get("api/ping") { ctx => ZIO.succeed(ctx.text("pong")) }
    app.post("api/echo") { ctx => ctx.body.text.map(ctx.text) }
    app.get("api/boom") { _ => ZIO.succeed[Response](throw new RuntimeException("kaboom")) }
    app

  def spec = suite("ZioHttpAdapter")(
    suite("routes (API only)")(
      test("dispatches a matching route") {
        val routes = ZioHttpAdapter.routes(apiApp)
        run(routes, get("/api/ping")).map(o => assertTrue(o.status == Status.Ok, o.body == "pong"))
      },
      test("reads the request body") {
        val routes = ZioHttpAdapter.routes(apiApp)
        run(routes, Request.post(URL.decode("/api/echo").toOption.get, zio.http.Body.fromString("hi")))
          .map(o => assertTrue(o.status == Status.Ok, o.body == "hi"))
      },
      test("returns 404 when nothing matches") {
        val routes = ZioHttpAdapter.routes(apiApp)
        run(routes, get("/nope")).map(res => assertTrue(res.status == Status.NotFound))
      },
      test("a route matching on path but not method is a 404") {
        val routes = ZioHttpAdapter.routes(apiApp)
        run(routes, Request.post(URL.decode("/api/ping").toOption.get, zio.http.Body.empty))
          .map(res => assertTrue(res.status == Status.NotFound))
      },
      test("a synchronous throw inside a handler becomes a 500, not a lost request") {
        // Regression for the defect path: without `Recover`'s sandbox and the dispatcher's
        // `catchAllCause`, this escapes as a `Cause.Die` instead of producing a response.
        val routes = ZioHttpAdapter.routes(apiApp)
        run(routes, get("/api/boom"))
          .map(o => assertTrue(o.status == Status.InternalServerError, o.body.contains("kaboom")))
      }
    ),
    suite("ssrRoutes")(
      test("renders a page through the shell, and keeps API routes and assets reachable") {
        // Regression for the routing-order defect: composing `Middleware.serveDirectory` with
        // `++` makes the static handler terminate every GET, so `/` and `/api/ping` both 404
        // whichever order the routes are combined in. Serving files from inside the catch-all
        // is what keeps all three reachable.
        withDist { dist =>
          val app = MeltKit[App]()
          app.get("") { ctx => ZIO.succeed(ctx.render(RenderResult(body = "<h1>page</h1>", head = ""))) }
          app.get("api/ping") { ctx => ZIO.succeed(ctx.text("pong")) }

          for
            routes   <- ZioHttpAdapter.ssrRoutes(app, dist, ViteManifest.empty)
            root    <- run(routes, get("/"))
            api     <- run(routes, get("/api/ping"))
            asset   <- run(routes, get("/app.js"))
            missing <- run(routes, get("/nope"))
          yield assertTrue(
            root.status == Status.Ok,
            root.body.contains("<h1>page</h1>"),
            root.body.contains("<!doctype html>"),
            api.status == Status.Ok,
            api.body == "pong",
            asset.status == Status.Ok,
            asset.body.contains("asset"),
            missing.status == Status.NotFound
          )
        }
      },
      test("a path traversal cannot escape the client build") {
        withDist { dist =>
          val app = MeltKit[App]()
          for
            routes <- ZioHttpAdapter.ssrRoutes(app, dist, ViteManifest.empty)
            res <- run(routes, get("/../../etc/passwd"))
          yield assertTrue(res.status == Status.NotFound)
        }
      }
    ),
    suite("hooks / CORS / CSP")(
      test("hooks wrap the handler") {
        val app = MeltKit[App]()
        app.use((_, resolve) => resolve().map(_.withHeaders(Map("x-hook" -> "ran"))))
        app.get("api/ping") { ctx => ZIO.succeed(ctx.text("pong")) }

        run(ZioHttpAdapter.routes(app), get("/api/ping"))
          .map(o => assertTrue(o.header("x-hook").contains("ran")))
      },
      test("an allowed origin gets CORS headers and a Vary") {
        val app = MeltKit[App]()
        app.cors(CorsConfig(allowedOrigins = CorsOrigins.allowlist("https://ok.example")))
        app.get("api/ping") { ctx => ZIO.succeed(ctx.text("pong")) }

        run(ZioHttpAdapter.routes(app), get("/api/ping").addHeader("Origin", "https://ok.example"))
          .map { o =>
            assertTrue(
              o.header("access-control-allow-origin").contains("https://ok.example"),
              o.header("vary").exists(_.contains("Origin"))
            )
          }
      },
      test("a disallowed origin gets no CORS headers") {
        val app = MeltKit[App]()
        app.cors(CorsConfig(allowedOrigins = CorsOrigins.allowlist("https://ok.example")))
        app.get("api/ping") { ctx => ZIO.succeed(ctx.text("pong")) }

        run(ZioHttpAdapter.routes(app), get("/api/ping").addHeader("Origin", "https://evil.example"))
          .map(o => assertTrue(o.header("access-control-allow-origin").isEmpty))
      },
      test("a preflight is answered before routing") {
        // OPTIONS is not a routable Melt method, so this only works because `Method.ANY`
        // delivers it to the catch-all, where the preflight branch runs first.
        val app = MeltKit[App]()
        app.cors(CorsConfig(allowedOrigins = CorsOrigins.allowlist("https://ok.example")))
        app.get("api/ping") { ctx => ZIO.succeed(ctx.text("pong")) }

        val preflight = Request
          .options(URL.decode("/api/ping").toOption.get)
          .addHeader("Origin", "https://ok.example")
          .addHeader("Access-Control-Request-Method", "POST")

        run(ZioHttpAdapter.routes(app), preflight)
          .map { o =>
            assertTrue(
              o.status == Status.NoContent,
              o.header("access-control-allow-origin").contains("https://ok.example")
            )
          }
      },
      test("CSP adds a per-request nonce to the header") {
        withDist { dist =>
          val app = MeltKit[App]()
          app.get("") { ctx => ZIO.succeed(ctx.render(RenderResult(body = "<h1>page</h1>", head = ""))) }

          for
            routes <- ZioHttpAdapter.ssrRoutes(
                        app,
                        dist,
                        ViteManifest.empty,
                        cspConfig = Some(CspConfig(Map("default-src" -> List("'self'"))))
                      )
            a <- run(routes, get("/"))
            b <- run(routes, get("/"))
            headerA = a.header("content-security-policy").getOrElse("")
            headerB = b.header("content-security-policy").getOrElse("")
          yield assertTrue(
            headerA.contains("default-src 'self'"),
            headerA.contains("'nonce-"),
            // A nonce is worthless if it repeats across requests.
            headerA != headerB
          )
        }
      }
    )
  )
