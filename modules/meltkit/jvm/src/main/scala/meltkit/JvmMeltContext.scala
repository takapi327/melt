/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.util.NotGiven
import scala.NamedTuple.AnyNamedTuple

import melt.runtime.render.RenderResult

import meltkit.codec.{ BodyDecoder, BodyEncoder, FormDataDecoder }
import meltkit.exceptions.BodyDecodeException

/** JVM SSR/SSG implementation of [[ServerMeltContext]] parameterised over `F[_]`.
  *
  * `render` evaluates the component inside `Router.withPath(requestPath)(...)`,
  * setting `Router.currentPath` via `ThreadLocal` for the duration of the
  * synchronous render.
  *
  * Body methods delegate to `runner.map(rawBody)(...)` so that the same class
  * works for both `Future`-based SSR (via [[UndertowHttpBinding]]) and identity-effect
  * SSG (via [[meltkit.ssg.SsgGenerator]]) without requiring `ExecutionContext`
  * at the SSG call site.
  */
final class JvmMeltContext[F[_], P <: AnyNamedTuple, B](
  val params:               P,
  val requestPath:          String,
  private val _queryParams: Map[String, List[String]]        = Map.empty,
  private val bodyDecoder:  BodyDecoder[B],
  private val rawBody:      F[String],
  private val rawHeaders:   Map[String, String]              = Map.empty,
  private val rawCookies:   Map[String, String]              = Map.empty,
  private val templateOpt:  Option[Template]                 = None,
  private val manifest:     ViteManifest                     = ViteManifest.empty,
  private val lang:         String                           = "en",
  private val basePath:     String                           = "",
  override val locals:      Locals                           = new Locals(),
  private val nonce:        Option[String]                   = None,
  private val defaultTitle: String                           = "",
  private val app:          Option[ServerMeltKitPlatform[F]] = None,
  private val routerEntry:  Option[String]                   = None,
  // Present only for the Future-based Undertow server (streaming SSR needs a real
  // ExecutionContext for concurrent boundary resolution); absent for SSG, where
  // renderStream degrades to the blocking renderAsync.
  private val streamEc: Option[scala.concurrent.ExecutionContext] = None
)(using runner: SyncRunner[F])
  extends ServerMeltContext[F, P, B, RenderResult]:

  override def query(name: String): Option[String] =
    _queryParams.get(name).flatMap(_.headOption)

  override def queryAll(name: String): List[String] =
    _queryParams.getOrElse(name, Nil)

  override def queryParams: Map[String, List[String]] = _queryParams

  // ── ServerMeltContext: body ────────────────────────────────────────────

  override val body: RequestBody[F, B] = new RequestBody[F, B]:

    def text: F[String] = rawBody

    def form: F[Either[BodyError, FormData]] =
      runner.map(rawBody)(FormData.parse)

    def form[A](using fdd: FormDataDecoder[A]): F[Either[BodyError, A]] =
      runner.map(rawBody)(raw => FormData.parse(raw).flatMap(fdd.decode))

    def json[A](using dec: BodyDecoder[A]): F[Either[BodyError, A]] =
      runner.map(rawBody)(dec.decode)

    def decode(using NotGiven[B =:= Unit]): F[Either[BodyError, B]] =
      runner.map(rawBody)(bodyDecoder.decode)

    def decodeOrBadRequest(using NotGiven[B =:= Unit]): F[B] =
      runner.map(rawBody) { raw =>
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
    templateOpt match
      case None           => throw missingTemplate
      case Some(template) =>
        val composed = Router.withPath(requestPath) {
          app match
            case Some(a) => a.wrapLayouts(requestPath, () => component)
            case None    => component
        }
        composeResponse(template, composed, 200)

  /** Blocking async SSR on a synchronous effect: resolve every `<melt:await>`
    * boundary in-process (via the app's server-function registry) and splice the
    * resolved branches over their markers, seeding the results for hydration. The
    * `Recover`/`Parallel` needed by resolution are derived from the [[SyncRunner]]
    * (resolution is sequential — a sync effect has no real concurrency). */
  override def renderAsync(component: => RenderResult): F[Response] =
    templateOpt match
      case None           => throw missingTemplate
      case Some(template) =>
        app match
          case None =>
            runner.pure(composeResponse(template, Router.withPath(requestPath)(component), 200))
          case Some(a) =>
            // Derive the effect type classes from the synchronous runner (resolution
            // is sequential — a sync effect has no real concurrency).
            given pureF: Pure[F] with
              def pure[A](x: A): F[A] = runner.pure(x)
            val flatMapF = new FlatMap[F]:
              def flatMap[A, C](fa: F[A])(f: A => F[C]): F[C] = f(runner.runSync(fa))
            val recover = new Recover[F]:
              def attempt[A](fa: F[A]): F[Either[Throwable, A]] =
                runner.pure(try Right(runner.runSync(fa))
                catch case e: Throwable => Left(e))
            val parallel = new Parallel[F]:
              def parTraverse[A, C](as: List[A])(f: A => F[C]): F[List[C]] =
                runner.pure(as.map(x => runner.runSync(f(x))))
            val resolve = a.resolveQueryFn(this.asInstanceOf[ServerMeltContext[F, PathSpec.Empty, Any, RenderResult]])
            val wrap    = new SsrRenderScope.BranchWrap:
              def apply(thunk: => RenderResult): RenderResult = Router.withPath(requestPath)(thunk)
            val (result, scope) =
              SsrRenderScope.withScope[F, RenderResult](resolve, wrap)(Router.withPath(requestPath)(component))
            if !scope.nonEmpty then runner.pure(composeResponse(template, result, 200))
            else
              runner.map(scope.resolveAll(using runner, flatMapF, pureF, recover, parallel)) { resolved =>
                composeResponse(template, result.copy(body = SsrRenderScope.spliceAndSeed(result.body, resolved)), 200)
              }

  /** Streaming async SSR (Undertow): flush the shell with each `<melt:await>` pending
    * fallback immediately, then stream each resolved branch as a `<template>` +
    * swap-script chunk. Unlike [[renderAsync]] (which resolves via the sequential
    * [[SyncRunner]]), this uses the real `Future` type-class instances for concurrent
    * resolution — hence it needs the server's [[scala.concurrent.ExecutionContext]]
    * ([[streamEc]]). Without one (e.g. SSG), it degrades to blocking [[renderAsync]]. */
  override def renderStream(component: => RenderResult): F[Response] =
    streamEc match
      case None      => renderAsync(component)
      case Some(ecx) =>
        templateOpt match
          case None           => throw missingTemplate
          case Some(template) =>
            app match
              case None =>
                runner.pure(composeResponse(template, Router.withPath(requestPath)(component), 200))
              case Some(a) =>
                // The Undertow binding always fixes F = Future, so the built-in
                // Future type classes (concurrent) drive resolution here.
                given scala.concurrent.ExecutionContext = ecx
                given pureF: Pure[F] with
                  def pure[A](x: A): F[A] = runner.pure(x)
                val resolve = a
                  .resolveQueryFn(this.asInstanceOf[ServerMeltContext[F, PathSpec.Empty, Any, RenderResult]])
                  .asInstanceOf[(String, String) => scala.concurrent.Future[Option[String]]]
                val wrap = new SsrRenderScope.BranchWrap:
                  def apply(thunk: => RenderResult): RenderResult = Router.withPath(requestPath)(thunk)
                val (result, scope) =
                  SsrRenderScope.withScope[scala.concurrent.Future, RenderResult](resolve, wrap)(
                    Router.withPath(requestPath)(component)
                  )
                if !scope.nonEmpty then runner.pure(composeResponse(template, result, 200))
                else
                  val (head, tail) = composeStreamParts(template, result)
                  val chunks       = scope.pendingSnapshot.map(s => scope.resolveToChunk(s, nonce))
                  runner.pure(
                    StreamingResponse(
                      200,
                      "text/html; charset=utf-8",
                      FutureStreamBody(head + SsrRenderScope.streamSwapBootstrap(nonce), chunks, tail)
                    )
                  )

  private def missingTemplate: IllegalStateException =
    new IllegalStateException("ctx.render() requires a JvmMeltContext initialized with a Template.")

  /** Composes the shell for streaming and splits it at the end of the page body into
    * `(head, tail)` — the same split as the http4s adapter. */
  private def composeStreamParts(template: Template, result: RenderResult): (String, String) =
    val augmented =
      if result.imports.isEmpty then result
      else
        val tags    = ImportTagResolver.resolveTags(result.imports, manifest, basePath, nonce)
        val newHead = if result.head.isEmpty then tags else s"$tags\n${ result.head }"
        result.copy(head = newHead)
    val html = template.render(
      augmented.copy(body = augmented.body + JvmMeltContext.streamSplit),
      manifest,
      title       = defaultTitle,
      lang        = lang,
      basePath    = basePath,
      vars        = Map.empty,
      nonce       = nonce,
      routerEntry = routerEntry
    )
    val idx = html.indexOf(JvmMeltContext.streamSplit)
    if idx < 0 then (html, "")
    else (html.substring(0, idx), html.substring(idx + JvmMeltContext.streamSplit.length))

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
      title       = defaultTitle,
      lang        = lang,
      basePath    = basePath,
      vars        = Map.empty,
      nonce       = nonce,
      routerEntry = routerEntry
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

object JvmMeltContext:
  /** Sentinel marking where the shell body ends, for splitting the streamed shell.
    * NUL chars never appear in HTML. */
  private val streamSplit: String = " MELT_STREAM_SPLIT "
