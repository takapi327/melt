/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.NamedTuple.AnyNamedTuple
import scala.collection.mutable.ListBuffer

/** Internal representation of a registered route.
  *
  * The parameter type `P` is erased at the storage level; adapters cast back
  * to the concrete type when constructing the [[MeltContext]] for a matched
  * request.
  */
private[meltkit] final class Route[F[_]](
  val method:   String,
  val segments: List[PathSegment],
  private[meltkit] val rawHandler: Any // MeltContext[F, P] => F[Response], P erased
):
  private[meltkit] def unsafeHandle[P <: AnyNamedTuple](ctx: MeltContext[F, P]): F[Response] =
    rawHandler.asInstanceOf[MeltContext[F, P] => F[Response]](ctx)

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
    method: String,
    spec:   PathSpec[P]
  )(handler: MeltContext[F, P] => F[Response]): Unit =
    _routes += Route(method, spec.segments, handler)

  def get[P <: AnyNamedTuple](spec: PathSpec[P])(handler: MeltContext[F, P] => F[Response]): Unit =
    register("GET", spec)(handler)

  def get(path: String)(handler: MeltContext[F, PathSpec.Empty] => F[Response]): Unit =
    register("GET", PathSpec.fromString(path))(handler)

  def post[P <: AnyNamedTuple](spec: PathSpec[P])(handler: MeltContext[F, P] => F[Response]): Unit =
    register("POST", spec)(handler)

  def post(path: String)(handler: MeltContext[F, PathSpec.Empty] => F[Response]): Unit =
    register("POST", PathSpec.fromString(path))(handler)

  def put[P <: AnyNamedTuple](spec: PathSpec[P])(handler: MeltContext[F, P] => F[Response]): Unit =
    register("PUT", spec)(handler)

  def put(path: String)(handler: MeltContext[F, PathSpec.Empty] => F[Response]): Unit =
    register("PUT", PathSpec.fromString(path))(handler)

  def delete[P <: AnyNamedTuple](spec: PathSpec[P])(handler: MeltContext[F, P] => F[Response]): Unit =
    register("DELETE", spec)(handler)

  def delete(path: String)(handler: MeltContext[F, PathSpec.Empty] => F[Response]): Unit =
    register("DELETE", PathSpec.fromString(path))(handler)

  def patch[P <: AnyNamedTuple](spec: PathSpec[P])(handler: MeltContext[F, P] => F[Response]): Unit =
    register("PATCH", spec)(handler)

  def patch(path: String)(handler: MeltContext[F, PathSpec.Empty] => F[Response]): Unit =
    register("PATCH", PathSpec.fromString(path))(handler)

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
      _routes += Route(r.method, PathSegment.Static(prefix) :: r.segments, r.rawHandler)
    }
