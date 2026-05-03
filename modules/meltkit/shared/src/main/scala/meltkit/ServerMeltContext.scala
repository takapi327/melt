/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.NamedTuple.AnyNamedTuple

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

  /** Returns the value of the named cookie from the request `Cookie` header, if present.
    *
    * {{{
    * app.on(Endpoint.get("profile").response[Profile]) { ctx =>
    *   ctx.cookie("session_id") match
    *     case None     => IO.pure(Left(Unauthorized()))
    *     case Some(id) => sessionStore.get(id).map(ctx.ok(_))
    * }
    * }}}
    */
  def cookie(name: String): Option[String]

  /** Returns all cookies from the request `Cookie` header as a `name → value` map.
    *
    * If the same cookie name appears more than once the last value wins.
    */
  def cookies: Map[String, String]

  /** Returns the value of the named request header (case-insensitive).
    *
    * If the same header name appears more than once, values are joined with
    * `", "` per RFC 7230 §3.2.2.
    *
    * {{{
    * ctx.header("Authorization")  // Some("Bearer abc")
    * ctx.header("authorization")  // Some("Bearer abc")  ← case-insensitive
    * ctx.header("X-Missing")      // None
    * }}}
    */
  def header(name: String): Option[String]

  /** Returns all request headers as a `name → value` map.
    *
    * - Header names are normalized to **lowercase**.
    * - If the same header name appears more than once, values are joined with `", "`.
    *
    * {{{
    * ctx.headers                  // Map("authorization" -> "Bearer abc", "content-type" -> "application/json", ...)
    * ctx.headers("authorization") // "Bearer abc"
    * }}}
    */
  def headers: Map[String, String]

