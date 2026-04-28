/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.http4s

import io.circe.Encoder

import meltkit.codec.BodyEncoder

/** Provides a [[meltkit.codec.BodyEncoder]] for any type that has a Circe [[io.circe.Encoder]].
  *
  * Import the given instance to enable typed JSON responses in route handlers:
  *
  * {{{
  * import meltkit.adapter.http4s.CirceBodyEncoder.given
  *
  * case class User(id: Int, name: String) derives io.circe.Codec
  *
  * app.get("api" / "users" / id) { ctx =>
  *   Database.findUser(ctx.params.id).map(user => ctx.json(user))
  * }
  * }}}
  */
object CirceBodyEncoder:
  given [A: Encoder]: BodyEncoder[A] with
    def encode(value: A): String = Encoder[A].apply(value).noSpaces
