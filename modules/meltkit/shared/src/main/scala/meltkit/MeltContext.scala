/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.NamedTuple.AnyNamedTuple

/** The handler context provided to each route handler.
  *
  * Concrete implementations are provided by adapters
  * (e.g. `Http4sMeltContext` in `meltkit-adapter-http4s`).
  *
  * @tparam F  the effect type (e.g. `cats.effect.IO`)
  * @tparam P  the [[scala.NamedTuple]] of typed path parameters
  */
trait MeltContext[F[_], P <: AnyNamedTuple]:

  /** The typed path parameters extracted from the URL.
    *
    * {{{
    * val id = param[Int]("id")
    * app.get("users" / id) { ctx =>
    *   ctx.params.id  // Int
    * }
    * }}}
    */
  def params: P

  /** Returns the first value of the named query parameter, if present. */
  def query(name: String): Option[String]

  /** Reads and decodes the request body.
    *
    * Returns `Left(BodyError.DecodeError(...))` when the raw body cannot be
    * parsed and `Left(BodyError.ValidationError(...))` when constraint
    * validation fails.  A `given BodyDecoder[A]` must be in scope; adapters
    * provide these (e.g. `CirceBodyDecoder` in `meltkit-adapter-http4s`).
    */
  def body[A: BodyDecoder]: F[Either[BodyError, A]]

  /** Like [[body]], but automatically returns a 400 Bad Request or
    * 422 Unprocessable Entity response when decoding fails.
    *
    * {{{
    * app.post("users") { ctx =>
    *   for
    *     b    <- ctx.bodyOrBadRequest[CreateUserBody]
    *     user <- Database.createUser(b)
    *   yield ctx.json(user.asJson.noSpaces)
    * }
    * }}}
    */
  def bodyOrBadRequest[A: BodyDecoder]: F[A]

  /** Builds a plain-text 200 response. */
  def text(value: String): Response

  /** Builds a 200 application/json response from a pre-serialised JSON string. */
  def json(body: String): Response

  /** Builds a 400 Bad Request response from a [[BodyError]]. */
  def badRequest(err: BodyError): Response

  /** Builds a 301 or 302 redirect response. */
  def redirect(path: String, permanent: Boolean = false): Response

  /** Builds a 404 Not Found response. */
  def notFound(message: String = "Not Found"): Response
