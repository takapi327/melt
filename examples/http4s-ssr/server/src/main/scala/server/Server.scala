/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package server

import java.util.UUID

import melt.runtime.ssr.Template

import cats.effect.*
import com.comcast.ip4s.*
import components.*
import generated.AssetManifest
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.`Content-Type`
import org.http4s.server.staticcontent.*
import org.http4s.server.Router

/** Phase C SSR + Hydration sample — a tiny http4s server that
  * demonstrates modern SSR patterns:
  *
  *   1. '''Static SSR pages''' — Home, About. Server renders HTML once
  *      per request; the page has hydration markers and a bootstrap
  *      script that calls each chunk's `hydrate()` export after the
  *      module loads.
  *   2. '''Dynamic SSR with conditional branches''' — Status. Server
  *      uses a path parameter to drive `if/else` and `match` rendering.
  *   3. '''SSR + Client-side interactivity''' — Todos. Server renders
  *      the initial list via SSR with Props serialisation. After
  *      hydration, the client takes over with reactive `Var[List[Todo]]`
  *      state. Add / toggle / delete operations update the local Var
  *      instantly (no page reload). The pattern demonstrates the full
  *      SSR → hydration → client-side reactivity lifecycle.
  *
  * == Running ==
  *
  * One-shot:
  * {{{
  *   sbt "http4s-ssr-server/run"
  * }}}
  *
  * Dev mode with auto-reload on source changes (recommended):
  * {{{
  *   sbt "~http4s-ssr-server/reStart"
  * }}}
  */
object Server extends IOApp.Simple:

  private val template: Template = Template.fromResource("/index.html")

  private def routes(todoStore: Ref[IO, List[Todos.Todo]]): HttpRoutes[IO] =
    HttpRoutes.of[IO] {

      // ── Hydration-enabled pages ──────────────────────────────────────────
      case GET -> Root =>
        val result = Home(Home.Props(userName = "Melt", count = 1))
        Ok(
          renderWithHydration(result, title = "Home · Melt SSR"),
          htmlContentType
        )

      case GET -> Root / "about" =>
        Ok(
          renderWithHydration(About(), title = "About · Melt SSR"),
          htmlContentType
        )

      case GET -> Root / "status" / IntVar(n) =>
        val result = components.Status(components.Status.Props(isActive = n > 0, count = n))
        Ok(
          renderWithHydration(result, title = s"Status $n · Melt SSR"),
          htmlContentType
        )

      // ── Todos: SSR + Hydration + JSON API ──────────────────────────────
      // GET renders the page with hydration. After hydration the client
      // manages its own Var[List[Todo]] reactively. Mutations fire
      // optimistic UI updates AND a fire-and-forget POST to the JSON
      // API below, so the server's Ref stays in sync and a page refresh
      // always returns the latest state.
      case GET -> Root / "todos" =>
        for
          items <- todoStore.get
          html = renderWithHydration(
                   Todos(Todos.Props(items = items)),
                   title = "Todos · Melt SSR"
                 )
          resp <- Ok(html, htmlContentType)
        yield resp

      // ── JSON API for client-side mutations ─────────────────────────────
      case req @ POST -> Root / "api" / "todos" / "add" =>
        for
          body <- req.as[String]
          text = parseTextField(body)
          resp <-
            if text.nonEmpty then
              val todo = Todos.Todo(id = UUID.randomUUID().toString, text = text)
              todoStore.update(todo :: _) *>
                Created(s"""{"id":"${todo.id}"}""", jsonContentType)
            else BadRequest()
        yield resp

      case POST -> Root / "api" / "todos" / "toggle" / id =>
        todoStore.update(_.map(t => if t.id == id then t.copy(done = !t.done) else t)) *>
          Ok()

      case POST -> Root / "api" / "todos" / "delete" / id =>
        todoStore.update(_.filterNot(_.id == id)) *> Ok()
    }

  // ── Small response helpers ─────────────────────────────────────────────

  private val htmlContentType: `Content-Type` =
    `Content-Type`(MediaType.text.html, Charset.`UTF-8`)

  private val jsonContentType: `Content-Type` =
    `Content-Type`(MediaType.application.json, Charset.`UTF-8`)

  private def renderWithHydration(result: melt.runtime.ssr.RenderResult, title: String): String =
    template.render(
      result,
      AssetManifest.manifest,
      title    = title,
      lang     = "en",
      basePath = "/assets",
      vars     = Map.empty
    )

  /** Extracts the "text" field from a minimal JSON body like
    * `{"text":"Buy milk"}`. Hand-rolled to avoid adding a JSON
    * library dependency to the example server.
    */
  private def parseTextField(json: String): String =
    val key = """"text":""""
    val idx = json.indexOf(key)
    if idx < 0 then ""
    else
      val start = idx + key.length
      val end   = json.indexOf('"', start)
      if end < 0 then "" else json.substring(start, end).trim

  private val assetRoutes: HttpRoutes[IO] =
    fileService[IO](FileService.Config(AssetManifest.clientDistDir.getAbsolutePath))

  def run: IO[Unit] =
    for
      todoStore <- Ref.of[IO, List[Todos.Todo]](
                     List(
                       Todos.Todo(UUID.randomUUID().toString, "Learn Melt SSR"),
                       Todos.Todo(UUID.randomUUID().toString, "Add hydration", done = true),
                       Todos.Todo(UUID.randomUUID().toString, "Ship it")
                     )
                   )
      httpApp = Router(
                  "/"       -> routes(todoStore),
                  "/assets" -> assetRoutes
                ).orNotFound
      _ <- EmberServerBuilder
             .default[IO]
             .withHost(host"0.0.0.0")
             .withPort(port"8080")
             .withHttpApp(httpApp)
             .build
             .useForever
    yield ()
