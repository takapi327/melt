/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.NotGiven
import scala.NamedTuple.AnyNamedTuple

import melt.runtime.render.RenderResult

import meltkit.codec.{ BodyDecoder, BodyEncoder, FormDataDecoder }
import meltkit.exceptions.BodyDecodeException

/** Node.js SSR implementation of [[ServerMeltContext]] fixed to `Future`.
  *
  * `render` evaluates the component inside `Router.withPath(requestPath)(...)`,
  * setting `Router.currentPath` for the duration of the synchronous render.
  *
  * For `IO`-based Node.js servers, use
  * [[meltkit.adapter.http4s.Http4sMeltContext]] via `meltkit-adapter-http4s`.
  */
final class NodeMeltContext[P <: AnyNamedTuple, B](
  val params:               P,
  val requestPath:          String,
  private val _queryParams: Map[String, List[String]]             = Map.empty,
  private val bodyDecoder:  BodyDecoder[B],
  private val rawBody:      Future[String],
  private val rawHeaders:   Map[String, String]                   = Map.empty,
  private val rawCookies:   Map[String, String]                   = Map.empty,
  private val templateOpt:  Option[Template]                      = None,
  private val manifest:     ViteManifest                          = ViteManifest.empty,
  private val lang:         String                                = "en",
  private val basePath:     String                                = "",
  override val locals:      Locals                                = new Locals(),
  private val nonce:        Option[String]                        = None,
  private val defaultTitle: String                                = "",
  private val app:          Option[ServerMeltKitPlatform[Future]] = None
)(using ec: ExecutionContext)
  extends ServerMeltContext[Future, P, B, RenderResult]:

  override def query(name: String): Option[String] =
    _queryParams.get(name).flatMap(_.headOption)

  override def queryAll(name: String): List[String] =
    _queryParams.getOrElse(name, Nil)

  override def queryParams: Map[String, List[String]] = _queryParams

  // ── ServerMeltContext: body ────────────────────────────────────────────

  override val body: RequestBody[Future, B] = new RequestBody[Future, B]:

    def text: Future[String] = rawBody

    def form: Future[Either[BodyError, FormData]] =
      rawBody.map(FormData.parse)

    def form[A](using fdd: FormDataDecoder[A]): Future[Either[BodyError, A]] =
      rawBody.map(raw => FormData.parse(raw).flatMap(fdd.decode))

    def json[A](using dec: BodyDecoder[A]): Future[Either[BodyError, A]] =
      rawBody.map(dec.decode)

    def decode(using NotGiven[B =:= Unit]): Future[Either[BodyError, B]] =
      rawBody.map(bodyDecoder.decode)

    def decodeOrBadRequest(using NotGiven[B =:= Unit]): Future[B] =
      rawBody.map { raw =>
        bodyDecoder.decode(raw) match
          case Right(b)  => b
          case Left(err) => throw BodyDecodeException(err)
      }

  // ── ServerMeltContext: cookies / headers ───────────────────────────────

  override def cookie(name: String): Option[String] = rawCookies.get(name)

  override def cookies: Map[String, String] = rawCookies

  override def header(name: String): Option[String] = rawHeaders.get(name.toLowerCase)

  override def headers: Map[String, String] = rawHeaders

  // ── MeltContext: render ────────────────────────────────────────────────

  override def render(component: => RenderResult): PlainResponse =
    render(component, 200)

  override def render(component: => RenderResult, status: StatusCode): PlainResponse =
    templateOpt match
      case None           => throw missingTemplate
      case Some(template) => composeResponse(template, Router.withPath(requestPath)(component), status)

  /** Blocking async SSR: resolve every `<melt:await>` boundary in-process (via the
    * app's server-function registry) and splice the resolved branches over their
    * markers, seeding the results for hydration. A page with no boundary is just
    * [[render]] lifted into `Future`. */
  override def renderAsync(component: => RenderResult): Future[Response] =
    templateOpt match
      case None           => throw missingTemplate
      case Some(template) =>
        app match
          case None =>
            Future.successful(composeResponse(template, Router.withPath(requestPath)(component), 200))
          case Some(a) =>
            val resolve =
              a.resolveQueryFn(this.asInstanceOf[ServerMeltContext[Future, PathSpec.Empty, Any, RenderResult]])
            // Re-establish the request path around each deferred branch render.
            val wrap = new SsrRenderScope.BranchWrap:
              def apply(thunk: => RenderResult): RenderResult = Router.withPath(requestPath)(thunk)
            val (result, scope) =
              SsrRenderScope.withScope[Future, RenderResult](resolve, wrap)(Router.withPath(requestPath)(component))
            if !scope.nonEmpty then Future.successful(composeResponse(template, result, 200))
            else
              scope.resolveAll.map { resolved =>
                composeResponse(template, result.copy(body = SsrRenderScope.spliceAndSeed(result.body, resolved)), 200)
              }

  private def missingTemplate: IllegalStateException =
    new IllegalStateException("ctx.render() requires a NodeMeltContext initialized with a Template.")

  /** Resolves `.melt` import tags and composes the page HTML via the [[Template]]. */
  private def composeResponse(template: Template, result: RenderResult, status: StatusCode): PlainResponse =
    val augmented =
      if result.imports.isEmpty then result
      else
        val tags    = ImportTagResolver.resolveTags(result.imports, manifest, basePath, nonce)
        val newHead = if result.head.isEmpty then tags else s"$tags\n${ result.head }"
        result.copy(head = newHead)
    val html = template.render(
      augmented,
      manifest,
      title    = defaultTitle,
      lang     = lang,
      basePath = basePath,
      vars     = Map.empty,
      nonce    = nonce
    )
    PlainResponse(status, "text/html; charset=utf-8", html)

  // ── MeltContext: response builders ─────────────────────────────────────

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
