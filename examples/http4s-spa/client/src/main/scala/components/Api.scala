/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package components

import scala.concurrent.ExecutionContext.Implicits.global

import melt.runtime.{ append, Var }

import io.circe.parser
import io.circe.Json
import meltkit.fetch.RequestInit
import meltkit.Fetch

/** Cross-platform HTTP client helper used by `.melt` event handlers.
  *
  * Uses `meltkit.Fetch` (JS: native `fetch`; JVM: `java.net.http.HttpClient`).
  * On the JVM/SSR side, relative URLs cause `Fetch` to return a failed `Future`;
  * `foreach` silently discards failures, so all methods no-op during SSR without
  * any platform-specific stubs.
  */
object Api:

  private def get(url: String) =
    Fetch(url).flatMap(_.text())

  private def send(url: String, method: String, body: Json) =
    Fetch(
      url,
      RequestInit(
        method  = method,
        headers = Map("Content-Type" -> "application/json"),
        body    = Some(body.noSpaces)
      )
    ).flatMap(_.text())

  // ── Todo API ──────────────────────────────────────────────────────────────

  def fetchTodos(todos: Var[List[Todo]], loaded: Var[Boolean]): Unit =
    get("/api/todos").foreach { body =>
      parser.decode[List[Todo]](body).foreach { list =>
        todos.set(list)
        loaded.set(true)
      }
    }

  def addTodo(text: String, todos: Var[List[Todo]]): Unit =
    send("/api/todos", "POST", Json.obj("text" -> Json.fromString(text))).foreach { resp =>
      parser.decode[Todo](resp).foreach(todos.append)
    }

  def toggleTodo(id: String): Unit =
    Fetch(s"/api/todos/$id/toggle", RequestInit(method = "POST"))

  def deleteTodo(id: String): Unit =
    Fetch(s"/api/todos/$id", RequestInit(method = "DELETE"))

  // ── User API ──────────────────────────────────────────────────────────────

  def fetchUsers(users: Var[List[User]], loaded: Var[Boolean]): Unit =
    get("/api/users").foreach { body =>
      parser.decode[List[User]](body).foreach { list =>
        users.set(list)
        loaded.set(true)
      }
    }

  def fetchUser(id: Int, user: Var[Option[User]], loaded: Var[Boolean]): Unit =
    get(s"/api/users/$id").foreach { body =>
      parser.decode[User](body).foreach { u =>
        user.set(Some(u))
        loaded.set(true)
      }
    }

  def addUser(name: String, email: String, role: String, users: Var[List[User]]): Unit =
    send(
      "/api/users",
      "POST",
      Json.obj("name" -> Json.fromString(name), "email" -> Json.fromString(email), "role" -> Json.fromString(role))
    ).foreach { resp =>
      parser.decode[User](resp).foreach(users.append)
    }

  def updateUser(
    id:    Int,
    name:  String,
    email: String,
    role:  String,
    user:  Var[Option[User]]
  ): Unit =
    send(
      s"/api/users/$id",
      "PUT",
      Json.obj("name" -> Json.fromString(name), "email" -> Json.fromString(email), "role" -> Json.fromString(role))
    ).foreach { resp =>
      parser.decode[User](resp).foreach { u => user.set(Some(u)) }
    }

  def deleteUser(id: Int): Unit =
    Fetch(s"/api/users/$id", RequestInit(method = "DELETE"))
