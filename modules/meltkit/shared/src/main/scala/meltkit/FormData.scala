/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import meltkit.codec.BodyDecoder

/** Represents parsed form data from an `application/x-www-form-urlencoded` request body.
  *
  * Fields are stored as `Map[String, List[String]]` to support multiple values
  * for the same field name (e.g. `<select multiple>`, repeated checkboxes).
  *
  * {{{
  * app.post("login") { ctx =>
  *   ctx.body.form.flatMap {
  *     case Right(form) =>
  *       val username = form.get("username")   // Option[String]
  *       val tags     = form.getAll("tag")     // List[String]
  *       ...
  *     case Left(err) => IO.pure(ctx.badRequest(err))
  *   }
  * }
  * }}}
  */
final case class FormData(fields: Map[String, List[String]]):

  /** Returns the first value for the given field name, if present. */
  def get(name: String): Option[String] =
    fields.get(name).flatMap(_.headOption)

  /** Returns the first value for the given field name,
    * or `Left(BodyError)` if the field is missing.
    */
  def require(name: String): Either[BodyError, String] =
    get(name).toRight(
      BodyError.DecodeError(s"Missing required form field: $name")
    )

  /** Returns all values for the given field name (empty list if absent). */
  def getAll(name: String): List[String] =
    fields.getOrElse(name, List.empty)

  /** Returns a flat `Map[String, String]` using the first value for each field.
    *
    * Useful for simple forms where each field has exactly one value.
    */
  def toMap: Map[String, String] =
    fields.collect { case (k, v :: _) => k -> v }

  /** Returns `true` if the given field name exists (even if the value is empty). */
  def has(name: String): Boolean =
    fields.contains(name)

object FormData:

  val empty: FormData = FormData(Map.empty)

  /** Parses a `application/x-www-form-urlencoded` body string into [[FormData]].
    *
    * Decoding rules (per WHATWG URL Standard):
    *   - `+` is decoded as space (` `) by `URLDecoder`
    *   - `%XX` sequences are percent-decoded as UTF-8
    *   - Empty values are preserved (e.g. `name=` -> `"name" -> ""`)
    *   - Fields without `=` are treated as having an empty value
    *   - Values containing `=` are preserved (e.g. `token=abc==` -> `"token" -> "abc=="`)
    *
    * {{{
    * FormData.parse("name=Alice&tag=scala&tag=fp")
    * // Right(FormData(Map("name" -> List("Alice"), "tag" -> List("scala", "fp"))))
    * }}}
    */
  def parse(body: String): Either[BodyError, FormData] =
    if body.isBlank then Right(empty)
    else
      try
        val fields = body
          .split('&')
          .toList
          .map { pair =>
            pair.indexOf('=') match
              case -1  => decodeComponent(pair) -> ""
              case idx => decodeComponent(pair.substring(0, idx)) -> decodeComponent(pair.substring(idx + 1))
          }
          .groupBy(_._1)
          .map { case (k, pairs) => k -> pairs.map(_._2) }
        Right(FormData(fields))
      catch
        case e: IllegalArgumentException =>
          Left(BodyError.DecodeError(
            "Invalid form data",
            detail = Some(e.getMessage)
          ))

  private def decodeComponent(s: String): String =
    java.net.URLDecoder.decode(s, "UTF-8")

  /** Built-in [[BodyDecoder]] for `application/x-www-form-urlencoded` bodies.
    *
    * This given enables `Endpoint.post("login").body[FormData]` to work.
    * For direct access without Endpoint, use `ctx.body.form` instead.
    */
  given BodyDecoder[FormData] with
    def decode(body: String): Either[BodyError, FormData] = parse(body)
