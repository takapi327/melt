/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.ziohttp

import scala.NamedTuple.AnyNamedTuple
import scala.util.NotGiven

import zio.stream.ZStream
import zio.{ Ref, ZIO }
import zio.http.Request

import melt.runtime.render.RenderResult

import meltkit.*
import meltkit.codec.{ BodyDecoder, BodyEncoder, FormDataDecoder }
import meltkit.exceptions.BodyDecodeException

import ZioInstances.ZTask
import ZioInstances.given

/** zio-http implementation of [[meltkit.ServerMeltContext]].
  *
  * `render` evaluates the component inside `Router.withPath(requestPath)` so that
  * `Router.currentPath` returns the right value during SSR. The JVM `Router` is backed by a
  * `ThreadLocal`, and `withPath` only wraps a synchronous by-name, so a ZIO fiber moving
  * between threads across effect boundaries does not lose it.
  *
  * @param params      decoded path parameters for this request
  * @param request     the underlying zio-http request
  * @param bodyDecoder the decoder bound to the endpoint's body type `B`
  * @param _locals     per-request [[meltkit.Locals]], shared with hooks
  * @param templateOpt the SSR shell; `None` for the API-only entry point, where `render` fails
  * @param manifest    resolves JS/CSS chunks for the rendered page
  * @param lang        `lang` attribute for the HTML root element
  * @param basePath    deployment root path for assets
  * @param nonce       per-request CSP nonce, when CSP is configured
  * @param app         the router, used to wrap layouts around the page
  * @param routerEntry hydration entry module for router-driven pages
  */
