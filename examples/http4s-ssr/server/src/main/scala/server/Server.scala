/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package server

import java.nio.file.Paths
import java.util.UUID

import cats.effect.*
import com.comcast.ip4s.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.{ `Content-Type`, Location }
import org.http4s.server.Router
import org.http4s.server.staticcontent.*

import melt.runtime.ssr.{ Template, ViteManifest }
import components.*

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
  *      so the next GET re-renders the latest state. This path DOES NOT
  *      use the Hydration overload of `Template.render` — form-based
  *      CRUD reloads the page on every action, so no client JS is
  *      needed.
  *
  * == Running ==
  *
  * {{{
  *   sbt "http4s-ssr-client/fastLinkJS"   // once, to produce the
  *                                        // per-component JS chunks
  *   sbt "http4s-ssr-server/run"
  * }}}
  *
  * Then open http://localhost:8080/ for the hydration demo and
  * http://localhost:8080/todos for the stateful SSR-only TODO app.
  */
object Server extends IOApp.Simple:

  /** Loaded once at application startup — `Template` is immutable and
    * thread-safe, so we can share it across all requests.
    */
  private val template: Template =
    Template.fromResource("/index.html")

  /** Manual Vite manifest — we skip Vite entirely for this Phase C
    * minimum-viable demo and serve the Scala.js-linked chunks directly.
    */
  private val manifest: ViteManifest =
    ViteManifest.fromEntries(
      Map(
        "scalajs:home.js"    -> ViteManifest.Entry(file = "home.js"),
        "scalajs:about.js"   -> ViteManifest.Entry(file = "about.js"),
        "scalajs:todos.js"   -> ViteManifest.Entry(file = "todos.js"),
        "scalajs:status.js"  -> ViteManifest.Entry(file = "status.js")
      )
    )

  /** Absolute filesystem path to the client's `fastLinkJS` output
    * directory. `sbt run` forks with the project's `baseDirectory` as
    * `user.dir`, so we navigate from `server/` up to its sibling
    * `client/`.
    */
  private val clientDistDir =
    Paths
      .get(sys.props.getOrElse("user.dir", "."))
      .resolve("../client/target/scala-3.3.7/http4s-ssr-client-fastopt")
      .toAbsolutePath
      .normalize
      .toFile

  private def routes(todoStore: Ref[IO, List[Todos.Todo]]): HttpRoutes[IO] =
    HttpRoutes.of[IO] {

      // ── Hydration-enabled pages ────────────────────────────────────────
      case GET -> Root =>
        val props  = Home.Props(userName = "Melt", count = 1)
        val result = Home(props)
        Ok(
          template.render(
            result,
            manifest,
            title    = "Home · Melt SSR",
            lang     = "en",
            basePath = "/assets",
            vars     = Map.empty
          ),
          `Content-Type`(MediaType.text.html, Charset.`UTF-8`)
        )

      case GET -> Root / "about" =>
        val result = About()
        Ok(
          template.render(
            result,
            manifest,
            title    = "About · Melt SSR",
            lang     = "en",
            basePath = "/assets",
            vars     = Map.empty
          ),
          `Content-Type`(MediaType.text.html, Charset.`UTF-8`)
        )

      case GET -> Root / "status" / IntVar(n) =>
        // FQN to disambiguate from org.http4s.Status.
        val result = components.Status(components.Status.Props(isActive = n > 0, count = n))
        Ok(
          template.render(
            result,
            manifest,
            title    = s"Status $n · Melt SSR",
            lang     = "en",
            basePath = "/assets",
            vars     = Map.empty
          ),
          `Content-Type`(MediaType.text.html, Charset.`UTF-8`)
        )

      // ── SSR-only stateful TODO app ─────────────────────────────────────
      //
      // These routes use the NO-manifest overload of `Template.render`
      // so that no hydration script is injected — the app works purely
      // via full-page reloads on form submission.

      case GET -> Root / "todos" =>
        for
          items <- todoStore.get
          result = Todos(Todos.Props(items = items))
          html   = template.render(result, title = "Todos · Melt SSR")
          resp <- Ok(html, `Content-Type`(MediaType.text.html, Charset.`UTF-8`))
        yield resp

      case req @ POST -> Root / "todos" / "add" =>
        val io = for
          form <- req.as[UrlForm]
          text  = form.getFirst("text").map(_.trim).getOrElse("")
          _    <-
            if text.nonEmpty then
              val todo = Todos.Todo(id = UUID.randomUUID().toString, text = text)
              todoStore.update(todo :: _)
            else IO.unit
          resp <- SeeOther(Location(Uri.unsafeFromString("/todos")))
        yield resp
        io.handleErrorWith { e =>
          IO(e.printStackTrace()) >> InternalServerError(s"error: ${ e.getMessage }")
        }

      case POST -> Root / "todos" / "toggle" / id =>
        todoStore.update(_.map(t => if t.id == id then t.copy(done = !t.done) else t)) >>
          SeeOther(Location(Uri.unsafeFromString("/todos")))

      case POST -> Root / "todos" / "delete" / id =>
        todoStore.update(_.filterNot(_.id == id)) >>
          SeeOther(Location(Uri.unsafeFromString("/todos")))
    }

  /** Serves every path under `/assets` from the Scala.js client's
    * `fastLinkJS` output directory.
    */
  private val assetRoutes: HttpRoutes[IO] =
    fileService[IO](FileService.Config(clientDistDir.getAbsolutePath))

  def run: IO[Unit] =
    for
      todoStore <- Ref.of[IO, List[Todos.Todo]](Nil)
      httpApp    = Router(
                     "/"       -> routes(todoStore),
                     "/assets" -> assetRoutes
                   ).orNotFound
      _         <- EmberServerBuilder
                     .default[IO]
                     .withHost(host"0.0.0.0")
                     .withPort(port"8080")
                     .withHttpApp(httpApp)
                     .build
                     .useForever
    yield ()
