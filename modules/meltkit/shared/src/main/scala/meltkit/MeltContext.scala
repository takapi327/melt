/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.util.NotGiven
import scala.NamedTuple.AnyNamedTuple

import meltkit.codec.BodyDecoder
import meltkit.codec.BodyEncoder

/** The handler context provided to each route handler.
  *
  * Concrete implementations are provided by adapters
  * (e.g. `Http4sMeltContext` in `meltkit-adapter-http4s`).
  *
  * @tparam F  the effect type (e.g. `cats.effect.IO`)
  * @tparam P  the [[scala.NamedTuple]] of typed path parameters
  * @tparam B  the request body type (`Unit` = no body)
  *
  * When `B = Unit` (i.e. the route or endpoint declares no request body),
  * calling [[body]] or [[bodyOrBadRequest]] is a **compile error** —
  * enforced via `NotGiven[B =:= Unit]`.
  *
  * {{{
  * val createTodo = Endpoint.post("api/todos").body[CreateTodoBody].response[Todo]
  * app.on(createTodo) { ctx =>
  *   ctx.body              // F[Either[BodyError, CreateTodoBody]]  OK
  *   ctx.bodyOrBadRequest  // F[CreateTodoBody]                     OK
  * }
  *
  * val getTodos = Endpoint.get("api/todos").response[List[Todo]]
  * app.on(getTodos) { ctx =>
  *   ctx.body              // compile error: No given instance of type NotGiven[Unit =:= Unit]
  * }
  * }}}
  */
trait MeltContext[F[_], P <: AnyNamedTuple, B]:

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

  /** Returns the first value of the named query parameter, if present. */
  def query(name: String): Option[String]

  /** Reads and decodes the request body using the endpoint's [[BodyDecoder]].
    *
    * Only available when `B ≠ Unit` (i.e. the endpoint declares a body type
    * via `.body[B]`).  Returns `Left` when the raw body cannot be decoded.
    */
  def body(using NotGiven[B =:= Unit]): F[Either[BodyError, B]]

  /** Like [[body]], but automatically raises a 400 Bad Request when decoding fails.
    *
    * Only available when `B ≠ Unit`.
    */
  def bodyOrBadRequest(using NotGiven[B =:= Unit]): F[B]

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

  /** Builds a 301 or 302 redirect response. */
  def redirect(path: String, permanent: Boolean = false): PlainResponse

  /** Builds a 404 Not Found response. */
  def notFound(message: String = "Not Found"): NotFound
