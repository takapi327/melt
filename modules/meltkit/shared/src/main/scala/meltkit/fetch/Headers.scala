/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.fetch

/** Immutable response headers, corresponding to the
  * [[https://nodejs.org/api/globals.html#headers Headers]] interface in the Node.js fetch API.
  *
  * Header names are treated case-insensitively as required by the HTTP specification.
  * Multiple values for the same header name (e.g. `Set-Cookie`) are preserved as a `List`.
  *
  * {{{
  * val ct      = headers.get("content-type")
  * val cookies = headers.getAll("set-cookie")
  * val all     = headers.entries
  * }}}
  */
final case class Headers private (entries: Map[String, List[String]]):

  /** Returns the first value for the given header name, or [[None]] if absent.
    *
    * Equivalent to `Headers.get()` in the Node.js fetch API.
    */
  def get(name: String): Option[String] =
    entries.get(name.toLowerCase).flatMap(_.headOption)

  /** Returns all values for the given header name, or an empty list if absent.
    *
    * Use this for headers that may carry multiple values, such as `Set-Cookie`.
    */
  def getAll(name: String): List[String] =
    entries.getOrElse(name.toLowerCase, Nil)

  /** Returns `true` if the given header is present. */
  def has(name: String): Boolean =
    entries.contains(name.toLowerCase)

object Headers:
  def apply(entries: Map[String, List[String]]): Headers = new Headers(entries)
