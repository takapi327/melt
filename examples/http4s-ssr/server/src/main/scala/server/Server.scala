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
import org.http4s.headers.{ `Content-Type`, Location }
import org.http4s.server.staticcontent.*
import org.http4s.server.Router

/** Phase C SSR + Hydration sample — a tiny http4s server that
  * demonstrates the three current-generation patterns:
  *
  *   1. '''Static SSR pages''' — Home, About. Server renders HTML once
  *      per request; the page has hydration markers and a bootstrap
  *      script that calls each chunk's `hydrate()` export after the
  *      module loads.
  *   2. '''Dynamic SSR with conditional branches''' — Status. Server
  *      uses a path parameter to drive `if/else` and `match` rendering.
  *   3. '''Stateful SSR CRUD''' — Todos. In-memory `Ref[IO, List[Todo]]`
  *      is updated by `POST /todos/{add,toggle/:id,delete/:id}` routes;
  *      each mutation responds with a `303 See Other` back to `/todos`
  *      so the next GET re-renders the latest state. This path DOES
  *      NOT use the hydration overload of `Template.render` — form-
  *      based CRUD reloads the page on every action, so no client JS
  *      is needed.
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
  *
  * `sbt-revolver`'s `~reStart` watches every `.melt` and `.scala`
  * file in the project. When you save, sbt regenerates the client
  * chunks, rebuilds `AssetManifest`, and restarts the server in
  * well under a second. Just reload the page in your browser.
  *
  * == How the manifest / chunks are wired ==
  *
  * `generated.AssetManifest` is a Scala source generated from the
  * client project's `Compile / fastLinkJS` output by a
  * `sourceGenerators` task in `build.sbt`. It exposes:
  *
  *   - `AssetManifest.manifest`      — a `ViteManifest` covering every
  *     `@JSExportTopLevel("hydrate", moduleID = …)` entry, built
  *     automatically from `Report.PublicModule` so adding or removing
  *     a `.melt` file re-flows through the pipeline with zero manual
  *     edits to this file.
  *   - `AssetManifest.clientDistDir` — the absolute path to the
  *     fastopt output directory, used by `http4s`'s `fileService` to
  *     serve `/assets`.
  */
object Server extends IOApp.Simple:

  /** Loaded once at application startup — `Template` is immutable and
    * thread-safe, so we can share it across all requests.
    */
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
        // FQN to disambiguate from org.http4s.Status.
        val result = components.Status(components.Status.Props(isActive = n > 0, count = n))
        Ok(
          renderWithHydration(result, title = s"Status $n · Melt SSR"),
          htmlContentType
        )

      // ── SSR-only stateful TODO app (no client JS) ───────────────────────
      case GET -> Root / "todos" =>
        for
          items <- todoStore.get
          html = template.render(Todos(Todos.Props(items = items)), title = "Todos · Melt SSR")
          resp <- Ok(html, htmlContentType)
        yield resp

      case req @ POST -> Root / "todos" / "add" =>
        for
          form <- req.as[UrlForm]
          text = form.getFirst("text").map(_.trim).getOrElse("")
          _ <-
            if text.nonEmpty then
              val todo = Todos.Todo(id = UUID.randomUUID().toString, text = text)
              todoStore.update(todo :: _)
            else IO.unit
          resp <- redirectToTodos
        yield resp

      case POST -> Root / "todos" / "toggle" / id =>
        todoStore.update(_.map(t => if t.id == id then t.copy(done = !t.done) else t)) *>
          redirectToTodos

      case POST -> Root / "todos" / "delete" / id =>
        todoStore.update(_.filterNot(_.id == id)) *> redirectToTodos
    }

  // ── Small response helpers ─────────────────────────────────────────────

  private val htmlContentType: `Content-Type` =
    `Content-Type`(MediaType.text.html, Charset.`UTF-8`)

  private val redirectToTodos: IO[Response[IO]] =
    SeeOther(Location(Uri.unsafeFromString("/todos")))

  /** Shorthand for the full Hydration-enabled `Template.render`
    * overload. All hydration pages share the same manifest / lang /
    * basePath / vars arguments, so we thread them through a single
    * helper to keep the route bodies one-liner friendly.
    */
  private def renderWithHydration(result: melt.runtime.ssr.RenderResult, title: String): String =
    template.render(
      result,
      AssetManifest.manifest,
      title    = title,
      lang     = "en",
      basePath = "/assets",
      vars     = Map.empty
    )

  /** Serves every path under `/assets` from the Scala.js client's
    * `fastLinkJS` output directory. The absolute path is embedded at
    * build time into `AssetManifest.clientDistDir` by the sbt
    * sourceGenerator.
    */
  private val assetRoutes: HttpRoutes[IO] =
    fileService[IO](FileService.Config(AssetManifest.clientDistDir.getAbsolutePath))

  def run: IO[Unit] =
    for
      todoStore <- Ref.of[IO, List[Todos.Todo]](Nil)
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
