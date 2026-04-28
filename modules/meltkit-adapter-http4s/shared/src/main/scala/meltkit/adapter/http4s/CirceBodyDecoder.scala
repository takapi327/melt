/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.http4s

import io.circe.parser
import io.circe.Decoder
import meltkit.codec.BodyDecoder
import meltkit.BodyError

/** Provides a [[meltkit.codec.BodyDecoder]] for any type that has a Circe [[io.circe.Decoder]].
  *
  * Import the given instance to enable JSON body parsing in route handlers:
  *
  * {{{
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
object CirceBodyDecoder:
  given [A: Decoder]: BodyDecoder[A] with
    def decode(body: String): Either[BodyError, A] =
      parser.decode[A](body).left.map {
        case e: io.circe.ParsingFailure  => BodyError.DecodeError("Invalid JSON", detail = Some(e.getMessage))
        case e: io.circe.DecodingFailure => BodyError.DecodeError("Invalid request body", detail = Some(e.getMessage))
      }
