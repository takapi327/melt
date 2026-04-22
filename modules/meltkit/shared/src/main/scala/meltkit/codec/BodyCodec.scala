/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.codec

import meltkit.BodyError

/** Combines [[BodyDecoder]] and [[BodyEncoder]] for a type `A`.
  *
  * Useful for types that appear both as a request body and a response body
  * (e.g. a `Todo` that is created and then returned).
  *
  * == Creating a BodyCodec ==
  *
  * {{{
  * // From separate given instances already in scope:
  * import meltkit.adapter.http4s.CirceBodyDecoder.given
  * import meltkit.adapter.http4s.CirceBodyEncoder.given
  *
  * val todoCodec: BodyCodec[Todo] = BodyCodec.of[Todo]
  *
  * // From explicit decoder and encoder:
  * val todoCodec: BodyCodec[Todo] = BodyCodec.from(myDecoder, myEncoder)
  * }}}
  *
  * == Using a BodyCodec as decoder or encoder ==
  *
  * Because `BodyCodec[A]` extends both [[BodyDecoder]][A] and [[BodyEncoder]][A],
  * a `given BodyCodec[A]` satisfies both constraints automatically:
  *
  * {{{
  * given BodyCodec[Todo] = BodyCodec.of[Todo]
  *
  * // Both usages compile with a single given:
  * endpoint.post("todos").body[Todo].response[Todo]
  * }}}
  */
trait BodyCodec[A] extends BodyDecoder[A] with BodyEncoder[A]

object BodyCodec:

  /** Creates a [[BodyCodec]] from explicit [[BodyDecoder]] and [[BodyEncoder]] instances. */
  def from[A](dec: BodyDecoder[A], enc: BodyEncoder[A]): BodyCodec[A] =
    new BodyCodec[A]:
      def decode(body: String): Either[BodyError, A] = dec.decode(body)
      def encode(value: A): String                   = enc.encode(value)

  /** Derives a [[BodyCodec]] from given [[BodyDecoder]] and [[BodyEncoder]] instances in scope. */
  def of[A](using dec: BodyDecoder[A], enc: BodyEncoder[A]): BodyCodec[A] =
    from(dec, enc)

  /** Built-in [[BodyCodec]] for `Unit` (no-op body). */
  given BodyCodec[Unit] with
    def decode(body: String): Either[BodyError, Unit] = Right(())
    def encode(value: Unit): String                   = ""
