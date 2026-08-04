/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.NamedTuple.AnyNamedTuple

import melt.runtime.render.RenderResult

/** Server-side extension of [[MeltContext]] that adds request-body access.
  *
  * Only server adapters (e.g. `Http4sMeltContext`) implement this trait.
  * The browser adapter (`BrowserMeltContext`) extends [[MeltContext]] only,
  * because browser navigation routes carry no request body.
  *
  * Route handlers registered with [[MeltKit.on]] and `app.post` / `app.put` /
  * `app.patch` / `app.delete` receive a `ServerMeltContext` so they can access
  * the request body via [[body]], as well as cookies and headers.
  * Handlers registered with `app.get` receive a plain [[MeltContext]].
  *
  * @tparam F the effect type (e.g. `cats.effect.IO`)
  * @tparam P the [[scala.NamedTuple]] of typed path parameters
  * @tparam B the request body type (`Unit` = no body)
  * @tparam C the component type for this platform
  */
trait ServerMeltContext[F[_], P <: AnyNamedTuple, B, C] extends MeltContext[F, P, B, C]:

  /** Format-specific access to the request body.
    *
    * Provides methods to read the body as raw text, JSON, or using the
    * endpoint's [[codec.BodyDecoder]]:
    *
    * {{{
    * // Raw text
    * ctx.body.text                     // F[String]
    *
    * // JSON (requires a BodyDecoder in scope)
    * ctx.body.json[CreateTodo]         // F[Either[BodyError, CreateTodo]]
    *
    * // Endpoint's decoder (only when B ≠ Unit)
    * ctx.body.decode                   // F[Either[BodyError, B]]
    * ctx.body.decodeOrBadRequest       // F[B]
    * }}}
    */
  def body: RequestBody[F, B]

  // NOTE: `cookie` / `cookies` / `header` / `headers` moved up to the base
  // `MeltContext` (with `None` / empty defaults) so that GET handlers — which
  // receive the base `MeltContext` — can read request headers and cookies too.
  // Server-side contexts (e.g. `JvmMeltContext`) override them with real values.

  /** Renders a component with **blocking async SSR**: every `<melt:await>` boundary
    * is resolved server-side (in-process, no HTTP loopback) and its branch is
    * spliced into the HTML before the response is sent, with the resolved query
    * results seeded for hydration so the client adopts them without refetching.
    *
    * Returns `F[Response]` (unlike the synchronous [[render]]) because resolving a
    * boundary is effectful. A page with no `<melt:await>` behaves exactly like
    * [[render]] lifted into `F`.
    *
    * Implemented by the http4s, Node, and JVM (Undertow) server contexts. The
    * default raises [[UnsupportedOperationException]] for any context that has not
    * wired async SSR.
    */
  def renderAsync(component: => C): F[Response] =
    throw new UnsupportedOperationException(
      "renderAsync (blocking async SSR for <melt:await>) is not supported by this server context."
    )

  /** Renders a component with **streaming async SSR**: the shell (with each
    * `<melt:await>` boundary's pending fallback) is flushed immediately for a fast
    * first paint, then each boundary's resolved branch is streamed as a chunk that
    * the client swaps over its fallback — React 18 `renderToPipeableStream`-style.
    *
    * The final DOM (and hydration seed) is identical to [[renderAsync]]; only the
    * delivery is incremental. Streaming requires JS on the client for the swap.
    *
    * Only the http4s adapter implements true chunked streaming; the default here
    * degrades to the blocking [[renderAsync]] (a single response), so a route
    * written against `renderStream` still works on every server context. */
  def renderStream(component: => C): F[Response] = renderAsync(component)

/** Makes [[ServerMeltContext.renderAsync]] / [[ServerMeltContext.renderStream]]
  * callable from `app.get` handlers, whose `ctx` is statically a [[MeltContext]]
  * but is always a server context at runtime. */
extension [F[_], P <: AnyNamedTuple, B](ctx: MeltContext[F, P, B, RenderResult])
  def renderAsync(component: => RenderResult): F[Response] =
    ctx match
      case s: ServerMeltContext[F, P, B, RenderResult] @unchecked => s.renderAsync(component)
      case _                                                      =>
        throw new UnsupportedOperationException("renderAsync requires a server (SSR) context")

  def renderStream(component: => RenderResult): F[Response] =
    ctx match
      case s: ServerMeltContext[F, P, B, RenderResult] @unchecked => s.renderStream(component)
      case _                                                      =>
        throw new UnsupportedOperationException("renderStream requires a server (SSR) context")
