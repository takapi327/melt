/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.collection.mutable.ListBuffer
import scala.NamedTuple.AnyNamedTuple

import melt.runtime.render.RenderResult

import meltkit.codec.BodyDecoder
import meltkit.codec.PathParamDecoder

/** Adapter-supplied factory that constructs a [[MeltContext]] for each request.
  *
  * @tparam F the effect type
  * @tparam C the component type for this platform
  */
private[meltkit] trait MeltContextFactory[F[_], C]:
  def build[P <: AnyNamedTuple, B](params: P, bodyDecoder: BodyDecoder[B]): MeltContext[F, P, B, C]

/** A registered route entry.
  *
  * @tparam F the effect type
  * @tparam C the component type for this platform
  */
private[meltkit] final class Route[F[_], C](
  val method:    HttpMethod,
  val segments:  List[PathSegment],
  val tryHandle: (List[String], MeltContextFactory[F, C]) => Option[F[Response]]
):
  /** Returns a copy of this route with `prefix` prepended to its segments. */
  private[meltkit] def withPrefix(prefix: String): Route[F, C] =
    Route(method, PathSegment.Static(prefix) :: segments, tryHandle)

/** The MeltKit routing DSL — platform-agnostic base trait.
  *
  * Users do not extend or instantiate this trait directly. Instead, use the
  * platform-specific [[MeltKit]] subclass, which fixes `C` automatically:
  *
  *   - JVM / Node.js — `MeltKit[IO]()` where `C = RenderResult`
  *   - Browser       — `MeltKit()` where `C = dom.Element`
  *
  * GET navigation routes are registered here via [[get]] / [[getAll]].
  * Data-mutation routes (`post` / `put` / `patch` / `delete`) are defined
  * in [[ServerMeltKitPlatform]] because they require server-side body access
  * and are not used by the browser router (which handles GET navigation only).
  *
  * {{{
  * // JVM / Node.js
  * val app = MeltKit[IO]()
  * app.get("api/todos") { ctx => IO.pure(ctx.ok(todos)) }
  * app.get("todos")     { ctx => IO.delay(ctx.render(TodoPage())) }
  *
  * // Browser
  * val app = MeltKit()
  * app.get("todos") { ctx => ctx.render(TodoPage()) }
  * }}}
  *
  * @tparam F the effect type (e.g. `cats.effect.IO`, `Id`)
  * @tparam C the component type for this platform
  */
trait MeltKitPlatform[F[_], C]:
  private val _routes = ListBuffer[Route[F, C]]()

  /** Returns all registered routes. Intended for adapter use only. */
  private[meltkit] def routes: List[Route[F, C]] = _routes.toList

  /** Adds a route. Used by [[ServerMeltKitPlatform]] to register typed endpoints. */
  private[meltkit] def addRoute(r: Route[F, C]): Unit = _routes += r

  private[meltkit] def register[P <: AnyNamedTuple](
    method: HttpMethod,
    spec:   PathSpec[P]
  )(handler: MeltContext[F, P, Unit, C] => F[Response]): Unit =
    val tryHandle: (List[String], MeltContextFactory[F, C]) => Option[F[Response]] =
      (rawValues, factory) =>
        val results = spec.paramDecoders.zip(rawValues).map {
          case ((_, dec), raw) =>
            dec.asInstanceOf[PathParamDecoder[Any]].decode(raw)
        }
        if results.forall(_.isRight) then
          val decoded = results.collect { case Right(v) => v }
          val params  = decoded.foldRight(EmptyTuple: Tuple)(_ *: _).asInstanceOf[P]
          Some(handler(factory.build(params, summon[BodyDecoder[Unit]])))
        else None
    _routes += Route(method, spec.segments, tryHandle)

  def get[P <: AnyNamedTuple](spec: PathSpec[P])(handler: MeltContext[F, P, Unit, C] => F[Response]): Unit =
    register("GET", spec)(handler)

  def get(path: String)(handler: MeltContext[F, PathSpec.Empty, Unit, C] => F[Response]): Unit =
    register("GET", PathSpec.fromString(path))(handler)

  /** Registers a catch-all GET handler that matches any path not already
    * matched by a more-specific route.
    *
    * Register this **last** so that specific routes (API endpoints, typed
    * pages) take precedence. Typical use is SSR page fallback:
    *
    * {{{
    * app.get("api/todos") { ctx => ... }  // specific API route first
    *
    * app.getAll { ctx =>                  // catch-all last
    *   IO.pure(ctx.melt(App()))
    * }
    * }}}
    */
  def getAll(handler: MeltContext[F, NamedTuple.Empty, Unit, C] => F[Response]): Unit =
    val tryHandle: (List[String], MeltContextFactory[F, C]) => Option[F[Response]] =
      (_, factory) => Some(handler(factory.build(PathSpec.emptyValue, summon[BodyDecoder[Unit]])))
    _routes += Route("GET", List(PathSegment.Wildcard), tryHandle)

  /** Mounts a sub-router under a static path prefix.
    *
    * {{{
    * val api = MeltKit[IO]()
    * api.get("users") { ctx => ... }
    *
    * app.route("api", api)  // → GET /api/users
    * }}}
    */
  def route(prefix: String, sub: MeltKitPlatform[F, C]): Unit =
    sub.routes.foreach { r => _routes += r.withPrefix(prefix) }

