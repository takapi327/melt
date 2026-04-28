/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.fetch

import scala.concurrent.Future

/** An HTTP response returned by [[meltkit.Fetch]], corresponding to the
  * [[https://nodejs.org/api/globals.html#response Response]] interface in the Node.js fetch API.
  *
  * {{{
  * Fetch("https://api.example.com/users")
  *   .flatMap(_.text())
  *   .map(json => decode[List[User]](json))
  * }}}
  *
  * @param status     HTTP status code (e.g. 200, 404)
  * @param statusText HTTP status text (e.g. "OK"). Always empty on the JVM platform.
  * @param ok         `true` when [[status]] is in the 200–299 range
  * @param url        Final URL after any redirects
  * @param headers    Response headers
  */
final class Response(
  val status:        Int,
  val statusText:    String,
  val ok:            Boolean,
  val url:           String,
  val headers:       Headers,
  private val _text: () => Future[String]
):

  /** Reads the response body as a string.
    *
    * Equivalent to `response.text()` in the Node.js fetch API.
    */
  def text(): Future[String] = _text()
