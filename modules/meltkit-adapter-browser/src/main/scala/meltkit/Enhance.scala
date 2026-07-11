/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.concurrent.ExecutionContext
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits.given

import org.scalajs.dom

import melt.runtime.Action
import melt.runtime.dom.{ Conversions, Element }
import melt.runtime.forms.FormHandle
import melt.runtime.json.SimpleJson

/** The `use:enhance={form}` action.
  *
  * Intercepts the native form submit and replays it as a `fetch` that returns an
  * [[ActionResult]] JSON envelope (via the `x-melt-enhance` header), then updates
  * `form` in place without a full-page reload. Removing `use:enhance` leaves a
  * plain `<form method="post">` that still works with JavaScript disabled — the
  * progressive-enhancement floor.
  *
  * Import it into a `.melt` script: `import meltkit.enhance`.
  */
val enhance: Action[FormHandle] = Enhance

private[meltkit] object Enhance extends Action[FormHandle]:

  private given ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.queue

  def apply(el: Element, form: FormHandle): () => Unit =
    val formEl = Conversions.unwrap(el).asInstanceOf[dom.html.Form]
    val listener: js.Function1[dom.Event, Unit] = { (e: dom.Event) =>
      e.preventDefault()
      submit(formEl, form)
    }
    formEl.addEventListener("submit", listener)
    () => formEl.removeEventListener("submit", listener)

  private def submit(formEl: dom.html.Form, form: FormHandle): Unit =
    form.submitting.set(true)
    val init = new dom.RequestInit {}
    init.method = dom.HttpMethod.POST
    init.body = serialize(formEl)
    init.headers = js.Dictionary(
      "x-melt-enhance" -> "true",
      "content-type"   -> "application/x-www-form-urlencoded",
      "accept"         -> "application/json"
    )
    dom
      .fetch(actionUrl(formEl), init)
      .toFuture
      .flatMap(_.text().toFuture)
      .foreach { text =>
        dispatch(text, formEl, form)
        form.submitting.set(false)
      }

  /** Serialise the form to `application/x-www-form-urlencoded` using the
    * browser's own `URLSearchParams(FormData)` (files are out of scope for P1).
    */
  private def serialize(formEl: dom.html.Form): String =
    val fd  = js.Dynamic.newInstance(js.Dynamic.global.FormData)(formEl)
    val usp = js.Dynamic.newInstance(js.Dynamic.global.URLSearchParams)(fd)
    usp.applyDynamic("toString")().asInstanceOf[String]

  /** Same-origin action path (+query, preserving `?/name`) for the fetch. */
  private def actionUrl(formEl: dom.html.Form): String =
    val a = formEl.action
    if a == null || a.isEmpty then dom.window.location.pathname + dom.window.location.search
    else
      val u = new dom.URL(a)
      u.pathname + u.search

  private def dispatch(text: String, formEl: dom.html.Form, form: FormHandle): Unit =
    SimpleJson.parse(text) match
      case obj: SimpleJson.JsonValue.Obj =>
        obj.fields.get("type") match
          case Some(SimpleJson.JsonValue.Str("redirect")) =>
            obj.fields.get("location").collect { case SimpleJson.JsonValue.Str(loc) => loc }.foreach(Router.navigate)
          case Some(SimpleJson.JsonValue.Str("success")) =>
            obj.fields.get("data").foreach(form.applyResultData)
            formEl.reset()
            resetFocus(formEl)
          case Some(SimpleJson.JsonValue.Str("failure")) =>
            obj.fields.get("data").foreach(form.applyResultData)
            resetFocus(formEl)
          case _ => ()
      case _ => ()

  /** a11y: after an intercepted submit, move focus to the first flagged error, or
    * to the form itself, mirroring native post-submit focus behaviour so screen
    * reader users are not left without context.
    */
  private def resetFocus(formEl: dom.html.Form): Unit =
    val invalid = formEl.querySelector("[aria-invalid='true'], .error")
    if invalid != null then invalid.asInstanceOf[dom.html.Element].focus()
    else formEl.focus()
