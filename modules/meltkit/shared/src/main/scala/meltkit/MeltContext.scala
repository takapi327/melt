/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.NamedTuple.AnyNamedTuple

import meltkit.codec.BodyEncoder

/** The handler context provided to each route handler.
  *
  * Concrete implementations are provided by adapter modules.
  *
  * For request-body access (`ctx.body.json[A]` / `ctx.body.decode`) use
  * [[ServerMeltContext]], which extends this trait and is provided to
  * handlers registered via `app.post` / `app.put` / `app.patch` /
  * `app.delete` and [[MeltKit.on]].
  *
  * @tparam F the effect type (e.g. `cats.effect.IO`)
  * @tparam P the [[scala.NamedTuple]] of typed path parameters
  * @tparam B the request body type (`Unit` = no body)
  * @tparam C the component type for this platform
  *           (`dom.Element` on browser, `RenderResult` on JVM / Node.js)
  */
trait MeltContext[F[_], P <: AnyNamedTuple, B, C]:

  /** The typed path parameters extracted from the URL.
    *
    * {{{
    * val id = param[Int]("id")
    * app.on(Endpoint.get("users" / id).response[User]) { ctx =>
    *   ctx.params.id  // Int
    * }
    * }}}
    */
  def params: P

  /** The path component of the current request URL (e.g. `"/counter"`, `"/users/42"`).
    *
    * On the JVM (SSR) this is derived from the incoming HTTP request URI.
    * In the browser it reflects `window.location.pathname`.
    *
    * Used internally by the JVM `ctx.melt()` extension to automatically set
    * [[Router.currentPath]] for the duration of SSR rendering, so that
    * components can read the correct path without any manual `Router.withPath`
    * call in route handlers.
    */
  def requestPath: String

  /** The request-scoped local store, populated by middleware.
    *
    * One [[Locals]] instance is created per matched request and shared between
    * all hooks (via [[RequestEvent.locals]]) and the route handler.
    * On the browser there is no middleware, so this map is always empty.
    *
    * {{{
    * val userKey = LocalKey.make[AuthUser]
    *
    * // middleware (server only) — sets the value
    * app.use { (info, next) =>
    *   verifyToken(info.header("Authorization")) match
    *     case None       => IO.pure(Unauthorized())
    *     case Some(user) => IO { info.locals.set(userKey, user) } *> next
    * }
    *
    * // GET handler — reads the value set by middleware
    * app.get("profile") { ctx =>
    *   IO.pure(ctx.ok(ctx.locals.get(userKey).getOrElse("guest")))
    * }
    * }}}
    */
  def locals: Locals

  /** Returns the first value of the named query parameter, if present. */
  def query(name: String): Option[String]

  /** Returns all values of the named query parameter.
    *
    * {{{
    * // URL: /articles?tag=scala&tag=fp&tag=cats
    * ctx.queryAll("tag")     // List("scala", "fp", "cats")
    * ctx.queryAll("missing") // List()
    * }}}
    */
  def queryAll(name: String): List[String]

  /** Returns all query parameters as a multi-valued map.
    *
    * {{{
    * // URL: /articles?tag=scala&tag=fp&page=1
    * ctx.queryParams  // Map("tag" -> List("scala", "fp"), "page" -> List("1"))
    * }}}
    */
  def queryParams: Map[String, List[String]]

  /** Renders a Melt component and returns a 200 response.
    *
    * The `component` parameter is by-name so that server-side implementations
    * can evaluate it inside `Router.withPath(requestPath)(...)`, ensuring that
    * `Router.currentPath` returns the correct value during SSR rendering.
    * Browser implementations evaluate `component` immediately.
    *
    * {{{
    * // shared route handler — works on both browser and server
    * app.get("todos") { ctx => F.pure(ctx.render(TodoPage())) }
    * }}}
    *
    * On JVM / Node.js: only available when the adapter is initialized with a [[Template]].
    * Calling this method without a template raises an [[IllegalStateException]] at runtime.
    */
  def render(component: => C): PlainResponse

  /** Renders a Melt component and returns a response with the given status code.
    *
    * Use this to render error pages with the appropriate HTTP status:
    *
    * {{{
    * app.get("products" / productId) { ctx =>
    *   productStore.find(ctx.params.id).map {
    *     case Some(product) => ctx.render(ProductPage(product))
    *     case None          => ctx.render(NotFoundPage(), 404)
    *   }
    * }
    * }}}
    */
  def render(component: => C, status: StatusCode): PlainResponse

  /** Builds a 200 OK JSON response. Requires a [[BodyEncoder]][A] in scope. */
  def ok[A: BodyEncoder](value: A): PlainResponse

  /** Builds a 201 Created JSON response. Requires a [[BodyEncoder]][A] in scope. */
  def created[A: BodyEncoder](value: A): PlainResponse

  /** Builds a 204 No Content response. */
  def noContent: PlainResponse

  /** Builds a plain-text 200 response. */
  def text(value: String): PlainResponse

  /** Builds a 200 application/json response from a raw JSON string. */
  def json(value: String): PlainResponse

  /** Builds a 400 Bad Request response from a [[BodyError]]. */
  def badRequest(err: BodyError): BadRequest

  /** Builds a 301 (permanent) or 302 (temporary) redirect response.
    *
    * Only relative paths are accepted (e.g. `"/dashboard"`, `"/users/1"`).
    * Absolute URLs (`https://...`), protocol-relative URLs (`//...`), and
    * other schemes are rejected with [[IllegalArgumentException]] to prevent
    * open-redirect attacks when user-supplied input flows into this method.
    *
    * @throws IllegalArgumentException if `path` is an external URL
    */
  def redirect(path: String, permanent: Boolean = false): PlainResponse

  /** Builds a 404 Not Found response. */
  def notFound(message: String = "Not Found"): NotFound
