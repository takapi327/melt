/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package components

import scala.scalajs.js

import melt.runtime.Var

/** JS-only fetch helper used by `.melt` event handlers to communicate
  * with the server without page reloads.
  */
object Api:

  /** Fire-and-forget POST. Used for toggle/delete where the client
    * has already applied the optimistic update to its local Var.
    */
  def post(url: String): Unit =
    js.Dynamic.global.fetch(
      url,
      js.Dynamic.literal(method = "POST")
    )

  /** POST /api/todos/add — sends JSON body, reads server response to
    * get the assigned ID, then prepends the new Todo to the Var.
    */
  def addTodo(text: String, todos: Var[List[Todos.Todo]]): Unit =
    val body = js.JSON.stringify(js.Dynamic.literal(text = text))
    val p    = js.Dynamic.global.fetch(
      "/api/todos/add",
      js.Dynamic.literal(
        method  = "POST",
        headers = js.Dynamic.literal(`Content-Type` = "application/json"),
        body    = body
      )
    )
    p.`then`((resp: js.Dynamic) => resp.json())
      .`then` { (data: js.Dynamic) =>
        val id   = data.id.asInstanceOf[String]
        val todo = Todos.Todo(id = id, text = text)
        todos.update(todo :: _)
      }
