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
  private val app:          Option[ServerMeltKitPlatform[F]] = None
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
    render(component, 200)

  override def render(component: => RenderResult, status: StatusCode): PlainResponse =
    templateOpt match
      case None           => throw missingTemplate
      case Some(template) => composeResponse(template, Router.withPath(requestPath)(component), status)

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
            given Pure[F] with
              def pure[A](x: A): F[A] = runner.pure(x)
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
              runner.map(scope.resolveAll(using runner, recover, parallel)) { resolved =>
                composeResponse(template, result.copy(body = SsrRenderScope.spliceAndSeed(result.body, resolved)), 200)
              }

  private def missingTemplate: IllegalStateException =
    new IllegalStateException("ctx.render() requires a JvmMeltContext initialized with a Template.")

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
