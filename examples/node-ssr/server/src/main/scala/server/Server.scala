/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package server

import scala.scalajs.js
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable.ListBuffer

import components.*
import meltkit.*
import meltkit.codec.BodyDecoder

/** Node.js SSR + Hydration server (pure Scala, no cats-effect / http4s).
  *
  * Same pages as http4s-ssr but runs entirely on Node.js with Future.
  *
  * {{{ sbt "~node-ssr-server/reStart" }}}
  */
object Server:

  // ── Simple JSON codec (no circe on server — pure string manipulation) ──

  // For the Node.js server we avoid circe and use a minimal JSON approach.
  // The request body is decoded from raw JSON strings, and responses use
  // the existing BodyEncoder mechanism.

  private given BodyDecoder[CreateTodoBody] with
    def decode(raw: String): Either[BodyError, CreateTodoBody] =
      SimpleJson.parseField(raw, "text").map(CreateTodoBody(_))

  private given BodyDecoder[CreateUserBody] with
    def decode(raw: String): Either[BodyError, CreateUserBody] =
      for
        name  <- SimpleJson.parseField(raw, "name")
        email <- SimpleJson.parseField(raw, "email")
        role  <- SimpleJson.parseField(raw, "role")
      yield CreateUserBody(name, email, role)

  private given BodyDecoder[UpdateUserBody] with
    def decode(raw: String): Either[BodyError, UpdateUserBody] =
      for
        name  <- SimpleJson.parseField(raw, "name")
        email <- SimpleJson.parseField(raw, "email")
        role  <- SimpleJson.parseField(raw, "role")
      yield UpdateUserBody(name, email, role)

  case class CreateTodoBody(text: String)
  case class CreateUserBody(name: String, email: String, role: String)
  case class UpdateUserBody(name: String, email: String, role: String)

  // ── Mutable stores (safe on Node.js single-threaded event loop) ────────

  private val todoStore = ListBuffer[Todo](
    Todo(randomId(), "Learn Melt SSR"),
    Todo(randomId(), "Build a component", done = true),
    Todo(randomId(), "Deploy to production")
  )

  private val initialUsers = List(
    User(1, "Alice", "alice@example.com", "Admin"),
    User(2, "Bob", "bob@example.com", "Developer"),
    User(3, "Charlie", "charlie@example.com", "Designer"),
    User(4, "Diana", "diana@example.com", "Developer"),
    User(5, "Eve", "eve@example.com", "Manager")
  )

  private val userStore = ListBuffer.from(initialUsers)
  private var nextUserId = initialUsers.size + 1

  private val todoId = param[String]("id")
  private val userId = param[Int]("id")

  // ── Application ────────────────────────────────────────────────────────

  val app: MeltApp[Future] = new MeltApp[Future]:

    // ── Todo API ──────────────────────────────────────────────────────

    get("api/todos") { ctx =>
      Future.successful(ctx.json(SimpleJson.encodeTodos(todoStore.toList)))
    }

    post("api/todos") { ctx =>
      ctx.body.json[CreateTodoBody].map {
        case Right(body) if body.text.nonEmpty =>
          val todo = Todo(id = randomId(), text = body.text)
          todoStore += todo
          ctx.json(SimpleJson.encodeTodo(todo))
        case Right(_) =>
          ctx.badRequest(BodyError.DecodeError("text must not be empty"))
        case Left(err) =>
          ctx.badRequest(err)
      }
    }

    post("api/todos" / todoId / "toggle") { ctx =>
      val id = ctx.params.id
      todoStore.zipWithIndex.find(_._1.id == id).foreach { case (todo, idx) =>
        todoStore(idx) = todo.copy(done = !todo.done)
      }
      Future.successful(ctx.text(""))
    }

    delete("api/todos" / todoId) { ctx =>
      todoStore.filterInPlace(_.id != ctx.params.id)
      Future.successful(ctx.text(""))
    }

    // ── User API ─────────────────────────────────────────────────────

    get("api/users") { ctx =>
      Future.successful(ctx.json(SimpleJson.encodeUsers(userStore.toList)))
    }

    get("api/users" / userId) { ctx =>
      userStore.find(_.id == ctx.params.id) match
        case Some(u) => Future.successful(ctx.json(SimpleJson.encodeUser(u)))
        case None    => Future.successful(ctx.notFound(s"User ${ ctx.params.id } not found"))
    }

    post("api/users") { ctx =>
      ctx.body.json[CreateUserBody].map {
        case Right(body) if body.name.nonEmpty && body.email.nonEmpty =>
          val id   = nextUserId; nextUserId += 1
          val user = User(id = id, name = body.name, email = body.email, role = body.role)
          userStore += user
          ctx.json(SimpleJson.encodeUser(user))
        case Right(_) =>
          ctx.badRequest(BodyError.DecodeError("name and email must not be empty"))
        case Left(err) =>
          ctx.badRequest(err)
      }
    }

    put("api/users" / userId) { ctx =>
      ctx.body.json[UpdateUserBody].map {
        case Right(body) =>
          userStore.zipWithIndex.find(_._1.id == ctx.params.id) match
            case Some((u, idx)) =>
              val updated = u.copy(name = body.name, email = body.email, role = body.role)
              userStore(idx) = updated
              ctx.json(SimpleJson.encodeUser(updated))
            case None =>
              ctx.notFound(s"User ${ ctx.params.id } not found")
        case Left(err) =>
          ctx.badRequest(err)
      }
    }

    delete("api/users" / userId) { ctx =>
      userStore.filterInPlace(_.id != ctx.params.id)
      Future.successful(ctx.text(""))
    }

    // ── SSR page routes ──────────────────────────────────────────────

    get("") { ctx =>
      Future.successful(ctx.render(TodoPage(TodoPage.Props(initialTodos = todoStore.toList))))
    }

    get("counter") { ctx =>
      Future.successful(ctx.render(CounterPage()))
    }

    get("users") { ctx =>
      Future.successful(ctx.render(UserPage(UserPage.Props(initialUsers = userStore.toList))))
    }

    get("users" / userId) { ctx =>
      val user = userStore.find(_.id == ctx.params.id)
      Future.successful(ctx.render(UserDetailPage(UserDetailPage.Props(userId = ctx.params.id, initialUser = user))))
    }

    getAll { ctx =>
      Future.successful(ctx.render(TodoPage(TodoPage.Props(initialTodos = todoStore.toList))))
    }

  // ── Entry point ────────────────────────────────────────────────────────

  def main(args: Array[String]): Unit =
    // Read template via Node.js fs module (scala.io.Source is JVM-only).
    // __dirname points to the fastopt output dir; resolve relative to process.cwd() instead.
    val fs       = js.Dynamic.global.require("fs")
    val nodePath = js.Dynamic.global.require("path")
    val templatePath = nodePath.resolve(
      js.Dynamic.global.process.cwd(),
      "examples/node-ssr/server/src/main/resources/index.html"
    ).asInstanceOf[String]
    val template = fs.readFileSync(templatePath, "utf8").asInstanceOf[String]
    NodeServer
      .builder(app)
      .withPort(9091)
      .withTemplate(template)
      .withClientDistDir(generated.AssetManifest.clientDistDir)
      .withManifest(generated.AssetManifest.manifest)
      .start()
      .foreach { server =>
        println(s"Node.js SSR server running at http://${ server.host }:${ server.port }")
      }

  private def randomId(): String =
    // Use Web Crypto API (available in Node.js) instead of java.util.UUID
    val bytes = new scala.scalajs.js.typedarray.Uint8Array(16)
    js.Dynamic.global.crypto.getRandomValues(bytes)
    bytes.toArray.map(b => f"${b & 0xff}%02x").mkString

/** Minimal JSON encoder/decoder — avoids circe dependency on the server. */
private object SimpleJson:

  def parseField(json: String, field: String): Either[BodyError, String] =
    // Simple regex-based extraction for {"field": "value"} patterns
    val pattern = s""""$field"\\s*:\\s*"([^"]*)"""".r
    pattern.findFirstMatchIn(json) match
      case Some(m) => Right(m.group(1))
      case None    => Left(BodyError.DecodeError(s"missing field: $field"))

  def encodeTodo(t: Todo): String =
    s"""{"id":"${ t.id }","text":"${ escapeJson(t.text) }","done":${ t.done }}"""

  def encodeTodos(ts: List[Todo]): String =
    ts.map(encodeTodo).mkString("[", ",", "]")

  def encodeUser(u: User): String =
    s"""{"id":${ u.id },"name":"${ escapeJson(u.name) }","email":"${ escapeJson(u.email) }","role":"${ escapeJson(u.role) }"}"""

  def encodeUsers(us: List[User]): String =
    us.map(encodeUser).mkString("[", ",", "]")

  private def escapeJson(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
