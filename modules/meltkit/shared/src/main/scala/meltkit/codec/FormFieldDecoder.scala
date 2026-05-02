/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.codec

import meltkit.FormData

/** Decodes a single form field value into a typed value.
  *
  * Used by [[FormDataDecoder.derived]] to decode each field of a case class.
  * Users can provide custom instances for domain-specific types.
  */
trait FormFieldDecoder[A]:
  /** Decodes the field named `name` from `form`.
    *
    * @return `Right(value)` on success, `Left(errorMessage)` on failure
    */
  def decode(name: String, form: FormData): Either[String, A]

object FormFieldDecoder:

  given FormFieldDecoder[String] with
    def decode(name: String, form: FormData): Either[String, String] =
      form.get(name).toRight(s"Missing required field: $name")

  given FormFieldDecoder[Int] with
    def decode(name: String, form: FormData): Either[String, Int] =
      form.get(name) match
        case None    => Left(s"Missing required field: $name")
        case Some(v) => v.toIntOption.toRight(s"Field '$name' is not a valid integer: $v")

  given FormFieldDecoder[Long] with
    def decode(name: String, form: FormData): Either[String, Long] =
      form.get(name) match
        case None    => Left(s"Missing required field: $name")
        case Some(v) => v.toLongOption.toRight(s"Field '$name' is not a valid long: $v")

  given FormFieldDecoder[Double] with
    def decode(name: String, form: FormData): Either[String, Double] =
      form.get(name) match
        case None    => Left(s"Missing required field: $name")
        case Some(v) => v.toDoubleOption.toRight(s"Field '$name' is not a valid number: $v")

  given FormFieldDecoder[Boolean] with
    def decode(name: String, form: FormData): Either[String, Boolean] =
      form.get(name) match
        case None                              => Right(false) // unchecked checkbox = absent
        case Some("true") | Some("1")          => Right(true)
        case Some("false") | Some("0") | Some("") => Right(false)
        case Some(v)                           => Left(s"Field '$name' is not a valid boolean: $v")

  /** Optional fields: missing -> `None`, present -> `Some(decoded)`. */
  given [A](using inner: FormFieldDecoder[A]): FormFieldDecoder[Option[A]] with
    def decode(name: String, form: FormData): Either[String, Option[A]] =
      if !form.has(name) then Right(None)
      else inner.decode(name, form).map(Some(_))

  /** Multi-value fields: `<select multiple>`, repeated checkboxes. */
  given [A](using inner: FormFieldDecoder[A]): FormFieldDecoder[List[A]] with
    def decode(name: String, form: FormData): Either[String, List[A]] =
      val values  = form.getAll(name)
      val results = values.map { v =>
        inner.decode(name, FormData(Map(name -> List(v))))
      }
      val errors = results.collect { case Left(e) => e }
      if errors.nonEmpty then Left(errors.mkString("; "))
      else Right(results.collect { case Right(v) => v })
