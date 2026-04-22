/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.NamedTuple.AnyNamedTuple

import meltkit.codec.BodyDecoder
import meltkit.codec.BodyEncoder
import meltkit.codec.PathParamEncoder

/** A typed endpoint definition.
  *
  * @tparam P path params as a [[scala.NamedTuple]] (e.g. `(id: Int)`)
  * @tparam B request body type (`Unit` = no body)
  * @tparam E error response type (`Nothing` = no `errorOut`)
  * @tparam R success response type
  *
  * Endpoints are created via the [[Endpoint]] companion DSL and registered
  * with [[MeltKit.on]]:
  *
  * {{{
  * val userId = param[Int]("id")
  *
  * val getTodos   = Endpoint.get("api/todos").response[List[Todo]]
  * val createTodo = Endpoint.post("api/todos").body[CreateTodoBody].status(201).response[Todo]
  * val getUser    = Endpoint.get("api/users" / userId).errorOut[NotFound].response[User]
  * val deleteTodo = Endpoint.delete("api/todos" / todoId).status(204)
  * }}}
  */
final case class Endpoint[P <: AnyNamedTuple, B, E, R] private[meltkit] (
  val method:                           String,
  val spec:                             PathSpec[P],
  val statusCode:                       StatusCode,
  private[meltkit] val bodyDecoder:     BodyDecoder[B],
  private[meltkit] val responseEncoder: BodyEncoder[R]
):

  /** Changes the success status code (default: 200).
    * Preserves all type parameters — uses `copy` internally.
    */
  def status(code: StatusCode): Endpoint[P, B, E, R] =
    copy(statusCode = code)

  /** Adds a typed request body. Requires a [[BodyDecoder]][B2] in scope.
    * Changes the `B` type parameter — a new instance is constructed.
    */
  def body[B2](using dec: BodyDecoder[B2]): Endpoint[P, B2, E, R] =
    Endpoint(method, spec, statusCode, dec, responseEncoder)

  /** Declares the error response type for this endpoint.
    * Multiple error types can be expressed as a union: `.errorOut[NotFound | Unauthorized]`.
    * Changes the `E` type parameter — a new instance is constructed.
    */
  def errorOut[E2 <: Response]: Endpoint[P, B, E2, R] =
    Endpoint(method, spec, statusCode, bodyDecoder, responseEncoder)

  /** Sets the success response type. Requires a [[BodyEncoder]][R2] in scope.
    * Changes the `R` type parameter — a new instance is constructed.
    */
  def response[R2](using enc: BodyEncoder[R2]): Endpoint[P, B, E, R2] =
    Endpoint(method, spec, statusCode, bodyDecoder, enc)

  /** Generates the URL path for this endpoint by substituting path parameters.
    *
    * {{{
    * val getUser = Endpoint.get("api/users" / userId).response[User]
    * getUser.url((id = 42))      // "/api/users/42"
    *
    * val getTodos = Endpoint.get("api/todos").response[List[Todo]]
    * getTodos.url(EmptyTuple)    // "/api/todos"
    * }}}
    */
  def url(params: P): String =
    val values   = tupleToList(params.asInstanceOf[Tuple])
    val encoders = spec.paramEncoders
    var idx      = 0
    val parts    = spec.segments.map {
      case PathSegment.Static(s) => s
      case PathSegment.Param(_) =>
        val (_, enc) = encoders(idx)
        val value    = values(idx)
        idx += 1
        enc.asInstanceOf[PathParamEncoder[Any]].encode(value)
    }
    "/" + parts.mkString("/")

  private def tupleToList(t: Tuple): List[Any] = t match
    case EmptyTuple => Nil
    case h *: tail  => h :: tupleToList(tail)

/** Companion object — serves as the top-level DSL for constructing [[Endpoint]] values.
  *
  * Each method returns an `Endpoint[P, Unit, Nothing, Unit]` — the default
  * response type is `Unit` and can be changed with `.response[R]`.
  *
  * {{{
  * Endpoint.get("ping")                             // Endpoint[Empty, Unit, Nothing, Unit]
  * Endpoint.get("api/todos").response[List[Todo]]   // Endpoint[Empty, Unit, Nothing, List[Todo]]
  * Endpoint.post("api/todos").body[Body].response[Todo]
  * }}}
  */
object Endpoint:

  private def make[P <: AnyNamedTuple](method: String, spec: PathSpec[P]): Endpoint[P, Unit, Nothing, Unit] =
    Endpoint(method, spec, 200, summon[BodyDecoder[Unit]], summon[BodyEncoder[Unit]])

  def get(path: String): Endpoint[PathSpec.Empty, Unit, Nothing, Unit] =
    make("GET", PathSpec.fromString(path))

  def get[P <: AnyNamedTuple](spec: PathSpec[P]): Endpoint[P, Unit, Nothing, Unit] =
    make("GET", spec)

  def post(path: String): Endpoint[PathSpec.Empty, Unit, Nothing, Unit] =
    make("POST", PathSpec.fromString(path))

  def post[P <: AnyNamedTuple](spec: PathSpec[P]): Endpoint[P, Unit, Nothing, Unit] =
    make("POST", spec)

  def put(path: String): Endpoint[PathSpec.Empty, Unit, Nothing, Unit] =
    make("PUT", PathSpec.fromString(path))

  def put[P <: AnyNamedTuple](spec: PathSpec[P]): Endpoint[P, Unit, Nothing, Unit] =
    make("PUT", spec)

  def delete(path: String): Endpoint[PathSpec.Empty, Unit, Nothing, Unit] =
    make("DELETE", PathSpec.fromString(path))

  def delete[P <: AnyNamedTuple](spec: PathSpec[P]): Endpoint[P, Unit, Nothing, Unit] =
    make("DELETE", spec)

  def patch(path: String): Endpoint[PathSpec.Empty, Unit, Nothing, Unit] =
    make("PATCH", PathSpec.fromString(path))

  def patch[P <: AnyNamedTuple](spec: PathSpec[P]): Endpoint[P, Unit, Nothing, Unit] =
    make("PATCH", spec)
