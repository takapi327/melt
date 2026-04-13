/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package components

import scala.scalajs.js

import melt.runtime.{ append, Var }

/** Client-side fetch helper for communicating with the http4s API. */
object Api:

  /** GET /api/todos — fetches the full todo list and replaces the Var. */
  def fetchTodos(todos: Var[List[Todo]]): Unit =
    js.Dynamic.global
      .fetch("/api/todos")
      .`then`((resp: js.Dynamic) => resp.json())
      .`then` { (data: js.Dynamic) =>
        val arr  = data.asInstanceOf[js.Array[js.Dynamic]]
        val list = arr.toList.map { d =>
          Todo(
            id   = d.id.asInstanceOf[String],
            text = d.text.asInstanceOf[String],
            done = d.done.asInstanceOf[Boolean]
          )
        }
        todos.set(list)
      }

  /** POST /api/todos — creates a todo on the server, then appends it
    * to the local Var with the server-assigned ID.
    */
  def addTodo(text: String, todos: Var[List[Todo]]): Unit =
    val body = js.JSON.stringify(js.Dynamic.literal(text = text))
    js.Dynamic.global
      .fetch(
        "/api/todos",
        js.Dynamic.literal(
          method  = "POST",
          headers = js.Dynamic.literal(`Content-Type` = "application/json"),
          body    = body
        )
      )
      .`then`((resp: js.Dynamic) => resp.json())
      .`then` { (data: js.Dynamic) =>
        val id = data.id.asInstanceOf[String]
        todos.append(Todo(id = id, text = text))
      }

  /** POST /api/todos/:id/toggle — fire-and-forget; the client has
    * already applied the optimistic update to its local Var.
    */
  def toggleTodo(id: String): Unit =
    js.Dynamic.global.fetch(
      s"/api/todos/$id/toggle",
      js.Dynamic.literal(method = "POST")
    )

  /** DELETE /api/todos/:id — fire-and-forget. */
  def deleteTodo(id: String): Unit =
    js.Dynamic.global.fetch(
      s"/api/todos/$id",
      js.Dynamic.literal(method = "DELETE")
    )

  /** GET /api/users — fetches the user list and updates the Var.
    * Sets `loading` to false when complete.
    */
  def fetchUsers(users: Var[List[User]], loading: Var[Boolean]): Unit =
    js.Dynamic.global
      .fetch("/api/users")
      .`then`((resp: js.Dynamic) => resp.json())
      .`then` { (data: js.Dynamic) =>
        val arr  = data.asInstanceOf[js.Array[js.Dynamic]]
        val list = arr.toList.map { d =>
          User(
            id    = d.id.asInstanceOf[Double].toInt,
            name  = d.name.asInstanceOf[String],
            email = d.email.asInstanceOf[String],
            role  = d.role.asInstanceOf[String]
          )
        }
        users.set(list)
        loading.set(false)
      }
