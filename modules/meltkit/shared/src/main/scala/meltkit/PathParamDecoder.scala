/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** Decodes a raw path-segment string into a value of type `A`.
  *
  * Standard instances are provided in the companion object for `String`,
  * `Int`, and `Long`.  Additional instances (e.g. for Iron-refined types or
  * `java.util.UUID`) can be supplied by the user as `given` definitions.
  */
trait PathParamDecoder[A]:
  def decode(s: String): Either[String, A]

object PathParamDecoder:
  given PathParamDecoder[String] with
    def decode(s: String): Either[String, String] = Right(s)

  given PathParamDecoder[Int] with
    def decode(s: String): Either[String, Int] =
      s.toIntOption.toRight(s"'$s' is not a valid Int")

  given PathParamDecoder[Long] with
    def decode(s: String): Either[String, Long] =
      s.toLongOption.toRight(s"'$s' is not a valid Long")
