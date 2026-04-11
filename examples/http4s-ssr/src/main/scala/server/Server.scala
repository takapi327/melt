/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package server

import cats.effect.*
import com.comcast.ip4s.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.`Content-Type`

import melt.runtime.ssr.Template
import components.*

/** Phase A SSR-only sample — a tiny http4s server that renders two
  * `.melt` components to HTML strings and serves them with
  * `Content-Type: text/html; charset=utf-8`.
  *
  * Uses a user-owned HTML template at
  * `src/main/resources/index.html` with SvelteKit-style placeholders
  * (`%melt.head%`, `%melt.body%`, `%melt.title%`, `%melt.lang%`). Edit
  * that file to customise the `<head>`, add analytics, OGP metadata,
  * favicons, etc.
  *
  * Run with:
  * {{{
  *   sbt "http4s-ssr/run"
  * }}}
  *
  * Then open http://localhost:8080/ in a browser.
  */
object Server extends IOApp.Simple:

  /** Loaded once at application startup — `Template` is immutable and
    * thread-safe, so we can share it across all requests.
    */
  private val template: Template =
    Template.fromResource("/index.html")

  private val routes: HttpApp[IO] = HttpRoutes
    .of[IO] {
      case GET -> Root =>
        val props  = Home.Props(userName = "Melt", count = 1)
        val result = Home(props)
        Ok(
          template.render(result, title = "Home · Melt SSR"),
          `Content-Type`(MediaType.text.html, Charset.`UTF-8`)
        )

      case GET -> Root / "about" =>
        val result = About()
        Ok(
          template.render(result, title = "About · Melt SSR"),
          `Content-Type`(MediaType.text.html, Charset.`UTF-8`)
        )

      case GET -> Root / "todos" =>
        val result = Todos(Todos.Props(items = List("Buy milk", "Write docs", "Ship Phase B")))
        Ok(
          template.render(result, title = "Todos · Melt SSR"),
          `Content-Type`(MediaType.text.html, Charset.`UTF-8`)
        )

      case GET -> Root / "status" / IntVar(n) =>
        // FQN to disambiguate from org.http4s.Status
        val result = components.Status(components.Status.Props(isActive = n > 0, count = n))
        Ok(
          template.render(result, title = s"Status $n · Melt SSR"),
          `Content-Type`(MediaType.text.html, Charset.`UTF-8`)
        )
    }
    .orNotFound

  def run: IO[Unit] =
    EmberServerBuilder
      .default[IO]
      .withHost(host"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(routes)
      .build
      .useForever