/** Server-specific extension of [[MeltKitPlatform]] that adds data-mutation
  * routes (`post` / `put` / `patch` / `delete`) and typed endpoint support
  * via [[on]].
  *
  * Extended by the JVM and Node.js platform [[MeltKit]] subclasses only.
  * Browser routing handles GET navigation only, so these methods are
  * intentionally absent from the browser [[MeltKit]].
  *
  * Handlers registered here receive a [[ServerMeltContext]] which provides
  * access to the request body (`ctx.body.json[A]`, `ctx.body.text`, …),
  * cookies (`ctx.cookie`), and headers (`ctx.header`).
  *
  * {{{
  * // Simple route with body access
  * app.post("api/todos") { ctx =>
  *   ctx.body.json[CreateTodo].flatMap {
  *     case Right(todo) => todoStore.create(todo).map(ctx.created(_))
  *     case Left(err)   => IO.pure(ctx.badRequest(err))
  *   }
  * }
  *
  * // Typed endpoint
  * val createTodo = Endpoint.post("api/todos").body[CreateTodoBody]
  * app.on(createTodo) { ctx =>
  *   ctx.body.decodeOrBadRequest.flatMap { body =>
  *     todoStore.update(_ :+ Todo(body.text)).as(ctx.ok(todo))
  *   }
  * }
  * }}}
  */
