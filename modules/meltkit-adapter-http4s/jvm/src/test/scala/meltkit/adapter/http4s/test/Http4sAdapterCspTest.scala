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

  test("buildCspValue derives script-src and style-src from default-src when absent"):
    val value = Http4sAdapter.buildCspValue(Map("default-src" -> List("'self'")), "abc123")
    // default-src is kept unchanged; derived script-src/style-src get the nonce
    assert(value.contains("default-src 'self'"), s"default-src should be unchanged: $value")
    assert(value.contains("script-src 'self' 'nonce-abc123'"), s"script-src should be derived with nonce: $value")
    assert(value.contains("style-src 'self' 'nonce-abc123'"), s"style-src should be derived with nonce: $value")

  test("buildCspValue handles multiple directives and only nonce-targets get nonce"):
    val directives = Map(
      "script-src" -> List("'self'"),
      "style-src"  -> List("'self'"),
      "img-src"    -> List("'self'", "data:")
    )
    val value = Http4sAdapter.buildCspValue(directives, "tok")
    assert(value.contains("script-src 'self' 'nonce-tok'"), s"script-src missing nonce: $value")
    assert(value.contains("style-src 'self' 'nonce-tok'"), s"style-src missing nonce: $value")
    assert(value.contains("img-src 'self' data:"), s"img-src should not have nonce: $value")
    assert(!value.contains("img-src 'self' data: 'nonce-"), s"img-src must not have nonce: $value")

  test("buildCspValue with empty directives returns empty string"):
    assertEquals(Http4sAdapter.buildCspValue(Map.empty, "abc123"), "")

  test("buildCspValue does not derive script-src when script-src is already present"):
    val value = Http4sAdapter.buildCspValue(
      Map("default-src" -> List("'self'"), "script-src" -> List("'strict-dynamic'")),
      "abc123"
    )
    assert(value.contains("script-src 'strict-dynamic' 'nonce-abc123'"), s"explicit script-src should be used: $value")
    assert(!value.contains("script-src 'self'"), s"default-src should not override explicit script-src: $value")

  // ── CspConfig methods ─────────────────────────────────────────────────────

  test("CspConfig.headerName returns Content-Security-Policy by default"):
    assertEquals(CspConfig().headerName, "Content-Security-Policy")

  test("CspConfig.headerName returns Content-Security-Policy-Report-Only when reportOnly"):
    assertEquals(CspConfig(reportOnly = true).headerName, "Content-Security-Policy-Report-Only")

  test("CspConfig.recommended contains script-src and style-src"):
    val rec = CspConfig.recommended
    assert(rec.directives.contains("script-src"), "recommended should have script-src")
    assert(rec.directives.contains("style-src"), "recommended should have style-src")
    assert(rec.directives.contains("default-src"), "recommended should have default-src")
    assert(rec.directives.contains("object-src"), "recommended should have object-src")

  test("CspConfig.recommended buildHeaderValue adds nonce to script-src and style-src"):
    val value = CspConfig.recommended.buildHeaderValue("tok")
    assert(value.contains("script-src 'self' 'nonce-tok'"), s"script-src should have nonce: $value")
    assert(value.contains("style-src 'self' 'nonce-tok'"), s"style-src should have nonce: $value")

  // ── Integration: CSP header on response ───────────────────────────────────

  private def makeAdapter(cspConfig: Option[CspConfig]): IO[Http4sAdapter[IO]] =
    import fs2.io.file.Files
    import fs2.io.file.Path
    import java.nio.file.Files as JFiles
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
      import java.nio.file.Files as JFiles
      val dir   = JFiles.createTempDirectory("meltkit-csp-test2")
      val index = dir.resolve("index.html")
      JFiles.writeString(index, "<html><body>%melt.body%</body></html>")
      Http4sAdapter[IO](app, Path.fromNioPath(dir), ViteManifest.empty, cspConfig = Some(config)).flatMap { a =>
        val req = Request[IO](method = Method.GET, uri = uri"/ping")
        a.routes.run(req).value.map { resp =>
          assert(resp.isDefined)
          val header = resp.get.headers.get(ci"Content-Security-Policy")
          assert(header.isDefined, "Expected Content-Security-Policy header")
          assert(
            header.get.head.value.startsWith("script-src 'self' 'nonce-"),
            s"Unexpected CSP value: ${ header.get.head.value }"
          )
        }
      }
    }

  test("Content-Security-Policy header is absent when cspConfig is None"):
    import fs2.io.file.Files
    import fs2.io.file.Path
    import java.nio.file.Files as JFiles
    val app = new ServerMeltKitPlatform[IO] {}
    app.get("ping") { ctx => IO.pure(ctx.text("pong")) }
    val dir   = JFiles.createTempDirectory("meltkit-csp-test3")
    val index = dir.resolve("index.html")
    JFiles.writeString(index, "<html><body>%melt.body%</body></html>")
    Http4sAdapter[IO](app, Path.fromNioPath(dir), ViteManifest.empty, cspConfig = None)
      .flatMap { a =>
        val req = Request[IO](method = Method.GET, uri = uri"/ping")
        a.routes.run(req).value.map { resp =>
          assert(resp.isDefined)
          assert(
            resp.get.headers.get(ci"Content-Security-Policy").isEmpty,
            "Expected no Content-Security-Policy header"
          )
        }
      }

  test("Content-Security-Policy-Report-Only header is set when reportOnly = true"):
    import fs2.io.file.Files
    import fs2.io.file.Path
    import java.nio.file.Files as JFiles
    val app = new ServerMeltKitPlatform[IO] {}
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
          assert(resp.get.headers.get(ci"Content-Security-Policy").isEmpty, "Expected no enforcing CSP header")
          val reportOnly = resp.get.headers.get(ci"Content-Security-Policy-Report-Only")
          assert(reportOnly.isDefined, "Expected Content-Security-Policy-Report-Only header")
        }
      }

  test("nonce in locals matches nonce in Content-Security-Policy header"):
    import fs2.io.file.Files
    import fs2.io.file.Path
    import java.nio.file.Files as JFiles
    val config        = CspConfig(directives = Map("script-src" -> List("'self'")))
    val capturedNonce = scala.collection.mutable.Buffer.empty[String]
    val app           = new ServerMeltKitPlatform[IO] {}
    app.use { (event, resolve) =>
      event.locals.get(CspNonce.localsKey).foreach(capturedNonce += _)
      resolve()
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
          assert(cspValue.contains(s"'nonce-$nonce'"), s"Expected nonce '$nonce' in CSP header: $cspValue")
        }
      }
