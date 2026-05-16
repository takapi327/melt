/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** A platform-independent request abstraction.
  *
  * Each adapter provides a concrete implementation:
  *   - `meltkit-node`: wraps `IncomingMessage`
  *   - `meltkit-adapter-http4s`: wraps `org.http4s.Request`
  *
  * Used by [[RequestEvent]] to expose the raw request to [[ServerHook]]
  * handlers.
  */
trait Request[F[_]]:
  def method:  HttpMethod
  def url:     Url
  def headers: Map[String, String]
  def body:    RequestBody[F, Unit]