trait ServerMeltKitPlatform[F[_]] extends MeltKitPlatform[F, RenderResult]:

  private val _middlewares = ListBuffer[Middleware[F]]()

  /** Registers a middleware to run around every matched route handler.
    *
    * Middlewares run in registration order (first registered = outermost).
    *
    * {{{
    * app.use { (info, next) =>
    *   info.cookie("session_id") match
    *     case None     => IO.pure(Unauthorized())
    *     case Some(_)  => next
    * }
    * }}}
    */
  def use(middleware: Middleware[F]): Unit =
    _middlewares += middleware

  private[meltkit] def middlewares: List[Middleware[F]] = _middlewares.toList

  // ── Data-mutation routes ────────────────────────────────────────────────

  private def registerServer[P <: AnyNamedTuple](
    method: HttpMethod,
    spec:   PathSpec[P]
  )(handler: ServerMeltContext[F, P, Unit, RenderResult] => F[Response]): Unit =
    val tryHandle: (List[String], MeltContextFactory[F, RenderResult]) => Option[F[Response]] =
      (rawValues, factory) =>
        val results = spec.paramDecoders.zip(rawValues).map {
          case ((_, dec), raw) =>
            dec.asInstanceOf[PathParamDecoder[Any]].decode(raw)
        }
        if results.forall(_.isRight) then
          val decoded = results.collect { case Right(v) => v }
          val params  = decoded.foldRight(EmptyTuple: Tuple)(_ *: _).asInstanceOf[P]
          val ctx     = factory.build(params, summon[BodyDecoder[Unit]])
                          .asInstanceOf[ServerMeltContext[F, P, Unit, RenderResult]]
          Some(handler(ctx))
        else None
    addRoute(Route(method, spec.segments, tryHandle))

  def post[P <: AnyNamedTuple](spec: PathSpec[P])(
    handler: ServerMeltContext[F, P, Unit, RenderResult] => F[Response]
  ): Unit =
    registerServer("POST", spec)(handler)

  def post(path: String)(
    handler: ServerMeltContext[F, PathSpec.Empty, Unit, RenderResult] => F[Response]
  ): Unit =
    registerServer("POST", PathSpec.fromString(path))(handler)

  def put[P <: AnyNamedTuple](spec: PathSpec[P])(
    handler: ServerMeltContext[F, P, Unit, RenderResult] => F[Response]
  ): Unit =
    registerServer("PUT", spec)(handler)

  def put(path: String)(
    handler: ServerMeltContext[F, PathSpec.Empty, Unit, RenderResult] => F[Response]
  ): Unit =
    registerServer("PUT", PathSpec.fromString(path))(handler)

  def delete[P <: AnyNamedTuple](spec: PathSpec[P])(
    handler: ServerMeltContext[F, P, Unit, RenderResult] => F[Response]
  ): Unit =
    registerServer("DELETE", spec)(handler)

  def delete(path: String)(
    handler: ServerMeltContext[F, PathSpec.Empty, Unit, RenderResult] => F[Response]
  ): Unit =
    registerServer("DELETE", PathSpec.fromString(path))(handler)

  def patch[P <: AnyNamedTuple](spec: PathSpec[P])(
    handler: ServerMeltContext[F, P, Unit, RenderResult] => F[Response]
  ): Unit =
    registerServer("PATCH", spec)(handler)

  def patch(path: String)(
    handler: ServerMeltContext[F, PathSpec.Empty, Unit, RenderResult] => F[Response]
  ): Unit =
    registerServer("PATCH", PathSpec.fromString(path))(handler)

  // ── Typed endpoints ─────────────────────────────────────────────────────

  /** Registers a typed endpoint handler.
    *
    * The handler must return `F[Response]` (no errorOut) or
    * `F[Either[E, Response]]` (with errorOut). Use [[MeltContext]] helpers
    * such as `ctx.ok`, `ctx.created`, and `ctx.noContent` to build responses.
    *
    * {{{
    * // without errorOut — return F[Response] directly
    * val getTodos = Endpoint.get("api/todos").response[List[Todo]]
    * app.on(getTodos) { ctx =>
    *   todoStore.get.map(ctx.ok(_))
    * }
    *
    * // with errorOut — return F[Either[E, Response]]
    * val getUser = Endpoint.get("users" / userId).errorOut[NotFound].response[User]
    * app.on(getUser) { ctx =>
    *   userStore.find(_.id == ctx.params.id) match
    *     case Some(u) => IO.pure(Right(ctx.ok(u)))
    *     case None    => IO.pure(Left(ctx.notFound("...")))
    * }
    * }}}
    */
  def on[P <: AnyNamedTuple, B, E <: Response, Out](ep: Endpoint[P, B, E, ?])(
    handler: ServerMeltContext[F, P, B, RenderResult] => F[Out]
  )(using functor: Functor[F], lift: ResponseLift[E, Out]): Unit =
    val tryHandle: (List[String], MeltContextFactory[F, RenderResult]) => Option[F[Response]] =
      (rawValues, factory) =>
        val results = ep.spec.paramDecoders.zip(rawValues).map {
          case ((_, dec), raw) =>
            dec.asInstanceOf[PathParamDecoder[Any]].decode(raw)
        }
        if results.forall(_.isRight) then
          val decoded = results.collect { case Right(v) => v }
          val params  = decoded.foldRight(EmptyTuple: Tuple)(_ *: _).asInstanceOf[P]
          val ctx     = factory.build(params, ep.bodyDecoder).asInstanceOf[ServerMeltContext[F, P, B, RenderResult]]
          Some(functor.map(handler(ctx))(lift.lift))
        else None
    addRoute(Route(ep.method, ep.spec.segments, tryHandle))

/** Extracts a [[Response]] from a handler output `Out`.
  *
  * Allows [[ServerMeltKitPlatform.on]] to accept both `F[Response]` and `F[Either[E, Response]]`:
  *
  * {{{
  * // without errorOut — F[Response]
  * app.on(getTodos) { ctx => todoStore.get.map(ctx.ok(_)) }
  *
  * // with errorOut — F[Either[E, Response]]
  * app.on(getUser) { ctx =>
  *   userStore.find(_.id == ctx.params.id) match
  *     case Some(u) => IO.pure(Right(ctx.ok(u)))
  *     case None    => IO.pure(Left(ctx.notFound("...")))
  * }
  * }}}
  */
sealed trait ResponseLift[E, Out]:
  def lift(out: Out): Response

object ResponseLift:
  /** `F[R]` where `R <: Response` — for endpoints without `errorOut`. */
  given [R <: Response]: ResponseLift[Nothing, R] with
    override def lift(r: R): Response = r

  /** `F[Either[E, R]]` where `R <: Response` — for endpoints with `errorOut`. */
  given [E <: Response, R <: Response]: ResponseLift[E, Either[E, R]] with
    override def lift(e: Either[E, R]): Response = e.fold(identity, identity)

  /** `Right[Nothing, R]` where `R <: Response` — widened from a `Right` literal. */
  given [E <: Response, R <: Response]: ResponseLift[E, Right[Nothing, R]] with
    override def lift(r: Right[Nothing, R]): Response = r.value

  /** `Left[E, Nothing]` — widened from a `Left` literal. */
  given [E <: Response]: ResponseLift[E, Left[E, Nothing]] with
    override def lift(l: Left[E, Nothing]): Response = l.value
