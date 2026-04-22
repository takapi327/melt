/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import meltkit.codec.PathParamDecoder
import meltkit.codec.PathParamEncoder

/** A typed path parameter that carries its name at the type level.
  *
  * @tparam N the parameter name as a literal `String` type
  * @tparam A the value type decoded from the URL path segment
  */
final class PathParam[N <: String, A] private[meltkit] (
  val paramName:                    String,
  private[meltkit] val decoder: PathParamDecoder[A],
  private[meltkit] val encoder: PathParamEncoder[A]
)

/** Creates a typed path parameter.
  *
  * `name: String & Singleton` prevents the string literal from being widened
  * to `String`, so `name.type` is inferred as the literal singleton type
  * (e.g., `"id"`). This preserves the parameter name at the type level with
  * a single explicit type argument at the call site.
  *
  * Both a [[PathParamDecoder]] (for server-side URL parsing) and a
  * [[PathParamEncoder]] (for client-side URL generation) must be in scope.
  *
  * {{{
  * val id   = param[Int]("id")      // PathParam["id", Int]
  * val slug = param[String]("slug") // PathParam["slug", String]
  * }}}
  */
def param[A: PathParamDecoder: PathParamEncoder](name: String & Singleton): PathParam[name.type, A] =
  new PathParam(name, summon[PathParamDecoder[A]], summon[PathParamEncoder[A]])
