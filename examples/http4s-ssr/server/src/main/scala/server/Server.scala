/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package server

import java.util.UUID

import cats.effect.*
import com.comcast.ip4s.*
import components.*
import generated.AssetManifest
import io.circe.Codec
import org.http4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.staticcontent.*
import org.http4s.server.Router

import meltkit.*
import meltkit.adapter.http4s.CirceBodyDecoder.given
import meltkit.adapter.http4s.CirceBodyEncoder.given
import meltkit.adapter.http4s.Http4sAdapter
import meltkit.adapter.http4s.Http4sAdapter.given

/** SSR + Hydration server.
  *
  * Each route renders the corresponding page component directly via SSR.
  * The client JS hydrates only that page's component independently —
  * no shared SPA root required.  Navigation between pages is server-side
  * (full page reload via standard `<a href>` links).
  *
  * {{{ sbt "~http4s-ssr-server/reStart" }}}
  */
object Server extends IOApp.Simple:

  case class CreateTodoBody(text: String) derives Codec
  case class CreateUserBody(name: String, email: String, role: String) derives Codec
  case class UpdateUserBody(name: String, email: String, role: String) derives Codec

  private val initialUsers = List(
    User(1, "Alice", "alice@example.com", "Admin"),
    User(2, "Bob", "bob@example.com", "Developer"),
    User(3, "Charlie", "charlie@example.com", "Designer"),
    User(4, "Diana", "diana@example.com", "Developer"),
    User(5, "Eve", "eve@example.com", "Manager")
  )

  private val todoId = param[String]("id")
  private val userId = param[Int]("id")

  private def buildApp(
    todoStore: Ref[IO, List[Todo]],
    userStore: Ref[IO, List[User]],
    nextId:    Ref[IO, Int]
  ): MeltKit[IO] =
    val app = MeltKit[IO]()

    val createTodo = Endpoint.post("api/todos").body[CreateTodoBody]
    val createUser = Endpoint.post("api/users").body[CreateUserBody]
    val updateUser = Endpoint.put("api/users" / userId).body[UpdateUserBody]

    // ── Todo API ──────────────────────────────────────────────────────────

    app.get("api/todos") { ctx =>
      todoStore.get.map(ctx.ok(_))
    }

    app.on(createTodo) { ctx =>
      for
        body <- ctx.bodyOrBadRequest
        resp <-
          if body.text.nonEmpty then
            val todo = Todo(id = UUID.randomUUID().toString, text = body.text)
            todoStore.update(_ :+ todo).as(ctx.ok(todo))
          else IO.pure(ctx.badRequest(BodyError.DecodeError("text must not be empty")))
      yield resp
    }

    app.post("api/todos" / todoId / "toggle") { ctx =>
      todoStore
        .update(_.map(t => if t.id == ctx.params.id then t.copy(done = !t.done) else t))
        .as(ctx.text(""))
    }

    app.delete("api/todos" / todoId) { ctx =>
      todoStore.update(_.filterNot(_.id == ctx.params.id)).as(ctx.text(""))
    }

    // ── User API ──────────────────────────────────────────────────────────

    app.get("api/users") { ctx =>
      userStore.get.map(ctx.ok(_))
    }

    app.get("api/users" / userId) { ctx =>
      userStore.get.map { users =>
        users.find(_.id == ctx.params.id) match
          case Some(u) => ctx.ok(u)
          case None    => ctx.notFound(s"User ${ ctx.params.id } not found")
      }
    }

    app.on(createUser) { ctx =>
      for
        body <- ctx.bodyOrBadRequest
        resp <-
          if body.name.nonEmpty && body.email.nonEmpty then
            for
              id <- nextId.getAndUpdate(_ + 1)
              user = User(id = id, name = body.name, email = body.email, role = body.role)
              _ <- userStore.update(_ :+ user)
            yield ctx.ok(user)
          else IO.pure(ctx.badRequest(BodyError.DecodeError("name and email must not be empty")))
      yield resp
    }

    app.on(updateUser) { ctx =>
      for
        body <- ctx.bodyOrBadRequest
        resp <- userStore.modify { users =>
                  users.find(_.id == ctx.params.id) match
                    case None    => (users, ctx.notFound(s"User ${ ctx.params.id } not found"))
                    case Some(u) =>
                      val updated = u.copy(name = body.name, email = body.email, role = body.role)
                      (users.map(x => if x.id == ctx.params.id then updated else x), ctx.ok(updated))
                }
      yield resp
    }

    app.delete("api/users" / userId) { ctx =>
      userStore.update(_.filterNot(_.id == ctx.params.id)).as(ctx.text(""))
    }

    // ── SSR page routes ───────────────────────────────────────────────────
    // Each route renders the corresponding page component directly.
    // The client hydrates only that page's component — no shared SPA root.

    app.get("") { ctx =>
      todoStore.get.map(todos => ctx.render(TodoPage(TodoPage.Props(initialTodos = todos))))
    }

    app.get("counter") { ctx =>
      IO.delay(ctx.render(CounterPage()))
    }

    app.get("users") { ctx =>
      userStore.get.map(users => ctx.render(UserPage(UserPage.Props(initialUsers = users))))
    }

    app.get("users" / userId) { ctx =>
      userStore.get.map { users =>
        val user = users.find(_.id == ctx.params.id)
        ctx.render(UserDetailPage(UserDetailPage.Props(userId = ctx.params.id, initialUser = user)))
      }
    }

    app.getAll { ctx =>
      todoStore.get.map(todos => ctx.render(TodoPage(TodoPage.Props(initialTodos = todos))))
    }

    app

  private val assetRoutes: HttpRoutes[IO] =
    fileService[IO](FileService.Config(AssetManifest.clientDistDir.toString))

  def run: IO[Unit] =
    for
      todoStore <- Ref.of[IO, List[Todo]](
                     List(
                       Todo(UUID.randomUUID().toString, "Learn Melt SSR"),
                       Todo(UUID.randomUUID().toString, "Build a component", done = true),
                       Todo(UUID.randomUUID().toString, "Deploy to production")
                     )
                   )
      userStore <- Ref.of[IO, List[User]](initialUsers)
      nextId    <- Ref.of[IO, Int](initialUsers.size + 1)
      adapter   <- Http4sAdapter(buildApp(todoStore, userStore, nextId), AssetManifest.clientDistDir, AssetManifest.manifest)
      httpApp = Router(
                  "/assets" -> assetRoutes,
                  "/"       -> adapter.routes
                ).orNotFound
      _ <- EmberServerBuilder
             .default[IO]
             .withHost(host"0.0.0.0")
             .withPort(port"9090")
             .withHttpApp(httpApp)
             .build
             .useForever
    yield ()
