/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** A typed path parameter that carries its name at the type level.
  *
  * @tparam N the parameter name as a literal `String` type
  * @tparam A the value type decoded from the URL path segment
  */
opaque type PathParam[N <: String, A] = String

object PathParam:
  extension [N <: String, A](p: PathParam[N, A])
    /** Returns the runtime parameter name. */
    def paramName: String = p

/** Creates a typed path parameter.
  *
  * The upper bound `N <: String` causes the compiler to infer the literal
  * singleton type (e.g., `"id"`) for `N` rather than widening to `String`.
  *
  * {{{
  * val id   = param[Int]("id")     // PathParam["id", Int]
  * val slug = param[String]("slug") // PathParam["slug", String]
  * }}}
  */
def param[A, N <: String](name: N): PathParam[N, A] = name
