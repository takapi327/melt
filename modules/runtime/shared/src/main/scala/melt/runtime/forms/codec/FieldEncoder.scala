/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.forms.codec

/** Encodes a Scala value into a form field's wire value(s).
  *
  * The counterpart of [[FieldDecoder]]: it produces the string(s) that seed an
  * `<input value>` (via [[melt.runtime.forms.Form.text]]) so that what is
  * rendered round-trips back through the matching decoder. Most types encode to a
  * single value; multi-valued types (`List`) may encode to several.
  *
  * Customise by contramapping an existing encoder (or define both directions at
  * once with [[FieldCodec]]):
  * {{{
  * given FieldEncoder[Email] = FieldEncoder[String].contramap(_.value)
  * }}}
  */
trait FieldEncoder[A]:
  self =>

  def encode(value: A): List[String]

  /** The single wire value for a scalar field (empty string when none). */
  def encodeValue(value: A): String = encode(value).headOption.getOrElse("")

  /** Adapt this encoder to a new input type `B`. */
  def contramap[B](f: B => A): FieldEncoder[B] =
    (value: B) => self.encode(f(value))

object FieldEncoder:

  def apply[A](using e: FieldEncoder[A]): FieldEncoder[A] = e

  /** Every [[FieldCodec]] is also an encoder. */
  given [A](using codec: FieldCodec[A]): FieldEncoder[A] = codec
