/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.forms.codec

/** Decodes a form field's submitted value(s) into a Scala type `A`.
  *
  * Decoupled from any request/`FormData` abstraction — it works on the raw
  * `values` for a field name — so the same codec is shared by the server body
  * decoder (meltkit's `FormDataDecoder`) and the client value encoder
  * ([[melt.runtime.forms.Form.text]]).
  *
  * `values` holds every value submitted for `name` (empty when the field is
  * absent); scalar decoders read `values.headOption`, multi-valued ones (`List`)
  * read them all.
  *
  * Customise a field type by mapping an existing decoder (see also [[FieldCodec]]
  * for the symmetric encode+decode pair):
  * {{{
  * given FieldDecoder[Email] = FieldDecoder[String].emap(Email.parse)
  * }}}
  */
trait FieldDecoder[A]:
  self =>

  def decode(name: String, values: List[String]): Either[String, A]

  /** Map decoded results to a new type `B`. */
  def map[B](f: A => B): FieldDecoder[B] =
    (name, values) => self.decode(name, values).map(f)

  /** Map decoded results to a new type `B`, or a decode error. */
  def emap[B](f: A => Either[String, B]): FieldDecoder[B] =
    (name, values) => self.decode(name, values).flatMap(f)

object FieldDecoder:

  def apply[A](using d: FieldDecoder[A]): FieldDecoder[A] = d

  /** Every [[FieldCodec]] is also a decoder. */
  given [A](using codec: FieldCodec[A]): FieldDecoder[A] = codec
