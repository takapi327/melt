/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** A read-only view of an incoming request, passed to each [[Middleware]].
  *
  * Abstracts over the adapter-specific request type and the route-specific
  * path-parameter / body type parameters of [[MeltContext]], so that
  * middleware can be written without depending on any particular adapter.
  */
trait RequestInfo:

  /** The HTTP method name in uppercase (e.g. `"GET"`, `"POST"`). */
  def method: String

  /** The path component of the request URL (e.g. `"/users/42"`). */
  def requestPath: String

  /** Returns the first value of the named query parameter, if present. */
  def query(name: String): Option[String]

  /** Returns all values of the named query parameter. */
  def queryAll(name: String): List[String]

  /** Returns all query parameters as a multi-valued map. */
  def queryParams: Map[String, List[String]]

  /** Returns the value of the named request header (case-insensitive).
    *
    * If the same header appears more than once, values are joined with `", "`.
    */
  def header(name: String): Option[String]

  /** Returns all request headers as a lowercase-keyed name→value map. */
  def headers: Map[String, String]

  /** Returns the value of the named cookie from the `Cookie` header, if present. */
  def cookie(name: String): Option[String]

  /** Returns all cookies from the `Cookie` header as a name→value map. */
  def cookies: Map[String, String]

  /** The request-scoped local store.
    *
    * Middleware writes values here before calling `next`;
    * they are visible to subsequent middleware and the route handler.
    *
    * Write operations (`set`, `remove`) are provided by the adapter as
    * `F[Unit]`-returning extension methods (e.g. `LocalsOps` in the http4s adapter).
    */
  def locals: Locals

/** A function that wraps a route handler effect.
  *
  * {{{
  * // Authentication middleware
  * val auth: Middleware[IO] = (info, next) =>
  *   info.cookie("session_id") match
  *     case None     => IO.pure(Response.badRequest("Unauthorized"))
  *     case Some(id) => next   // proceed to the actual handler
  *
  * // Logging middleware
  * val logging: Middleware[IO] = (info, next) =>
  *   IO.println(s"${info.method} ${info.requestPath}") *> next
  *
  * // Response-modification middleware
  * val cors: Middleware[IO] = (info, next) =>
  *   next.map(_.withHeaders(Map("Access-Control-Allow-Origin" -> "*")))
  *
  * app.use(logging)
  * app.use(auth)
  * app.use(cors)
  * }}}
  *
  * Middlewares are applied in registration order.
  * Use [[Middleware.sequence]] to compose multiple middlewares into one.
  */
type Middleware[F[_]] = (RequestInfo, F[Response]) => F[Response]

object Middleware:

  /** Composes multiple middlewares into a single middleware, applied in order.
    *
    * {{{
    * app.use(Middleware.sequence(logging, auth, cors))
    * // equivalent to:
    * // app.use(logging)
    * // app.use(auth)
    * // app.use(cors)
    * }}}
    */
  def sequence[F[_]](ms: Middleware[F]*): Middleware[F] =
    ms.toList.foldRight[Middleware[F]]((_, next) => next) { (mw, acc) => (info, next) => mw(info, acc(info, next)) }
