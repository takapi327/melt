/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package components

import org.scalajs.dom

import melt.runtime.{ Cleanup, Style, Var }

import models.Todo

/** TodoList component — renders a list of Todo items with toggle and remove.
  *
  * Implemented as a plain Scala object because dynamic list rendering
  * (Bind.list) is a Phase 6 feature. The list is rebuilt on each change
  * by subscribing to the items Var.
  */
object TodoList:

  private val scopeId = "melt-todolist"

  private val css =
    ".todo-list.melt-todolist { list-style: none; padding: 0; }" +
      " li.melt-todolist { display: flex; align-items: center; gap: 0.5em; padding: 0.5em 0; border-bottom: 1px solid #eee; }" +
      " li.done.melt-todolist span { text-decoration: line-through; color: #999; }" +
      " li.melt-todolist span { flex: 1; }" +
      " li.melt-todolist button { background: none; border: none; color: #c00; cursor: pointer; font-size: 1.2em; }"

  case class Props(items: Var[List[Todo]], onToggle: Int => Unit, onRemove: Int => Unit)

  def create(props: Props): dom.Element =
    Cleanup.pushScope()
    Style.inject(scopeId, css)

    val ul = dom.document.createElement("ul")
    ul.classList.add(scopeId)
    ul.classList.add("todo-list")

    def rebuild(todos: List[Todo]): Unit =
      // Clear existing children
      while ul.firstChild != null do ul.removeChild(ul.firstChild)
      // Build new list
      todos.foreach { item =>
        val li = dom.document.createElement("li")
        li.classList.add(scopeId)
        if item.done then li.classList.add("done")

        val checkbox = dom.document.createElement("input").asInstanceOf[dom.html.Input]
        checkbox.setAttribute("type", "checkbox")
        checkbox.checked = item.done
        checkbox.addEventListener("change", (_: dom.Event) => props.onToggle(item.id))
        li.appendChild(checkbox)

        val span = dom.document.createElement("span")
        span.textContent = item.text
        li.appendChild(span)

        val btn = dom.document.createElement("button")
        btn.textContent = "\u00d7"
        btn.addEventListener("click", (_: dom.Event) => props.onRemove(item.id))
        li.appendChild(btn)

        ul.appendChild(li)
      }

    // Initial render + subscribe to changes
    rebuild(props.items.now())
    val cancel = props.items.subscribe(rebuild)
    Cleanup.register(cancel)

    val cleanups = Cleanup.popScope()
    ul
