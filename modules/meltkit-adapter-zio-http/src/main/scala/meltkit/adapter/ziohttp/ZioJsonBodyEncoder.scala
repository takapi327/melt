/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.ziohttp

import zio.json.{ EncoderOps, JsonEncoder }

import meltkit.codec.BodyEncoder

/** Supplies a [[meltkit.codec.BodyEncoder]] for any type with a zio-json `JsonEncoder`, so
  * `ctx.ok(value)` / `ctx.created(value)` can return it as JSON.
  *
  * {{{
  * import meltkit.adapter.ziohttp.ZioJsonBodyEncoder.given
  *
  * app.get("users") { ctx => ZIO.serviceWithZIO[UserStore](_.all).map(ctx.ok(_)) }
  * }}}
  */
object ZioJsonBodyEncoder:

  given [A: JsonEncoder]: BodyEncoder[A] with
    def encode(value: A): String = value.toJson
