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

/** SSR + Hydration server.
  *
  * Renders initial HTML on the JVM via shared Melt components, then the
  * client hydrates for interactivity. Uses the same components as http4s-spa.
  *
  * {{{ sbt "~http4s-ssr-server/reStart" }}}
  */
object Server extends IOApp.Simple:

  private val template: Template = Template.fromResource("/index.html")

  private val htmlContentType: `Content-Type` =
    `Content-Type`(MediaType.text.html, Charset.`UTF-8`)

  private val jsonContentType: `Content-Type` =
    `Content-Type`(MediaType.application.json, Charset.`UTF-8`)

  private val users = List(
    User(1, "Alice", "alice@example.com", "Admin"),
    User(2, "Bob", "bob@example.com", "Developer"),
    User(3, "Charlie", "charlie@example.com", "Designer"),
    User(4, "Diana", "diana@example.com", "Developer"),
    User(5, "Eve", "eve@example.com", "Manager")
  )

  private def routes(todoStore: Ref[IO, List[Todo]]): HttpRoutes[IO] =
    HttpRoutes.of[IO] {

      // ── SSR pages ─────────────────────────────────────────────────────

      case GET -> Root =>
        for
          items <- todoStore.get
          html = renderWithHydration(
                   TodoPage(TodoPage.Props(items = items)),
                   title = "Todos · Melt SSR"
                 )
          resp <- Ok(html, htmlContentType)
        yield resp

      case GET -> Root / "counter" =>
        Ok(
          renderWithHydration(CounterPage(), title = "Counter · Melt SSR"),
          htmlContentType
        )

      case GET -> Root / "users" =>
        Ok(
          renderWithHydration(
            UserPage(UserPage.Props(items = users)),
            title = "Users · Melt SSR"
          ),
          htmlContentType
        )

      // ── Todo API ──────────────────────────────────────────────────────

      case GET -> Root / "api" / "todos" =>
        for
          todos <- todoStore.get
          json = todos.map(todoToJson).mkString("[", ",", "]")
          resp <- Ok(json, jsonContentType)
        yield resp

      case req @ POST -> Root / "api" / "todos" =>
        for
          body <- req.as[String]
          text = parseTextField(body)
          resp <-
            if text.nonEmpty then
              val todo = Todo(id = UUID.randomUUID().toString, text = text)
              todoStore.update(_ :+ todo) *>
                Created(todoToJson(todo), jsonContentType)
            else BadRequest()
        yield resp

      case POST -> Root / "api" / "todos" / id / "toggle" =>
        todoStore.update(_.map(t => if t.id == id then t.copy(done = !t.done) else t)) *>
          Ok()

      case DELETE -> Root / "api" / "todos" / id =>
        todoStore.update(_.filterNot(_.id == id)) *> Ok()

      // ── User API ──────────────────────────────────────────────────────

      case GET -> Root / "api" / "users" =>
        Ok(users.map(userToJson).mkString("[", ",", "]"), jsonContentType)
    }

  private def renderWithHydration(result: melt.runtime.ssr.RenderResult, title: String): String =
    template.render(
      result,
      AssetManifest.manifest,
      title    = title,
      lang     = "en",
      basePath = "/assets",
      vars     = Map.empty
    )

  private def todoToJson(t: Todo): String =
    s"""{"id":"${ t.id }","text":"${ escapeJson(t.text) }","done":${ t.done }}"""

  private def userToJson(u: User): String =
    s"""{"id":${ u.id },"name":"${ escapeJson(u.name) }","email":"${ escapeJson(u.email) }","role":"${ escapeJson(
        u.role
      ) }"}"""

  private def escapeJson(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

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
      todoStore <- Ref.of[IO, List[Todo]](
                     List(
                       Todo(UUID.randomUUID().toString, "Learn Melt SSR"),
                       Todo(UUID.randomUUID().toString, "Build a component", done = true),
                       Todo(UUID.randomUUID().toString, "Deploy to production")
                     )
                   )
      httpApp = Router(
                  "/"       -> routes(todoStore),
                  "/assets" -> assetRoutes
                ).orNotFound
      _ <- EmberServerBuilder
             .default[IO]
             .withHost(host"0.0.0.0")
             .withPort(port"9090")
             .withHttpApp(httpApp)
             .build
             .useForever
    yield ()
