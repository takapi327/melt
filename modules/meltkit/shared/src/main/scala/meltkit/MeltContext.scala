/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.NamedTuple.AnyNamedTuple

/** The handler context provided to each route handler.
  *
  * Concrete implementations are provided by adapters
  * (e.g. `Http4sMeltContext` in `meltkit-adapter-http4s`).
  *
  * @tparam F  the effect type (e.g. `cats.effect.IO`)
  * @tparam P  the [[scala.NamedTuple]] of typed path parameters
  */
trait MeltContext[F[_], P <: AnyNamedTuple]:

  /** The typed path parameters extracted from the URL.
    *
    * {{{
    * val id = param[Int]("id")
    * app.get("users" / id) { ctx =>
    *   ctx.params.id  // Int
    * }
    * }}}
    */
  def params: P

  /** Returns the first value of the named query parameter, if present. */
  def query(name: String): Option[String]

  /** Builds a plain-text 200 response. */
  def text(value: String): Response

  /** Builds a 301 or 302 redirect response. */
  def redirect(path: String, permanent: Boolean = false): Response

  /** Builds a 404 Not Found response. */
  def notFound(message: String = "Not Found"): Response
