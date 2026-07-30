/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.env

/** Server-only, type-safe access to private environment variables (secrets, API
  * keys, connection strings).
  *
  * Why this lives in the JVM source set rather than `shared`: its absence from the
  * Scala.js artifact IS the enforcement — referencing it from a browser-compiled
  * `.melt` fails to link on the JS cross-build, which a runtime check or a shared
  * API could not guarantee. Read private env only in server positions (route
  * handlers, hooks, JVM-only code); expose browser-safe values via the generated
  * `PublicEnv` instead.
  *
  * {{{
  * val dbUrl = PrivateEnv.required[String]("DATABASE_URL")
  * val port  = PrivateEnv.optional[Int]("PORT").getOrElse(8080)
  * }}}
  */
object PrivateEnv:

  def get(name: String): Option[String] =
    Option(System.getenv(name)).orElse(sys.props.get(name)).filter(_.nonEmpty)

  def optional[A](name: String)(using d: EnvDecoder[A]): Option[A] =
    get(name).map { raw =>
      d.decode(raw) match
        case Right(a) => a
        // Present-but-undecodable is a config error, not "absent"; returning None
        // would bury the bug under whatever fallback the caller supplies.
        case Left(err) => throw new IllegalStateException(s"Environment variable '$name': $err")
    }

  /** Throws when unset — fail fast at boot rather than serve requests from a
    * half-configured server. */
  def required[A](name: String)(using EnvDecoder[A]): A =
    optional[A](name).getOrElse(
      throw new IllegalStateException(s"Required environment variable '$name' is not set")
    )
