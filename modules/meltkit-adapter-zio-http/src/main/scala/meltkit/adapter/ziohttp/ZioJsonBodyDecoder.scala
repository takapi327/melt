/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.ziohttp

import meltkit.codec.BodyDecoder
import meltkit.BodyError
import zio.json.{ DecoderOps, JsonDecoder }

/** Supplies a [[meltkit.codec.BodyDecoder]] for any type with a zio-json `JsonDecoder`.
  *
  * Import the given to read a JSON request body in a route handler:
  *
  * {{{
  * import meltkit.adapter.ziohttp.ZioJsonBodyDecoder.given
  *
  * app.post("users") { ctx =>
  *   ctx.body[CreateUser].flatMap {
  *     case Right(user) => ...
  *     case Left(err)   => ZIO.succeed(ctx.badRequest(err))
  *   }
  * }
  * }}}
  *
  * Only hand-written endpoints need this. Server Functions serialise through `melt-runtime`'s
  * `PropsCodec` and form actions through `FormDataDecoder`, so both work without any JSON
  * library on the classpath.
  */
object ZioJsonBodyDecoder:

  given [A: JsonDecoder]: BodyDecoder[A] with

    /** zio-json reports failures as a single string (`.field.sub(expected 'x')`), which is
      * useful in a log but not something to hand a client — so it goes in `detail` and the
      * message stays generic, matching the circe bridge.
      */
    def decode(body: String): Either[BodyError, A] =
      body.fromJson[A].left.map(err => BodyError.DecodeError("Invalid request body", detail = Some(err)))
