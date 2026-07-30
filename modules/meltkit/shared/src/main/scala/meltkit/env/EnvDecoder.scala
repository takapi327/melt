/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.env

/** Decodes an environment-variable string into a typed value.
  *
  * Kept in the shared source set — unlike its consumer [[meltkit.env.PrivateEnv]],
  * which is JVM-only — because a decoder carries no secret, so there is no reason to
  * withhold it from the browser side.
  */
trait EnvDecoder[A]:
  def decode(raw: String): Either[String, A]

object EnvDecoder:
  def apply[A](using d: EnvDecoder[A]): EnvDecoder[A] = d

  given EnvDecoder[String] with
    def decode(raw: String): Either[String, String] = Right(raw)

  given EnvDecoder[Int] with
    def decode(raw: String): Either[String, Int] =
      raw.toIntOption.toRight(s"'$raw' is not a valid Int")

  given EnvDecoder[Long] with
    def decode(raw: String): Either[String, Long] =
      raw.toLongOption.toRight(s"'$raw' is not a valid Long")

  given EnvDecoder[Boolean] with
    def decode(raw: String): Either[String, Boolean] =
      raw.trim.toLowerCase match
        case "true" | "1" | "yes" | "on"  => Right(true)
        case "false" | "0" | "no" | "off" => Right(false)
        case _                            => Left(s"'$raw' is not a valid Boolean")
