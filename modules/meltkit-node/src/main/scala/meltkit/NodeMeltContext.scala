/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.NamedTuple.AnyNamedTuple

import melt.runtime.render.RenderResult

import meltkit.codec.BodyDecoder
import meltkit.codec.BodyEncoder

/** Node.js SSR implementation of [[MeltContext]] with `C = RenderResult`.
  *
  * `render` evaluates the component inside `Router.withPath(requestPath)(...)`,
  * setting `Router.currentPath` for the duration of the synchronous render.
  *
  * This context is used by [[MeltKit]] for Node.js server-side rendering without
  * the http4s adapter. For http4s-based Node.js servers, use
  * [[meltkit.adapter.http4s.Http4sMeltContext]] via [[meltkit-adapter-http4s]].
  *
  * @param params      the decoded path parameters for this request
  * @param requestPath the URL path for this request (e.g. `"/users/42"`)
  * @param queryParams the parsed query string parameters
  * @param bodyDecoder the [[BodyDecoder]] bound to the endpoint's body type `B`
  * @param templateOpt the [[Template]] for SSR rendering; `None` for API-only responses
  * @param manifest    the [[ViteManifest]] used to resolve JS/CSS chunks for SSR
  * @param lang        the `lang` attribute value for the HTML root element
  * @param basePath    the asset base path passed to [[Template.render]]
  */
final class NodeMeltContext[F[_], P <: AnyNamedTuple, B](
  val params:               P,
  val requestPath:          String,
  private val _queryParams: Map[String, List[String]] = Map.empty,
  private val bodyDecoder:  BodyDecoder[B],
  private val templateOpt:  Option[Template]          = None,
  private val manifest:     ViteManifest              = ViteManifest.empty,
  private val lang:         String                    = "en",
  private val basePath:     String                    = "/assets"
) extends MeltContext[F, P, B, RenderResult]:

  override def query(name: String): Option[String] =
    _queryParams.get(name).flatMap(_.headOption)

  override def queryAll(name: String): List[String] =
    _queryParams.getOrElse(name, Nil)

  override def queryParams: Map[String, List[String]] = _queryParams

  /** Evaluates `component` inside `Router.withPath(requestPath)` so that
    * `Router.currentPath` returns the correct path during SSR rendering.
    */
  override def render(component: => RenderResult): PlainResponse =
    render(component, 200)

  override def render(component: => RenderResult, status: StatusCode): PlainResponse =
    templateOpt match
      case None =>
        throw new IllegalStateException(
          "ctx.render() requires a NodeMeltContext initialized with a Template."
        )
      case Some(template) =>
        val result = Router.withPath(requestPath)(component)
        val html   = template.render(result, manifest, title = "", lang = lang, basePath = basePath, vars = Map.empty)
        PlainResponse(status, "text/html; charset=utf-8", html)

  override def ok[A: BodyEncoder](value: A): PlainResponse =
    PlainResponse(200, "application/json", summon[BodyEncoder[A]].encode(value))

  override def created[A: BodyEncoder](value: A): PlainResponse =
    PlainResponse(201, "application/json", summon[BodyEncoder[A]].encode(value))

  override def noContent: PlainResponse = Response.noContent

  override def text(value: String): PlainResponse = Response.text(value)

  override def json(value: String): PlainResponse = Response.json(value)

  override def badRequest(err: BodyError): BadRequest = Response.badRequest(err.message)

  override def redirect(path: String, permanent: Boolean = false): PlainResponse =
    Response.redirect(path, permanent)

  override def notFound(message: String = "Not Found"): NotFound =
    Response.notFound(message)
