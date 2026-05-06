/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.ssg

import scala.util.NotGiven
import scala.NamedTuple.AnyNamedTuple

import melt.runtime.render.RenderResult

import meltkit.*
import meltkit.codec.*

/** SSG-specific [[ServerMeltContext]] implementation.
  *
  * Runs on the JVM at build time — no live HTTP request exists.
  * [[render]] assembles the full HTML page via [[Template]] and stores it in
  * [[capturedHtml]] for [[SsgGenerator]] to write to disk.
  *
  * - `body` / `cookie` / `header` — throw [[UnsupportedOperationException]];
  *   SSG generates pages from GET routes only, so body access never occurs in practice.
  * - `redirect` / `notFound`      — emit a warning and skip the page
  * - `ok` / `json` / `text`       — ignored (non-HTML responses)
  *
  * @tparam F the effect type
  * @tparam P the named-tuple of typed path parameters
  * @tparam B the request body type (`Unit` = no body)
  */
final class SsgMeltContext[F[_], P <: AnyNamedTuple, B](
  val params:      P,
  val requestPath: String,
  template:        Template,
  manifest:        ViteManifest,
  basePath:        String,
  useHydration:    Boolean,
  defaultTitle:    String,
  defaultLang:     String
) extends ServerMeltContext[F, P, B, RenderResult]:

  private var _capturedHtml: Option[String] = None

  /** The fully assembled HTML produced by [[render]], available after the handler runs. */
  private[ssg] def capturedHtml: Option[String] = _capturedHtml

  val locals: Locals = new Locals()

  def query(name:    String): Option[String]            = None
  def queryAll(name: String): List[String]              = Nil
  def queryParams:            Map[String, List[String]] = Map.empty

  def render(component: => RenderResult): PlainResponse =
    val result = Router.withPath(requestPath)(component)
    val html   =
      if useHydration then
        template.render(
          result,
          manifest,
          title    = defaultTitle,
          lang     = defaultLang,
          basePath = basePath,
          vars     = Map.empty
        )
      else template.render(result, title = defaultTitle, lang = defaultLang, vars = Map.empty)
    _capturedHtml = Some(html)
    PlainResponse(200, "text/html; charset=utf-8", html)

  def render(component: => RenderResult, status: StatusCode): PlainResponse =
    render(component) // status code is not meaningful in SSG

  def ok[A: codec.BodyEncoder](value: A): PlainResponse =
    PlainResponse(200, "application/json", "")

  def created[A: codec.BodyEncoder](value: A): PlainResponse =
    PlainResponse(201, "application/json", "")

  def noContent: PlainResponse =
    PlainResponse(204, "text/plain", "")

  def text(value: String): PlainResponse =
    PlainResponse(200, "text/plain; charset=utf-8", value)

  def json(value: String): PlainResponse =
    PlainResponse(200, "application/json", value)

  def badRequest(err: BodyError): BadRequest =
    Response.badRequest(err.message)

  def redirect(path: String, permanent: Boolean = false): PlainResponse =
    System.err.println(
      s"[meltkit-ssg] Warning: redirect('$path') ignored in SSG context at path '$requestPath'"
    )
    Response.redirect(path, permanent)

  def notFound(message: String = "Not Found"): NotFound =
    System.err.println(
      s"[meltkit-ssg] Warning: notFound() at path '$requestPath' — page will be skipped"
    )
    Response.notFound(message)

  // ── ServerMeltContext body / cookie / header ─────────────────────────────
  // No real HTTP request exists in SSG.
  // Body methods throw UnsupportedOperationException: SSG only handles GET
  // routes so body access should never occur. SsgGenerator wraps each handler
  // call in try/catch and surfaces any unexpected errors clearly.

  val body: RequestBody[F, B] = new RequestBody[F, B]:
    private def noBody: Nothing =
      throw new UnsupportedOperationException("No request body in SSG context")
    def text:                                           F[String]                      = noBody
    def form:                                           F[Either[BodyError, FormData]] = noBody
    def form[A](using FormDataDecoder[A]):              F[Either[BodyError, A]]        = noBody
    def json[A](using BodyDecoder[A]):                  F[Either[BodyError, A]]        = noBody
    def decode(using NotGiven[B =:= Unit]):             F[Either[BodyError, B]]        = noBody
    def decodeOrBadRequest(using NotGiven[B =:= Unit]): F[B]                           = noBody

  def cookie(name: String): Option[String]      = None
  def cookies:              Map[String, String] = Map.empty
  def header(name: String): Option[String]      = None
  def headers:              Map[String, String] = Map.empty
