/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.util.NotGiven
import scala.NamedTuple.AnyNamedTuple

/** Server-side extension of [[MeltContext]] that adds request-body access.
  *
  * Only server adapters (e.g. `Http4sMeltContext`) implement this trait.
  * The browser adapter (`BrowserMeltContext`) extends [[MeltContext]] only,
  * because browser navigation routes carry no request body.
  *
  * Route handlers registered with [[MeltKit.on]] receive a `ServerMeltContext`
  * so they can safely call [[body]] / [[bodyOrBadRequest]].
  * Handlers registered with `app.get` / `app.post` / … receive a plain
  * [[MeltContext]] and cannot access the body at compile time.
  *
  * @tparam F the effect type (e.g. `cats.effect.IO`)
  * @tparam P the [[scala.NamedTuple]] of typed path parameters
  * @tparam B the request body type (`Unit` = no body)
  */
trait ServerMeltContext[F[_], P <: AnyNamedTuple, B] extends MeltContext[F, P, B]:

  /** Reads and decodes the request body using the endpoint's [[codec.BodyDecoder]].
    *
    * Only available when `B ≠ Unit` (i.e. the endpoint declares a body type
    * via `.body[B]`).  Returns `Left` when the raw body cannot be decoded.
    */
  def body(using NotGiven[B =:= Unit]): F[Either[BodyError, B]]

  /** Like [[body]], but automatically raises a 400 Bad Request when decoding fails.
    *
    * Only available when `B ≠ Unit`.
    */
  def bodyOrBadRequest(using NotGiven[B =:= Unit]): F[B]
