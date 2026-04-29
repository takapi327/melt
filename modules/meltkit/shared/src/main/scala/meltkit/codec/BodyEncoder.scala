/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.codec

/** Encodes a value of type `A` into a JSON string.
  *
  * `meltkit` defines only this interface; concrete implementations are provided
  * by adapter modules.
  *
  * A `given BodyEncoder[String]` is provided in the companion object so that
  * raw JSON strings remain usable with [[meltkit.MeltContext.json]] without any import.
  *
  * {{{
  * // Typed value (requires a BodyEncoder given from an adapter module in scope):
  * ctx.json(user)
  *
  * // Raw JSON string (always works):
  * ctx.json("""{"id":1}""")
  * }}}
  */
trait BodyEncoder[A]:
  def encode(value: A): String

object BodyEncoder:
  /** Pass-through instance so that `ctx.json(rawString)` always compiles. */
  given BodyEncoder[String] with
    def encode(value: String): String = value

  /** No-op instance for endpoints with no response body (`R = Unit`). */
  given BodyEncoder[Unit] with
    def encode(value: Unit): String = ""
