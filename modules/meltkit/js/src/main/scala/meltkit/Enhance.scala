/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.concurrent.ExecutionContext
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits.given
import scala.util.{ Failure, Success }

import org.scalajs.dom

import melt.runtime.dom.{ Conversions, Element }
import melt.runtime.forms.FormHandle
import melt.runtime.json.SimpleJson
import melt.runtime.Action

/** The `use:enhance={form}` action (client implementation).
  *
  * Intercepts the native form submit and replays it as a `fetch` that returns an
  * [[ActionResult]] JSON envelope (via the `x-melt-enhance` header), then updates
  * `form` in place without a full-page reload. Removing `use:enhance` leaves a
  * plain `<form method="post">` that still works with JavaScript disabled — the
  * progressive-enhancement floor.
  *
  * Lives in `meltkit` (crossProject) so a `.melt` component that `import
  * meltkit.enhance` compiles for both SSR (JVM, no-op — `use:` is client-only)
  * and hydration (JS, this implementation). Redirects use a full navigation
  * (`window.location.assign`) so this depends only on the DOM, not the router.
  *
  * Import it into a `.melt` script: `import meltkit.enhance`.
  */
val enhance: Action[FormHandle] = Enhance

private[meltkit] object Enhance extends Action[FormHandle]:

  private given ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.queue

  def apply(el: Element, form: FormHandle): () => Unit =
    val formEl = Conversions.unwrap(el).asInstanceOf[dom.html.Form]
    val listener: js.Function1[dom.Event, Unit] = (e: dom.Event) =>
      e.preventDefault()
      if form.beforeSubmit() then submit(formEl, form, submitterOf(e))
    formEl.addEventListener("submit", listener)
    () => formEl.removeEventListener("submit", listener)

  /** The element that triggered the submit (e.g. a `<button formaction="?/save">`),
    * from the `SubmitEvent`. `None` for programmatic or non-button submits.
    */
  private def submitterOf(e: dom.Event): Option[dom.Element] =
    val s = e.asInstanceOf[js.Dynamic].submitter
    if js.isUndefined(s) || s == null then None else Some(s.asInstanceOf[dom.Element])

  private def submit(formEl: dom.html.Form, form: FormHandle, submitter: Option[dom.Element]): Unit =
    form.submitting.set(true)
    val url  = actionUrl(formEl, submitter)
    val init = new dom.RequestInit {}
    init.method  = dom.HttpMethod.POST
    init.body    = serialize(formEl)
    init.headers = js.Dictionary(
      "x-melt-enhance" -> "true",
      "content-type"   -> "application/x-www-form-urlencoded",
      "accept"         -> "application/json"
    )
    val result =
      for
        resp <- dom.fetch(url, init).toFuture
        text <- resp.text().toFuture
      yield (resp, text)

    result.onComplete { outcome =>
      // Always clear `submitting` so the form is never stuck disabled, even on a
      // network error or a non-2xx response.
      form.submitting.set(false)
      outcome match
        case Success((resp, text)) =>
          if resp.status >= 200 && resp.status < 300 then dispatch(text, url, formEl, form)
          else
            // A non-2xx response is not an enhance envelope (e.g. 400 "Unknown
            // form action" when a `formaction="?/name"` did not reach the server).
            // Surface it instead of silently doing nothing.
            dom.console.error(
              s"[melt enhance] POST $url failed: ${ resp.status } ${ resp.statusText }. Body: $text"
            )
        case Failure(err) =>
          dom.console.error(s"[melt enhance] POST $url errored: ${ err.getMessage }")
    }

  /** Serialise the form to `application/x-www-form-urlencoded` using the
    * browser's own `URLSearchParams(FormData)` (files are out of scope for P1).
    */
  private def serialize(formEl: dom.html.Form): String =
    val fd  = js.Dynamic.newInstance(js.Dynamic.global.FormData)(formEl)
    val usp = js.Dynamic.newInstance(js.Dynamic.global.URLSearchParams)(fd)
    usp.applyDynamic("toString")().asInstanceOf[String]

  /** Same-origin action path (+query, preserving `?/name`) for the fetch.
    *
    * A `<button formaction="?/save">` overrides the form's action for that submit;
    * the submitter's resolved `formAction` reflects that (falling back to the
    * form's action when the button has no `formaction`), so named actions work
    * with multiple submit buttons on one form.
    */
  private def actionUrl(formEl: dom.html.Form, submitter: Option[dom.Element]): String =
    val a = submitter
      .map(_.asInstanceOf[js.Dynamic].formAction.asInstanceOf[String])
      .filter(s => s != null && s.nonEmpty)
      .getOrElse(formEl.action)
    if a == null || a.isEmpty then dom.window.location.pathname + dom.window.location.search
    else
      val u = new dom.URL(a)
      u.pathname + u.search

  private def dispatch(text: String, url: String, formEl: dom.html.Form, form: FormHandle): Unit =
    SimpleJson.parse(text) match
      case obj: SimpleJson.JsonValue.Obj =>
        obj.fields.get("type") match
          case Some(SimpleJson.JsonValue.Str("redirect")) =>
            form.afterResult(
              "redirect",
              () =>
                obj.fields
                  .get("location")
                  .collect { case SimpleJson.JsonValue.Str(loc) => loc }
                  .foreach(dom.window.location.assign)
            )
          case Some(SimpleJson.JsonValue.Str("success")) =>
            form.afterResult(
              "success",
              () =>
                obj.fields.get("data").foreach(form.applyResultData)
                formEl.reset()
                resetFocus(formEl)
            )
          case Some(SimpleJson.JsonValue.Str("failure")) =>
            form.afterResult(
              "failure",
              () =>
                obj.fields.get("data").foreach(form.applyResultData)
                resetFocus(formEl)
            )
          case other =>
            dom.console.error(
              s"[melt enhance] POST $url returned an unrecognised result type ($other). Body: $text"
            )
      case _ =>
        dom.console.error(s"[melt enhance] POST $url did not return a JSON object. Body: $text")

  /** a11y: after an intercepted submit, move focus to the first flagged error, or
    * to the form itself, mirroring native post-submit focus behaviour so screen
    * reader users are not left without context.
    */
  private def resetFocus(formEl: dom.html.Form): Unit =
    val invalid = formEl.querySelector("[aria-invalid='true'], .error")
    if invalid != null then invalid.asInstanceOf[dom.html.Element].focus()
    else formEl.focus()
