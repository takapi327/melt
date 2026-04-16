/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package components

import scala.scalajs.js

import melt.runtime.{ append, Var }

/** JS-only fetch helper used by `.melt` event handlers to communicate
  * with the server without page reloads.
  */
object Api:

  /** Fire-and-forget POST. */
  def post(url: String): Unit =
    js.Dynamic.global.fetch(
      url,
      js.Dynamic.literal(method = "POST")
    )

  /** POST /api/todos — sends JSON body, reads server response to get
    * the assigned ID, then appends the new Todo to the Var.
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

  /** POST /api/todos/:id/toggle — fire-and-forget. */
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

  /** GET /api/users — fetches the user list and replaces the Var. */
  def fetchUsers(users: Var[List[User]]): Unit =
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
      }

  /** Fetches todos and users, then sets `loaded` to true. */
  def fetchAll(todos: Var[List[Todo]], users: Var[List[User]], loaded: Var[Boolean]): Unit =
    val p1 = js.Dynamic.global
      .fetch("/api/todos")
      .`then`((r: js.Dynamic) => r.json())
      .`then` { (data: js.Dynamic) =>
        val list = data.asInstanceOf[js.Array[js.Dynamic]].toList.map { d =>
          Todo(d.id.asInstanceOf[String], d.text.asInstanceOf[String], d.done.asInstanceOf[Boolean])
        }
        todos.set(list)
      }
    val p2 = js.Dynamic.global
      .fetch("/api/users")
      .`then`((r: js.Dynamic) => r.json())
      .`then` { (data: js.Dynamic) =>
        val list = data.asInstanceOf[js.Array[js.Dynamic]].toList.map { d =>
          User(
            d.id.asInstanceOf[Double].toInt,
            d.name.asInstanceOf[String],
            d.email.asInstanceOf[String],
            d.role.asInstanceOf[String]
          )
        }
        users.set(list)
      }
    js.Dynamic.global.Promise
      .all(js.Array(p1, p2))
      .`then` { (_: js.Dynamic) => loaded.set(true) }
