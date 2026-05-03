/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.http4s.test

import munit.CatsEffectSuite

import cats.effect.IO
import meltkit.*
import meltkit.adapter.http4s.Http4sAdapter
import meltkit.adapter.http4s.Http4sAdapter.given
import org.http4s.*
import org.http4s.implicits.*
import org.typelevel.ci.*

class Http4sAdapterCspTest extends CatsEffectSuite:

  // ── buildCspValue ─────────────────────────────────────────────────────────

  test("buildCspValue appends nonce token to script-src"):
    val value = Http4sAdapter.buildCspValue(Map("script-src" -> List("'self'")), "abc123")
    assertEquals(value, "script-src 'self' 'nonce-abc123'")

  test("buildCspValue appends nonce token to style-src"):
    val value = Http4sAdapter.buildCspValue(Map("style-src" -> List("'self'")), "abc123")
    assertEquals(value, "style-src 'self' 'nonce-abc123'")

  test("buildCspValue does NOT append nonce to img-src"):
    val value = Http4sAdapter.buildCspValue(Map("img-src" -> List("'self'", "data:")), "abc123")
    assertEquals(value, "img-src 'self' data:")

  test("buildCspValue does NOT append nonce to default-src"):
    val value = Http4sAdapter.buildCspValue(Map("default-src" -> List("'self'")), "abc123")
    assertEquals(value, "default-src 'self'")

  test("buildCspValue handles multiple directives and only nonce-targets get nonce"):
    val directives = Map(
      "script-src" -> List("'self'"),
      "style-src"  -> List("'self'"),
      "img-src"    -> List("'self'", "data:")
    )
    val value = Http4sAdapter.buildCspValue(directives, "tok")
    assert(value.contains("script-src 'self' 'nonce-tok'"),  s"script-src missing nonce: $value")
    assert(value.contains("style-src 'self' 'nonce-tok'"),   s"style-src missing nonce: $value")
    assert(value.contains("img-src 'self' data:"),           s"img-src should not have nonce: $value")
    assert(!value.contains("img-src 'self' data: 'nonce-"), s"img-src must not have nonce: $value")

  test("buildCspValue with empty directives returns empty string"):
    assertEquals(Http4sAdapter.buildCspValue(Map.empty, "abc123"), "")

  // ── Integration: CSP header on response ───────────────────────────────────

  private def makeAdapter(cspConfig: Option[CspConfig]): IO[Http4sAdapter[IO]] =
    import fs2.io.file.Files
    import fs2.io.file.Path
    import java.nio.file.{Files => JFiles}
    val dir   = JFiles.createTempDirectory("meltkit-csp-test")
    val index = dir.resolve("index.html")
    JFiles.writeString(index, "<html><body>%melt.body%</body></html>")
    Http4sAdapter[IO](
      new ServerMeltKitPlatform[IO] {},
      Path.fromNioPath(dir),
      ViteManifest.empty,
      cspConfig = cspConfig
    )

  test("Content-Security-Policy header is set when cspConfig is provided"):
    val config = CspConfig(directives = Map("script-src" -> List("'self'")))
    makeAdapter(Some(config)).flatMap { adapter =>
      val app = new ServerMeltKitPlatform[IO] {}
      app.get("ping") { ctx => IO.pure(ctx.text("pong")) }
      // Build a fresh adapter wired to `app`
      import fs2.io.file.Files
      import fs2.io.file.Path
      import java.nio.file.{Files => JFiles}
      val dir   = JFiles.createTempDirectory("meltkit-csp-test2")
      val index = dir.resolve("index.html")
      JFiles.writeString(index, "<html><body>%melt.body%</body></html>")
      Http4sAdapter[IO](app, Path.fromNioPath(dir), ViteManifest.empty,
                        cspConfig = Some(config)).flatMap { a =>
        val req = Request[IO](method = Method.GET, uri = uri"/ping")
        a.routes.run(req).value.map { resp =>
          assert(resp.isDefined)
          val header = resp.get.headers.get(ci"Content-Security-Policy")
          assert(header.isDefined, "Expected Content-Security-Policy header")
          assert(header.get.head.value.startsWith("script-src 'self' 'nonce-"),
            s"Unexpected CSP value: ${header.get.head.value}")
        }
      }
    }

  test("Content-Security-Policy header is absent when cspConfig is None"):
    import fs2.io.file.Files
    import fs2.io.file.Path
    import java.nio.file.{Files => JFiles}
    val app   = new ServerMeltKitPlatform[IO] {}
    app.get("ping") { ctx => IO.pure(ctx.text("pong")) }
    val dir   = JFiles.createTempDirectory("meltkit-csp-test3")
    val index = dir.resolve("index.html")
    JFiles.writeString(index, "<html><body>%melt.body%</body></html>")
    Http4sAdapter[IO](app, Path.fromNioPath(dir), ViteManifest.empty, cspConfig = None)
      .flatMap { a =>
        val req = Request[IO](method = Method.GET, uri = uri"/ping")
        a.routes.run(req).value.map { resp =>
          assert(resp.isDefined)
          assert(resp.get.headers.get(ci"Content-Security-Policy").isEmpty,
            "Expected no Content-Security-Policy header")
        }
      }

  test("Content-Security-Policy-Report-Only header is set when reportOnly = true"):
    import fs2.io.file.Files
    import fs2.io.file.Path
    import java.nio.file.{Files => JFiles}
    val app    = new ServerMeltKitPlatform[IO] {}
    app.get("ping") { ctx => IO.pure(ctx.text("pong")) }
    val config = CspConfig(directives = Map("script-src" -> List("'self'")), reportOnly = true)
    val dir    = JFiles.createTempDirectory("meltkit-csp-test4")
    val index  = dir.resolve("index.html")
    JFiles.writeString(index, "<html><body>%melt.body%</body></html>")
    Http4sAdapter[IO](app, Path.fromNioPath(dir), ViteManifest.empty, cspConfig = Some(config))
      .flatMap { a =>
        val req = Request[IO](method = Method.GET, uri = uri"/ping")
        a.routes.run(req).value.map { resp =>
          assert(resp.isDefined)
          assert(resp.get.headers.get(ci"Content-Security-Policy").isEmpty,
            "Expected no enforcing CSP header")
          val reportOnly = resp.get.headers.get(ci"Content-Security-Policy-Report-Only")
          assert(reportOnly.isDefined, "Expected Content-Security-Policy-Report-Only header")
        }
      }

  test("nonce in locals matches nonce in Content-Security-Policy header"):
    import fs2.io.file.Files
    import fs2.io.file.Path
    import java.nio.file.{Files => JFiles}
    val config = CspConfig(directives = Map("script-src" -> List("'self'")))
    val capturedNonce = scala.collection.mutable.Buffer.empty[String]
    val app    = new ServerMeltKitPlatform[IO] {}
    app.use { (info, next) =>
      info.locals.get(CspNonce.localsKey).foreach(capturedNonce += _)
      next
    }
    app.get("ping") { ctx => IO.pure(ctx.text("pong")) }
    val dir   = JFiles.createTempDirectory("meltkit-csp-test5")
    val index = dir.resolve("index.html")
    JFiles.writeString(index, "<html><body>%melt.body%</body></html>")
    Http4sAdapter[IO](app, Path.fromNioPath(dir), ViteManifest.empty, cspConfig = Some(config))
      .flatMap { a =>
        val req = Request[IO](method = Method.GET, uri = uri"/ping")
        a.routes.run(req).value.map { resp =>
          assert(resp.isDefined)
          val cspValue = resp.get.headers.get(ci"Content-Security-Policy").get.head.value
          assert(capturedNonce.nonEmpty, "Expected nonce to be stored in locals")
          val nonce = capturedNonce.head
          assert(cspValue.contains(s"'nonce-$nonce'"),
            s"Expected nonce '$nonce' in CSP header: $cspValue")
        }
      }
