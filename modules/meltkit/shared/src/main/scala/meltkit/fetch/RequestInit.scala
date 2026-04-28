/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.fetch

/** Options for a [[meltkit.Fetch]] request, corresponding to the
  * [[https://nodejs.org/api/globals.html#requestinit RequestInit]] interface in the Node.js fetch API.
  *
  * {{{
  * RequestInit(
  *   method  = "POST",
  *   headers = Map("Content-Type" -> "application/json"),
  *   body    = Some("""{"name":"Alice"}""")
  * )
  * }}}
  */
final case class RequestInit(
  method:  String              = "GET",
  headers: Map[String, String] = Map.empty,
  body:    Option[String]      = None
)