final class ZioHttpMeltContext[R, P <: AnyNamedTuple, B](
  val params:              P,
  private val request:     Request,
  private val bodyDecoder: BodyDecoder[B],
  private val _locals:     Locals,
  private val templateOpt: Option[Template] = None,
  private val manifest:    ViteManifest = ViteManifest.empty,
  private val lang:        String = "en",
  private val basePath:    String = "",
  private val nonce:       Option[String] = None,
  private val app:         Option[ServerMeltKitPlatform[ZTask[R]]] = None,
  private val routerEntry: Option[String] = None
) extends ServerMeltContext[ZTask[R], P, B, RenderResult]:

  def requestPath: String = request.path.encode

  def locals: Locals = _locals

  def query(name: String): Option[String] = request.queryParam(name)

  def queryAll(name: String): List[String] = request.queryParameters.getAll(name).toList

  def queryParams: Map[String, List[String]] =
    request.queryParameters.map.map((k, v) => k -> v.toList).toMap

  override def header(name: String): Option[String] = request.headers.get(name)

  override def headers: Map[String, String] =
    request.headers.map(h => h.headerName -> h.renderedValue).toMap

  override def cookie(name: String): Option[String] =
    request.cookies.find(_.name == name).map(_.content)

  override def cookies: Map[String, String] =
    request.cookies.map(c => c.name -> c.content).toMap

  def body: RequestBody[ZTask[R], B] = new RequestBody[ZTask[R], B]:

    def text: ZIO[R, Throwable, String] = request.body.asString

    def form: ZIO[R, Throwable, Either[BodyError, FormData]] =
      text.map(FormData.parse)

    def form[A](using dec: FormDataDecoder[A]): ZIO[R, Throwable, Either[BodyError, A]] =
      form.map(_.flatMap(dec.decode))

    def json[A](using dec: BodyDecoder[A]): ZIO[R, Throwable, Either[BodyError, A]] =
      text.map(dec.decode)

    def decode(using NotGiven[B =:= Unit]): ZIO[R, Throwable, Either[BodyError, B]] =
      text.map(bodyDecoder.decode)

    def decodeOrBadRequest(using NotGiven[B =:= Unit]): ZIO[R, Throwable, B] =
      decode.flatMap {
        case Right(b) => ZIO.succeed(b)
        case Left(e)  => ZIO.fail(BodyDecodeException(e))
      }

  // Response constructors: transport-neutral, so they delegate straight to `meltkit.Response`.

  def ok[A: BodyEncoder](value: A): PlainResponse =
    PlainResponse(200, "application/json", summon[BodyEncoder[A]].encode(value))

  def created[A: BodyEncoder](value: A): PlainResponse =
    PlainResponse(201, "application/json", summon[BodyEncoder[A]].encode(value))

  def noContent: PlainResponse = Response.noContent

  def text(value: String): PlainResponse = Response.text(value)

  def json(value: String): PlainResponse = Response.json(value)

  def notFound(message: String): NotFound = Response.notFound(message)

  def badRequest(err: BodyError): BadRequest = Response.badRequest(err.message)

  def redirect(path: String, permanent: Boolean): PlainResponse = Response.redirect(path, permanent)

  def render(component: => RenderResult): PlainResponse =
    templateOpt match
      case None           => throw missingTemplate
      case Some(template) =>
        val composed = Router.withPath(requestPath) {
          app match
            case Some(a) => a.wrapLayouts(requestPath, () => component)
            case None    => component
        }
        composeResponse(template, composed, 200)

  /** Blocking async SSR: resolve every `<melt:await>` boundary, then splice the resolved
    * branches over their markers and seed them for hydration. One response, no chunking.
    */
  override def renderAsync(component: => RenderResult): ZIO[R, Throwable, Response] =
    templateOpt match
      case None           => ZIO.fail(missingTemplate)
      case Some(template) =>
        app match
          case None    => ZIO.succeed(composeResponse(template, Router.withPath(requestPath)(component), 200))
          case Some(a) =>
            val (result, scope) = renderInScope(a, component)
            if !scope.nonEmpty then ZIO.succeed(composeResponse(template, result, 200))
            else
              scope.resolveAll.map { resolved =>
                val body = SsrRenderScope.spliceAndSeed(result.body, resolved)
                composeResponse(template, result.copy(body = body), 200)
              }

  /** Streaming async SSR: flush the shell (with each `<melt:await>` pending fallback)
    * immediately, then stream every boundary's resolved branch as a `<template>` + swap
    * `<script>` chunk. Boundaries resolve concurrently and each chunk is emitted as it settles,
    * so a slow one never holds up a faster one — the client swaps by id regardless of order.
    */
  override def renderStream(component: => RenderResult): ZIO[R, Throwable, Response] =
    templateOpt match
      case None           => ZIO.fail(missingTemplate)
      case Some(template) =>
        app match
          case None    => ZIO.succeed(composeResponse(template, Router.withPath(requestPath)(component), 200))
          case Some(a) =>
            val (result, scope) = renderInScope(a, component)
            if !scope.nonEmpty then ZIO.succeed(composeResponse(template, result, 200))
            else
              val (head, tail) = composeStreamParts(template, result)
              val pending      = scope.pendingSnapshot
              // `Body` cannot carry an environment, so `R` is discharged here — the handler
              // still has it in scope, unlike the response conversion downstream.
              ZIO.environment[R].map { env =>
                val bytes: ZStream[Any, Throwable, Byte] =
                  ZStream
                    .fromZIO(Ref.make(List.empty[(String, String)]))
                    .flatMap { seedRef =>
                      val headS = ZStream.succeed(head + SsrRenderScope.streamSwapBootstrap(nonce))
                      val chunkS = ZStream
                        .fromIterable(pending)
                        .mapZIOParUnordered(ZioHttpMeltContext.streamConcurrency) { s =>
                          scope.resolveToChunk(s, nonce).flatMap {
                            case (html, seeds) => seedRef.update(_ ++ seeds).as(html)
                          }
                        }
                      val tailS =
                        ZStream.fromZIO(seedRef.get.map(seeds => SsrRenderScope.streamSeedScript(seeds) + tail))
                      (headS ++ chunkS ++ tailS).mapConcatChunk(s => zio.Chunk.fromArray(s.getBytes("UTF-8")))
                    }
                    .provideEnvironment(env)
                StreamingResponse(200, "text/html; charset=utf-8", ZStreamBody(bytes))
              }

  /** Renders the shell inside an [[SsrRenderScope]], collecting the `<melt:await>` boundaries.
    *
    * Each deferred branch re-establishes the request path: the JVM `Router` is a `ThreadLocal`,
    * and resolution runs in a later effect that may be on another thread.
    */
  private def renderInScope(
    a:         ServerMeltKitPlatform[ZTask[R]],
    component: => RenderResult
  ): (RenderResult, SsrRenderScope[ZTask[R]]) =
    val resolve = a.resolveQueryFn(this.asInstanceOf[ServerMeltContext[ZTask[R], PathSpec.Empty, Any, RenderResult]])
    val wrap = new SsrRenderScope.BranchWrap:
      def apply(thunk: => RenderResult): RenderResult = Router.withPath(requestPath)(thunk)
    SsrRenderScope.withScope(resolve, wrap)(Router.withPath(requestPath)(component))

  /** Composes the shell for streaming and splits it where the page body ends.
    *
    * `head` is the document up to and including the shell body (with the `<melt:await>`
    * fallbacks); `tail` is everything after it. Streamed fragments and the query seed go between.
    */
  private def composeStreamParts(template: Template, result: RenderResult): (String, String) =
    val augmented =
      if result.imports.isEmpty then result
      else
        val tags    = ImportTagResolver.resolveTags(result.imports, manifest, basePath, nonce)
        val newHead = if result.head.isEmpty then tags else s"$tags\n${ result.head }"
        result.copy(head = newHead)
    val html = template.render(
      augmented.copy(body = augmented.body + ZioHttpMeltContext.streamSplit),
      manifest,
      title       = "",
      lang        = lang,
      basePath    = basePath,
      vars        = Map.empty,
      nonce       = nonce,
      routerEntry = routerEntry
    )
    val idx = html.indexOf(ZioHttpMeltContext.streamSplit)
    if idx < 0 then (html, "")
    else (html.substring(0, idx), html.substring(idx + ZioHttpMeltContext.streamSplit.length))

  private def missingTemplate: UnsupportedOperationException =
    new UnsupportedOperationException(
      "ctx.render() requires the SSR entry point (ZioHttpAdapter.ssrRoutes). " +
        "Routes registered through ZioHttpAdapter.routes are API-only."
    )

  /** Splices the rendered component into the shell, resolving any `@JSImport` tags the page
    * needs against the asset manifest. */
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
      title       = "",
      lang        = lang,
      basePath    = basePath,
      vars        = Map.empty,
      nonce       = nonce,
      routerEntry = routerEntry
    )
    PlainResponse(status, "text/html; charset=utf-8", html)

object ZioHttpMeltContext:

  /** Max `<melt:await>` boundaries resolved concurrently while streaming; each chunk is emitted
    * as it settles. */
  private val streamConcurrency: Int = 8

  /** Sentinel marking where the shell body ends, used by `composeStreamParts` to split the
    * rendered document. */
  private val streamSplit: String = " MELT_STREAM_SPLIT "
