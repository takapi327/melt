/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.codec

import meltkit.BodyError

/** Decodes a raw request body string into a value of type `A`.
  *
  * `meltkit` defines only this interface; concrete implementations are provided
  * by adapters (e.g. `meltkit-adapter-http4s` provides `CirceBodyDecoder`).
  *
  * {{{
  * // In user code (after importing a given from the adapter):
  * import meltkit.adapter.http4s.CirceBodyDecoder.given
  *
  * app.post("users") { ctx =>
  *   ctx.body[CreateUserBody].flatMap {
  *     case Right(body) => ...
  *     case Left(err)   => IO.pure(ctx.badRequest(err))
  *   }
  * }
  * }}}
  */
trait BodyDecoder[A]:
  def decode(body: String): Either[BodyError, A]

object BodyDecoder:
  /** No-op instance for endpoints with no request body (`B = Unit`). */
  given BodyDecoder[Unit] with
    def decode(body: String): Either[BodyError, Unit] = Right(())
