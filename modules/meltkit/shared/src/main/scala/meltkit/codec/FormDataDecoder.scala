/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.codec

import meltkit.{ BodyError, FormData }

/** Decodes a [[FormData]] into a typed value `A`.
  *
  * Provides a type-safe bridge between raw form fields and application-level
  * case classes. Implementations are typically derived via
  * [[FormDataDecoder.derived]] (Scala 3 `derives` clause).
  *
  * {{{
  * case class LoginForm(username: String, password: String) derives FormDataDecoder
  *
  * app.post("login") { ctx =>
  *   ctx.body.form[LoginForm].flatMap {
  *     case Right(form) => authenticate(form.username, form.password)
  *     case Left(err)   => IO.pure(ctx.badRequest(err))
  *   }
  * }
  * }}}
  */
trait FormDataDecoder[A]:
  def decode(form: FormData): Either[BodyError, A]

object FormDataDecoder:

  /** Summons a [[FormDataDecoder]] from implicit scope. */
  def apply[A](using dec: FormDataDecoder[A]): FormDataDecoder[A] = dec

  /** Creates a [[FormDataDecoder]] from a function. */
  def instance[A](f: FormData => Either[BodyError, A]): FormDataDecoder[A] =
    new FormDataDecoder[A]:
      def decode(form: FormData): Either[BodyError, A] = f(form)

  /** Derives a [[FormDataDecoder]] for a product type (case class) using
    * Scala 3's `Mirror.ProductOf`.
    *
    * Each field is decoded by field name. A scalar / custom field uses a
    * [[FormFieldDecoder]] (read from `FormData.get(fieldName)`); a field whose
    * type is itself a case class with a [[FormDataDecoder]] is decoded from
    * hierarchical keys — `FormData.scoped(fieldName)` strips the `fieldName.`
    * prefix and recurses. Errors are accumulated into
    * [[BodyError.FieldErrors]], keyed by the field they came from, so an action
    * can surface each issue next to its input.
    *
    * {{{
    * case class Address(city: String, zip: String) derives FormDataDecoder
    * case class User(name: String, address: Address) derives FormDataDecoder
    * // decodes name=…&address.city=…&address.zip=…
    * }}}
    */
  inline def derived[A](using m: scala.deriving.Mirror.ProductOf[A]): FormDataDecoder[A] =
    val labels   = constValueLabels[m.MirroredElemLabels]
    val decoders = summonSlotDecoders[m.MirroredElemTypes]
    instance { form =>
      val results: List[(String, Either[String, Any])] = labels.zip(decoders).map { (label, dec) =>
        label -> dec(label, form)
      }
      val fieldErrors: Map[String, List[String]] =
        results.collect { case (label, Left(e)) => label -> List(e) }.toMap
      if fieldErrors.nonEmpty then Left(BodyError.FieldErrors(fieldErrors))
      else
        val values = results.collect { case (_, Right(v)) => v }
        val tuple  = Tuple.fromArray(values.toArray)
        Right(m.fromTuple(tuple.asInstanceOf[m.MirroredElemTypes]))
    }

  private inline def constValueLabels[T <: Tuple]: List[String] =
    inline scala.compiletime.erasedValue[T] match
      case _: EmptyTuple     => Nil
      case _: (head *: tail) =>
        scala.compiletime.constValue[head].asInstanceOf[String] :: constValueLabels[tail]

  /** One decoder per field, dispatching scalar/custom fields to a
    * [[FormFieldDecoder]] and nested case-class fields to a scoped
    * [[FormDataDecoder]].
    */
  private inline def summonSlotDecoders[T <: Tuple]: List[(String, FormData) => Either[String, Any]] =
    inline scala.compiletime.erasedValue[T] match
      case _: EmptyTuple     => Nil
      case _: (head *: tail) =>
        slotDecoder[head] :: summonSlotDecoders[tail]

  private inline def slotDecoder[A]: (String, FormData) => Either[String, Any] =
    scala.compiletime.summonFrom {
      // a scalar or user-provided field type
      case ffd: FormFieldDecoder[A] =>
        (name, form) => ffd.decode(name, form)
      // a nested case class: decode from the `name.`-prefixed sub-form
      case fdd: FormDataDecoder[A] =>
        (name, form) => fdd.decode(form.scoped(name)).left.map(err => s"$name.${ err.message }")
    }

  /** When a [[FormDataDecoder]][A] is in scope, a [[BodyDecoder]][A] is
    * automatically derived.
    *
    * This enables `Endpoint.post("login").body[LoginForm]` +
    * `ctx.body.decode` to work. For direct access without Endpoint,
    * use `ctx.body.form[LoginForm]` instead.
    */
  given [A](using fdd: FormDataDecoder[A]): BodyDecoder[A] with
    def decode(body: String): Either[BodyError, A] =
      FormData.parse(body).flatMap(fdd.decode)
