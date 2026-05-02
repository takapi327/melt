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
    * Each field is read from `FormData.get(fieldName)` and decoded via
    * [[FormFieldDecoder]]. Missing required fields and type conversion
    * errors are accumulated into [[BodyError.ValidationError]].
    *
    * {{{
    * case class CreateTodo(text: String, priority: Int) derives FormDataDecoder
    * }}}
    */
  inline def derived[A](using m: scala.deriving.Mirror.ProductOf[A]): FormDataDecoder[A] =
    val labels   = constValueLabels[m.MirroredElemLabels]
    val decoders = summonFieldDecoders[m.MirroredElemTypes]
    instance { form =>
      val results: List[Either[String, Any]] = labels.zip(decoders).map { (label, dec) =>
        dec.asInstanceOf[FormFieldDecoder[Any]].decode(label, form) match
          case Right(v)  => Right(v)
          case Left(msg) => Left(msg)
      }
      val errors = results.collect { case Left(e) => e }
      if errors.nonEmpty then Left(BodyError.ValidationError(errors))
      else
        val values = results.collect { case Right(v) => v }
        val tuple  = Tuple.fromArray(values.toArray)
        Right(m.fromTuple(tuple.asInstanceOf[m.MirroredElemTypes]))
    }

  private inline def constValueLabels[T <: Tuple]: List[String] =
    inline scala.compiletime.erasedValue[T] match
      case _: EmptyTuple     => Nil
      case _: (head *: tail) =>
        scala.compiletime.constValue[head].asInstanceOf[String] :: constValueLabels[tail]

  private inline def summonFieldDecoders[T <: Tuple]: List[FormFieldDecoder[?]] =
    inline scala.compiletime.erasedValue[T] match
      case _: EmptyTuple     => Nil
      case _: (head *: tail) =>
        scala.compiletime.summonInline[FormFieldDecoder[head]] :: summonFieldDecoders[tail]

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
