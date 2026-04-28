/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import org.scalajs.dom

import scala.NamedTuple.AnyNamedTuple

import melt.runtime.Mount

import meltkit.codec.BodyDecoder
import meltkit.codec.BodyEncoder

/** Browser implementation of [[MeltContext]].
  *
  * Created by [[BrowserAdapter]] for each URL change. Route handlers receive
  * this context and build responses using the standard `ctx.*` helpers.
  *
  * `ctx.render(Component())` mounts the component into `rootEl`, replacing any
  * previously mounted content.
  *
  * Browser navigation routes carry no request body; use the [[Fetch]] client
  * for API calls from within components. Body access is available only on the
  * server side via [[ServerMeltContext]].
  *
  * @param params      the decoded path parameters extracted from the URL
  * @param bodyDecoder the [[BodyDecoder]] bound to the endpoint's body type `B`
  * @param rootEl      the DOM element used as the mount target for components
  */
final class BrowserMeltContext[F[_], P <: AnyNamedTuple, B](
  val params:              P,
  private val bodyDecoder: BodyDecoder[B],
  private val rootEl:      dom.Element
) extends MeltContext[F, P, B]:

  override def requestPath: String = dom.window.location.pathname

  override def query(name: String): Option[String] =
    val search = dom.window.location.search
    if search.isEmpty || search == "?" then None
    else
      search.drop(1).split("&").collectFirst {
        case kv if kv.startsWith(s"$name=") => kv.drop(name.length + 1)
      }

  /** Mounts the component into the root DOM element and returns a no-content response.
    *
    * Clears `rootEl.innerHTML` before mounting so that only the new component
    * is visible. The [[Component]] wraps the `dom.Element` returned by the
    * `.melt`-compiled component.
    *
    * {{{
    * // shared route handler
    * app.get("todos") { ctx => F.pure(ctx.melt(TodoPage())) }
    * }}}
    */
  override def render(component: Component): PlainResponse =
    val element = component.asInstanceOf[dom.Element]
    rootEl.innerHTML = ""
    Mount(rootEl, element)
    Response.noContent

  override def ok[A: BodyEncoder](value: A): PlainResponse =
    PlainResponse(200, "application/json", summon[BodyEncoder[A]].encode(value))

  override def created[A: BodyEncoder](value: A): PlainResponse =
    PlainResponse(201, "application/json", summon[BodyEncoder[A]].encode(value))

  override def noContent: PlainResponse = Response.noContent

  override def text(value: String): PlainResponse = Response.text(value)

  override def json(value: String): PlainResponse = Response.json(value)

  override def badRequest(err: BodyError): BadRequest = Response.badRequest(err.message)

  /** Navigates to `path` using [[BrowserRouter]] and returns a no-content response.
    *
    * The URL change fires a `popstate` event which [[BrowserAdapter]] intercepts
    * to dispatch the new route.
    */
  override def redirect(path: String, permanent: Boolean = false): PlainResponse =
    Router.navigate(path)
    Response.noContent

  override def notFound(message: String = "Not Found"): NotFound = Response.notFound(message)
