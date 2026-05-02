/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.http4s

import scala.util.NotGiven
import scala.NamedTuple.AnyNamedTuple

import melt.runtime.render.RenderResult

import cats.effect.Concurrent
import cats.syntax.all.*
import meltkit.*
import meltkit.codec.BodyDecoder
import meltkit.codec.BodyEncoder
import org.http4s.headers.Cookie as Http4sCookieHeader
import org.http4s.Request

/** An exception raised by [[RequestBody.decodeOrBadRequest]] when body
  * decoding fails. [[Http4sAdapter]] catches this and converts it to a 400
  * Bad Request response.
  */
private[http4s] final class BodyDecodeException(val error: BodyError) extends RuntimeException(error.message)

/** http4s implementation of [[ServerMeltContext]] for SSR (JVM and Node.js).
  *
  * `C` is fixed to [[melt.runtime.render.RenderResult]].
  * `render` evaluates the component inside `Router.withPath(requestPath)` so that
  * `Router.currentPath` returns the correct value during SSR rendering.
  *
  * On JVM, `Router` is backed by `ThreadLocal`; on Node.js it is backed by
  * `AsyncLocalStorage` — both are resolved via the platform-specific dependency
  * wired in `build.sbt` (`.jvmConfigure` / `.jsConfigure`).
  *
  * @param params      the decoded path parameters for this request
  * @param request     the underlying http4s [[Request]]
  * @param bodyDecoder the [[BodyDecoder]] bound to the endpoint's body type `B`
  * @param templateOpt the [[Template]] for SSR rendering; `None` when using the
  *                    API-only `Http4sAdapter.routes(app)`
  * @param manifest    the [[ViteManifest]] used to resolve JS/CSS chunks for SSR
  * @param lang        the `lang` attribute value for the HTML root element
  * @param basePath    the asset base path passed to [[Template.render]]
  */
final class Http4sMeltContext[F[_]: Concurrent, P <: AnyNamedTuple, B](
  val params:              P,
  private val request:     Request[F],
  private val bodyDecoder: BodyDecoder[B],
  private val templateOpt: Option[Template] = None,
  private val manifest:    ViteManifest     = ViteManifest.empty,
  private val lang:        String           = "en",
  private val basePath:    String           = "/assets"
) extends ServerMeltContext[F, P, B, RenderResult]:

  private val cachedBody: F[String] =
    Concurrent[F]
      .memoize(
        request.body.through(fs2.text.utf8.decode).compile.string
      )
      .flatten

  override def requestPath: String = request.uri.path.renderString

  override def query(name: String): Option[String] =
    request.uri.query.params.get(name)

  override val body: RequestBody[F, B] = new RequestBody[F, B]:

    def text: F[String] = cachedBody

    def form: F[Either[BodyError, FormData]] =
      cachedBody.map(FormData.parse)

    def form[A](using fdd: codec.FormDataDecoder[A]): F[Either[BodyError, A]] =
      cachedBody.map(raw => FormData.parse(raw).flatMap(fdd.decode))

    def json[A](using dec: BodyDecoder[A]): F[Either[BodyError, A]] =
      cachedBody.map(dec.decode)

    def decode(using NotGiven[B =:= Unit]): F[Either[BodyError, B]] =
      cachedBody.map(bodyDecoder.decode)

    def decodeOrBadRequest(using NotGiven[B =:= Unit]): F[B] =
      decode.flatMap {
        case Right(b)  => Concurrent[F].pure(b)
        case Left(err) => Concurrent[F].raiseError(BodyDecodeException(err))
      }

  // Parse the Cookie header once per request (lazy to avoid unnecessary work).
  // For Recurring headers, get[H] returns Option[NonEmptyList[H]]; each Cookie
  // wraps NonEmptyList[RequestCookie], so we flatten to collect all pairs.
  // Duplicate names resolve to the last value, which is acceptable per RFC 6265.
  private lazy val parsedCookies: Map[String, String] =
    request.headers.get[Http4sCookieHeader] match
      case None         => Map.empty
      case Some(cookie) => cookie.values.toList.map(c => c.name -> c.content).toMap

  override def cookie(name: String): Option[String] = parsedCookies.get(name)

  override def cookies: Map[String, String] = parsedCookies

  // Parse all request headers once per request (lazy to avoid unnecessary work).
  // Keys are normalized to lowercase; duplicate header names are joined with ", "
  // per RFC 7230 §3.2.2.
  private lazy val parsedHeaders: Map[String, String] =
    request.headers.headers
      .groupBy(_.name.toString.toLowerCase)
      .map { case (name, vals) => name -> vals.map(_.value).mkString(", ") }

  override def header(name: String): Option[String] = parsedHeaders.get(name.toLowerCase)

  override def headers: Map[String, String] = parsedHeaders

  /** Evaluates `component` inside `Router.withPath(requestPath)` so that
    * `Router.currentPath` returns the correct path during SSR rendering.
    */
  override def render(component: => RenderResult): PlainResponse =
    render(component, 200)

  override def render(component: => RenderResult, status: StatusCode): PlainResponse =
    templateOpt match
      case None =>
        throw new IllegalStateException(
          "ctx.render() requires an Http4sAdapter initialized with a Template. " +
            "Use `Http4sAdapter(app, template, manifest).routes` instead of `Http4sAdapter.routes(app)`."
        )
      case Some(template) =>
        val result = Router.withPath(requestPath)(component)
        val html   = template.render(result, manifest, title = "", lang = lang, basePath = basePath, vars = Map.empty)
        PlainResponse(status, "text/html; charset=utf-8", html)

  override def ok[A: BodyEncoder](value: A): PlainResponse =
    PlainResponse(200, "application/json", summon[BodyEncoder[A]].encode(value))

  override def created[A: BodyEncoder](value: A): PlainResponse =
    PlainResponse(201, "application/json", summon[BodyEncoder[A]].encode(value))

  override def noContent: PlainResponse =
    Response.noContent

  override def text(value: String): PlainResponse =
    Response.text(value)

  override def json(value: String): PlainResponse =
    Response.json(value)

  override def badRequest(err: BodyError): BadRequest =
    Response.badRequest(err.message)

  override def redirect(path: String, permanent: Boolean = false): PlainResponse =
    Response.redirect(path, permanent)

  override def notFound(message: String = "Not Found"): NotFound =
    Response.notFound(message)
