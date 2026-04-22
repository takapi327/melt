/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.collection.mutable.ListBuffer
import scala.NamedTuple.AnyNamedTuple

import meltkit.codec.BodyDecoder
import meltkit.codec.PathParamDecoder

/** Adapter-supplied factory that constructs a [[MeltContext]] from decoded
  * path parameters and a body decoder.
  *
  * Each adapter implements this once per incoming request, capturing any
  * adapter-specific data (e.g. the http4s `Request[F]`) in the closure.
  * The shared routing core calls [[build]] with the already-decoded,
  * typed parameter tuple and the endpoint's [[BodyDecoder]];
  * the adapter returns the concrete context.
  *
  * For routes without a body (old-style `app.get` / `app.post`),
  * `B = Unit` and `bodyDecoder = summon[BodyDecoder[Unit]]`.
  */
private[meltkit] trait MeltContextFactory[F[_]]:
  def build[P <: AnyNamedTuple, B](params: P, bodyDecoder: BodyDecoder[B]): MeltContext[F, P, B]

/** Internal representation of a registered route.
  *
  * [[tryHandle]] is a closure produced at registration time that already
  * captures the typed handler and parameter decoders.  Adapters supply:
  *
  *   - `rawValues` — the raw path-parameter strings extracted from the
  *     request URI (in declaration order, one per `param[T]("name")` in
  *     the route's [[PathSpec]])
  *   - `factory`   — an adapter-specific [[MeltContextFactory]] that
  *     builds the concrete [[MeltContext]] from the decoded params
  *
  * Returns `None` when any parameter fails to decode (the route should be
  * treated as unmatched), or `Some(F[Response])` on success.
  */
private[meltkit] final class Route[F[_]](
  val method:    HttpMethod,
  val segments:  List[PathSegment],
  val tryHandle: (List[String], MeltContextFactory[F]) => Option[F[Response]]
)

/** The MeltKit routing DSL.
  *
  * Register route handlers with [[get]], [[post]], [[put]], [[delete]], and
  * [[patch]]. Compose multiple routers with [[route]].
  *
  * {{{
  * val app = MeltKit[IO]()
  *
  * val id = param[Int]("id")
  *
  * app.get("users" / id) { ctx =>
  *   IO.pure(ctx.text(s"User \${ctx.params.id}"))
  * }
  *
  * // mount a sub-router
  * val api = MeltKit[IO]()
  * api.get("ping") { ctx => IO.pure(ctx.text("pong")) }
  * app.route("api", api)
  * }}}
  */
class MeltKit[F[_]]:
  private val _routes = ListBuffer[Route[F]]()

  /** Returns all registered routes. Intended for adapter use only. */
  private[meltkit] def routes: List[Route[F]] = _routes.toList

  private def register[P <: AnyNamedTuple](
    method: HttpMethod,
    spec:   PathSpec[P]
  )(handler: MeltContext[F, P, Unit] => F[Response]): Unit =
    val tryHandle: (List[String], MeltContextFactory[F]) => Option[F[Response]] =
      (rawValues, factory) =>
        val results = spec.paramDecoders.zip(rawValues).map { case ((_, dec), raw) =>
          dec.asInstanceOf[PathParamDecoder[Any]].decode(raw)
        }
        if results.forall(_.isRight) then
          val decoded = results.map(_.getOrElse(sys.error("unreachable")))
          val params  = decoded.foldRight(EmptyTuple: Tuple)(_ *: _).asInstanceOf[P]
          Some(handler(factory.build(params, summon[BodyDecoder[Unit]])))
        else None
    _routes += Route(method, spec.segments, tryHandle)

  def get[P <: AnyNamedTuple](spec: PathSpec[P])(handler: MeltContext[F, P, Unit] => F[Response]): Unit =
    register("GET", spec)(handler)

  def get(path: String)(handler: MeltContext[F, PathSpec.Empty, Unit] => F[Response]): Unit =
    register("GET", PathSpec.fromString(path))(handler)

  def post[P <: AnyNamedTuple](spec: PathSpec[P])(handler: MeltContext[F, P, Unit] => F[Response]): Unit =
    register("POST", spec)(handler)

  def post(path: String)(handler: MeltContext[F, PathSpec.Empty, Unit] => F[Response]): Unit =
    register("POST", PathSpec.fromString(path))(handler)

  def put[P <: AnyNamedTuple](spec: PathSpec[P])(handler: MeltContext[F, P, Unit] => F[Response]): Unit =
    register("PUT", spec)(handler)

  def put(path: String)(handler: MeltContext[F, PathSpec.Empty, Unit] => F[Response]): Unit =
    register("PUT", PathSpec.fromString(path))(handler)

  def delete[P <: AnyNamedTuple](spec: PathSpec[P])(handler: MeltContext[F, P, Unit] => F[Response]): Unit =
    register("DELETE", spec)(handler)

  def delete(path: String)(handler: MeltContext[F, PathSpec.Empty, Unit] => F[Response]): Unit =
    register("DELETE", PathSpec.fromString(path))(handler)

  def patch[P <: AnyNamedTuple](spec: PathSpec[P])(handler: MeltContext[F, P, Unit] => F[Response]): Unit =
    register("PATCH", spec)(handler)

  def patch(path: String)(handler: MeltContext[F, PathSpec.Empty, Unit] => F[Response]): Unit =
    register("PATCH", PathSpec.fromString(path))(handler)

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
    handler: MeltContext[F, P, B] => F[Out]
  )(using functor: Functor[F], lift: ResponseLift[E, Out]): Unit =
    val tryHandle: (List[String], MeltContextFactory[F]) => Option[F[Response]] =
      (rawValues, factory) =>
        val results = ep.spec.paramDecoders.zip(rawValues).map { case ((_, dec), raw) =>
          dec.asInstanceOf[PathParamDecoder[Any]].decode(raw)
        }
        if results.forall(_.isRight) then
          val decoded = results.map(_.getOrElse(sys.error("unreachable")))
          val params  = decoded.foldRight(EmptyTuple: Tuple)(_ *: _).asInstanceOf[P]
          val ctx     = factory.build(params, ep.bodyDecoder)
          Some(functor.map(handler(ctx))(lift.lift))
        else None
    _routes += Route(ep.method, ep.spec.segments, tryHandle)

  /** Mounts a sub-router under a static path prefix.
    *
    * {{{
    * val api = MeltKit[IO]()
    * api.get("users") { ctx => ... }
    *
    * app.route("api", api)  // → GET /api/users
    * }}}
    */
  def route(prefix: String, sub: MeltKit[F]): Unit =
    sub.routes.foreach { r =>
      _routes += Route(r.method, PathSegment.Static(prefix) :: r.segments, r.tryHandle)
    }

/** Extracts a [[Response]] from a handler output `Out`.
  *
  * Allows [[MeltKit.on]] to accept both `F[Response]` and `F[Either[E, Response]]`:
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
