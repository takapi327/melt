/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.testkit

/** Builds the `use:enhance` JSON envelope a server would return, so a
  * [[FetchStub]] can drive the client without hand-writing JSON. The shape
  * mirrors `meltkit.ActionResult.toJson`.
  *
  * {{{
  * FetchStub.install(body = EnhanceResult.failure(422, """{"errors":["invalid email"]}"""))
  * FetchStub.install(body = EnhanceResult.redirect("/dashboard"))
  * }}}
  */
object EnhanceResult:

  /** A `success` envelope carrying the form `data` (raw JSON, default `{}`). */
  def success(dataJson: String = "{}"): String =
    s"""{"type":"success","status":200,"data":$dataJson}"""

  /** A `failure` envelope with a status and the form `data`. */
  def failure(status: Int, dataJson: String = "{}"): String =
    s"""{"type":"failure","status":$status,"data":$dataJson}"""

  /** A `redirect` envelope (the client navigates to `location`). */
  def redirect(location: String, status: Int = 303): String =
    s"""{"type":"redirect","status":$status,"location":"$location"}"""
