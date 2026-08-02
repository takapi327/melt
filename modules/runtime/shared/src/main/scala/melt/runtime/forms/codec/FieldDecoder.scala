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

  /** Decodes a repeated field (e.g. `?tag=a&tag=b`) into a `Set`, deduping. An
    * absent field decodes to the empty set (mirroring how `List` decodes to
    * `Nil`). */
  given [A](using list: FieldDecoder[List[A]]): FieldDecoder[Set[A]] with
    def decode(name: String, values: List[String]): Either[String, Set[A]] =
      list.decode(name, values).map(_.toSet)

  /** Decodes a single whitespace-delimited value (e.g. OIDC `scope=openid profile`)
    * into a `Set`, decoding each token with the element decoder. An absent or blank
    * value decodes to the empty set. Surrounding and repeated whitespace is ignored.
    *
    * Unlike the [[given_FieldDecoder_A]] `Set` instance (which reads *repeated*
    * parameters), this reads a *single* value and splits it — so it is provided as
    * an explicit constructor rather than a `given`, e.g.
    * `given FieldDecoder[Set[Scope]] = FieldDecoder.spaceDelimited[Scope]`.
    */
  def spaceDelimited[A](using elem: FieldDecoder[A]): FieldDecoder[Set[A]] =
    (name, values) =>
      values.headOption.map(_.trim).filter(_.nonEmpty) match
        case None => Right(Set.empty)
        case Some(joined) =>
          joined.split("\\s+").toList.foldLeft[Either[String, Set[A]]](Right(Set.empty)) { (acc, token) =>
            for
              set     <- acc
              decoded <- elem.decode(name, List(token))
            yield set + decoded
          }

  /** A string-keyed map field is treated as server-populated output — e.g. the
    * per-field validation issues a form model carries back
    * (`errors: Map[String, List[String]]`). A flat form submission never contains
    * it, so it decodes to an empty map (mirroring how a `List` field decodes to
    * `Nil` when absent). The server fills it in on the response. */
  given [V]: FieldDecoder[Map[String, V]] with
    def decode(name: String, values: List[String]): Either[String, Map[String, V]] = Right(Map.empty)
