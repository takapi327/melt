/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.codec

import melt.runtime.forms.codec.FieldDecoder

import meltkit.FormData

/** Decodes a single form field value into a typed value.
  *
  * Used by [[FormDataDecoder.derived]] to decode each field of a case class.
  * The actual per-type logic lives in the platform-neutral, symmetric
  * [[melt.runtime.forms.codec.FieldCodec]] (shared with the client's
  * `form.text` value encoder); this trait simply binds it to a request
  * [[FormData]] by field name. Provide a custom `FieldCodec`/`FieldDecoder` to
  * support a new field type on both sides at once.
  */
trait FormFieldDecoder[A]:
  /** Decodes the field named `name` from `form`.
    *
    * @return `Right(value)` on success, `Left(errorMessage)` on failure
    */
  def decode(name: String, form: FormData): Either[String, A]

object FormFieldDecoder:

  /** Bridges any runtime [[FieldDecoder]] to a [[FormFieldDecoder]] by reading
    * all submitted values for the field name from the request [[FormData]].
    */
  given [A](using field: FieldDecoder[A]): FormFieldDecoder[A] with
    def decode(name: String, form: FormData): Either[String, A] =
      field.decode(name, form.getAll(name).toList)
