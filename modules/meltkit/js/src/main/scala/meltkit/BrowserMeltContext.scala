/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.NamedTuple.AnyNamedTuple

import org.scalajs.dom

import melt.runtime.Mount

import meltkit.codec.BodyDecoder
import meltkit.codec.BodyEncoder

/** Browser implementation of [[MeltContext]].
  *
  * Created by [[BrowserAdapter]] for each URL change. Route handlers receive
  * this context and build responses using the standard `ctx.*` helpers.
  *
  * `ctx.render(Component())` replaces the outlet element's content with the
  * given component. In [[BrowserAdapter.mount]] mode the outlet is `rootEl`
  * itself; in [[BrowserAdapter.mountWithShell]] mode it is the
  * `[data-melt-outlet]` element within the shell.
  *
  * Browser navigation routes carry no request body; use the [[Fetch]] client
  * for API calls from within components. Body access is available only on the
  * server side via [[ServerMeltContext]].
  *
  * @param params      the decoded path parameters extracted from the URL
  * @param bodyDecoder the [[BodyDecoder]] bound to the endpoint's body type `B`
  * @param outletEl    the DOM element to render into; either `rootEl` (full-replace
  *                    mode) or the `[data-melt-outlet]` element (shell mode)
  */
final class BrowserMeltContext[F[_], P <: AnyNamedTuple, B](
  val params:              P,
  private val bodyDecoder: BodyDecoder[B],
  private val outletEl:    dom.Element
) extends MeltContext[F, P, B]:

  override def requestPath: String = dom.window.location.pathname

  override def query(name: String): Option[String] =
    Option(new dom.URLSearchParams(dom.window.location.search).get(name))

  /** Replaces the outlet element's content with the given component.
    *
    * Clears `outletEl.innerHTML` before mounting so that only the new
    * component is visible. The [[Component]] wraps the `dom.Element`
    * returned by the `.melt`-compiled component.
    *
    * {{{
    * app.get("todos") { ctx => ctx.render(TodoPage()) }
    * }}}
    */
  override def render(component: Component): PlainResponse =
    outletEl.innerHTML = ""
    Mount(outletEl, Component.unwrap(component))
    Response.noContent

  override def ok[A: BodyEncoder](value: A): PlainResponse =
    PlainResponse(200, "application/json", summon[BodyEncoder[A]].encode(value))

  override def created[A: BodyEncoder](value: A): PlainResponse =
    PlainResponse(201, "application/json", summon[BodyEncoder[A]].encode(value))

  override def noContent: PlainResponse = Response.noContent

  override def text(value: String): PlainResponse = Response.text(value)

  override def json(value: String): PlainResponse = Response.json(value)

  override def badRequest(err: BodyError): BadRequest = Response.badRequest(err.message)

  /** Navigates to `path` using [[Router]] and returns a no-content response.
    *
    * The URL change fires a `popstate` event which [[BrowserAdapter]] intercepts
    * to dispatch the new route.
    */
  override def redirect(path: String, permanent: Boolean = false): PlainResponse =
    Response.requireRelativePath(path)
    Router.navigate(path)
    Response.noContent

  override def notFound(message: String = "Not Found"): NotFound = Response.notFound(message)
