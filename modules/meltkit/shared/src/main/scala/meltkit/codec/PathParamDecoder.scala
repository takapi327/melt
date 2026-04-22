/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.codec

/** Decodes a raw path-segment string into a value of type `A`.
  *
  * Built-in instances for `String`, `Int`, and `Long` are provided via
  * [[PathParamCodec]], which extends both this trait and [[PathParamEncoder]].
  * Additional instances (e.g. for Iron-refined types or `java.util.UUID`)
  * can be supplied as `given` definitions.
  *
  * == Combinators ==
  *
  * {{{
  * // map — infallible transform (e.g. wrapping an opaque type)
  * given PathParamDecoder[UserId] = PathParamDecoder[Int].map(UserId(_))
  *
  * // emap — fallible transform (e.g. additional validation)
  * given PathParamDecoder[Port] =
  *   PathParamDecoder[Int].emap(n =>
  *     if n > 0 && n <= 65535 then Right(Port(n))
  *     else Left(s"'$n' is not a valid port number")
  *   )
  * }}}
  */
trait PathParamDecoder[A]:
  def decode(s: String): Either[String, A]

  /** Transforms the decoded value with an infallible function. */
  def map[B](f: A => B): PathParamDecoder[B] =
    s => decode(s).map(f)

  /** Transforms the decoded value with a function that may fail.
    *
    * `Left(message)` is treated as a decode error.
    */
  def emap[B](f: A => Either[String, B]): PathParamDecoder[B] =
    s => decode(s).flatMap(f)

object PathParamDecoder:
  given PathParamDecoder[String] with
    def decode(s: String): Either[String, String] = Right(s)

  given PathParamDecoder[Int] with
    def decode(s: String): Either[String, Int] =
      s.toIntOption.toRight(s"'$s' is not a valid Int")

  given PathParamDecoder[Long] with
    def decode(s: String): Either[String, Long] =
      s.toLongOption.toRight(s"'$s' is not a valid Long")
