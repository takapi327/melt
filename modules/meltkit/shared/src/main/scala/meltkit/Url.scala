/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** A platform-independent URL abstraction.
  *
  * Used by [[RequestEvent]] and [[Request]] to expose the parsed URL
  * of the incoming request.
  *
  * @param pathname     the path component (e.g. `"/users/42"`)
  * @param searchParams query parameters as a multi-valued map
  * @param origin       the origin (e.g. `"https://example.com"`)
  * @param hash         the fragment identifier (usually empty on the server)
  */
case class Url(
  pathname:     String,
  searchParams: Map[String, List[String]],
  origin:       String,
  hash:         String = ""
):
  /** Returns the first value of the named query parameter, if present. */
  def query(name: String): Option[String] =
    searchParams.get(name).flatMap(_.headOption)

  /** Returns all values of the named query parameter. */
  def queryAll(name: String): List[String] =
    searchParams.getOrElse(name, Nil)

object Url:

  /** Parses a raw URL string (path + optional query string) with the given origin.
    *
    * {{{
    * Url.parse("/users/42?role=admin&role=dev", "https://example.com")
    * // Url("/users/42", Map("role" -> List("admin", "dev")), "https://example.com")
    * }}}
    */
  def parse(rawUrl: String, origin: String): Url =
    val qIdx     = rawUrl.indexOf('?')
    val pathname = if qIdx >= 0 then rawUrl.substring(0, qIdx) else rawUrl
    val params   =
      if qIdx < 0 || qIdx + 1 >= rawUrl.length then Map.empty[String, List[String]]
      else
        rawUrl
          .substring(qIdx + 1)
          .split('&')
          .filter(_.nonEmpty)
          .foldLeft(Map.empty[String, List[String]]) { (acc, pair) =>
            val eqIdx  = pair.indexOf('=')
            val (k, v) =
              if eqIdx >= 0 then (decodeComponent(pair.substring(0, eqIdx)), decodeComponent(pair.substring(eqIdx + 1)))
              else (decodeComponent(pair), "")
            acc.updated(k, acc.getOrElse(k, Nil) :+ v)
          }
    Url(pathname, params, origin)

  private def decodeComponent(s: String): String =
    try java.net.URLDecoder.decode(s, "UTF-8")
    catch case _: Exception => s
