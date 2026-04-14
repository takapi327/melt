/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import org.scalajs.dom

/** Document-level form reset coordinator.
  *
  * Mirrors Svelte's `add_form_reset_listener`: installs a single capture-phase
  * `reset` listener on the document and dispatches to per-element handlers.
  *
  * After the DOM has reset (one microtask via `Promise.resolve().then(...)`),
  * each registered handler is called so the associated `Var` can be synced
  * back from the reset DOM state.
  *
  * Usage: called internally by `Bind.textareaValue`, `Bind.selectValue`, and
  * `Bind.selectMultipleValue`. Not intended for direct use.
  */
private[runtime] object FormReset:

  private val handlers = scalajs.js.Dictionary.empty[() => Unit]
  private var installed = false
  private var counter   = 0

  def register(el: dom.Element, handler: () => Unit): Unit =
    val key = nextKey()
    el.asInstanceOf[scalajs.js.Dynamic].updateDynamic("__meltResetKey")(key)
    handlers(key) = handler
    ensureListener()

  def unregister(el: dom.Element): Unit =
    val key = el.asInstanceOf[scalajs.js.Dynamic].__meltResetKey
    if !scalajs.js.isUndefined(key) then handlers -= key.asInstanceOf[String]

  private def ensureListener(): Unit =
    if !installed then
      installed = true
      val listener: scalajs.js.Function1[dom.Event, Unit] = (e: dom.Event) =>
        if !e.defaultPrevented then
          // Use a microtask (Promise.resolve().then) so DOM properties have
          // already been reset to their default values before we read them.
          // This matches Svelte's add_form_reset_listener behaviour exactly.
          Future.unit.foreach { _ =>
            val form = e.target.asInstanceOf[dom.html.Form]
            (0 until form.elements.length).foreach { i =>
              val el  = form.elements(i).asInstanceOf[scalajs.js.Dynamic]
              val key = el.__meltResetKey
              if !scalajs.js.isUndefined(key) then
                handlers.get(key.asInstanceOf[String]).foreach(_())
            }
          }
      dom.document.addEventListener("reset", listener, true) // capture phase

  private def nextKey(): String =
    counter += 1
    s"__meltReset_$counter"
