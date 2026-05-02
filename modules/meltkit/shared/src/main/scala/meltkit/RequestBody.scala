/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.util.NotGiven

import meltkit.codec.{ BodyDecoder, FormDataDecoder }

/** Provides format-specific access to the request body.
  *
  * Obtained via [[ServerMeltContext.body]]. Each method reads the raw body
  * string and parses it according to the specified format.
  *
  * This follows the same pattern as the Web standard `Request` API:
  *   - `request.formData()` → `ctx.body.form` / `ctx.body.form[A]`
  *   - `request.json()`     → `ctx.body.json[A]`
  *   - `request.text()`     → `ctx.body.text`
  *
  * {{{
  * app.post("login") { ctx =>
  *   ctx.body.form[LoginForm].flatMap {
  *     case Right(form) => authenticate(form.username, form.password)
  *     case Left(err)   => IO.pure(ctx.badRequest(err))
  *   }
  * }
  * }}}
  *
  * @tparam F the effect type (e.g. `cats.effect.IO`)
  * @tparam B the endpoint body type (`Unit` when no endpoint is used)
  */
trait RequestBody[F[_], B]:

  /** Returns the raw body as a UTF-8 string. */
  def text: F[String]

  /** Parses the body as `application/x-www-form-urlencoded` and returns [[FormData]].
    *
    * {{{
    * app.post("login") { ctx =>
    *   ctx.body.form.flatMap {
    *     case Right(formData) =>
    *       val username = formData.get("username")
    *       ...
    *     case Left(err) => IO.pure(ctx.badRequest(err))
    *   }
    * }
    * }}}
    */
  def form: F[Either[BodyError, FormData]]

  /** Parses the body as `application/x-www-form-urlencoded` and decodes it into `A`.
    *
    * {{{
    * case class LoginForm(username: String, password: String) derives FormDataDecoder
    *
    * app.post("login") { ctx =>
    *   ctx.body.form[LoginForm].flatMap {
    *     case Right(form) => authenticate(form.username, form.password)
    *     case Left(err)   => IO.pure(ctx.badRequest(err))
    *   }
    * }
    * }}}
    */
  def form[A](using FormDataDecoder[A]): F[Either[BodyError, A]]

  /** Parses the body as JSON and decodes it into `A` using the [[BodyDecoder]] in scope.
    *
    * {{{
    * import meltkit.adapter.http4s.CirceBodyDecoder.given
    *
    * app.post("api/todos") { ctx =>
    *   ctx.body.json[CreateTodo].flatMap {
    *     case Right(todo) => todoStore.create(todo).map(ctx.created(_))
    *     case Left(err)   => IO.pure(ctx.badRequest(err))
    *   }
    * }
    * }}}
    */
  def json[A](using BodyDecoder[A]): F[Either[BodyError, A]]

  /** Decodes the body using the [[Endpoint]]'s [[BodyDecoder]].
    *
    * Only available when the endpoint declares a body type via `.body[B]`
    * (i.e. `B ≠ Unit`).
    *
    * {{{
    * val createTodo = Endpoint.post("api/todos").body[CreateTodo].response[Todo]
    * app.on(createTodo) { ctx =>
    *   ctx.body.decode.flatMap {
    *     case Right(todo) => todoStore.create(todo).map(ctx.ok(_))
    *     case Left(err)   => IO.pure(ctx.badRequest(err))
    *   }
    * }
    * }}}
    */
  def decode(using NotGiven[B =:= Unit]): F[Either[BodyError, B]]

  /** Like [[decode]], but raises a 400 Bad Request when decoding fails.
    *
    * Only available when the endpoint declares a body type via `.body[B]`
    * (i.e. `B ≠ Unit`).
    *
    * {{{
    * app.on(createTodo) { ctx =>
    *   ctx.body.decodeOrBadRequest.flatMap { todo =>
    *     todoStore.create(todo).map(ctx.ok(_))
    *   }
    * }
    * }}}
    */
  def decodeOrBadRequest(using NotGiven[B =:= Unit]): F[B]
