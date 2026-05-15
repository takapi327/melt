/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.collection.mutable

/** Mutable cookie manager for the current request scope.
  *
  * `set` / `delete` are synchronous side effects (same pattern as [[Locals]]).
  * Each request gets its own [[CookieJar]] instance, so there are no
  * concurrency issues.
  *
  * Used by [[RequestEvent]] in [[ServerHook]] handlers:
  *
  * {{{
  * new ServerHook[F]:
  *   def handle(event: RequestEvent[F], resolve: Resolve[F]): F[Response] =
  *     event.cookies.set("session", token)
  *     resolve()
  * }}}
  */
trait CookieJar:

  /** Returns the value of the named cookie from the request, if present. */
  def get(name: String): Option[String]

  /** Returns all request cookies as a `name -> value` map. */
  def getAll: Map[String, String]

  /** Sets a cookie to be sent with the response. */
  def set(name: String, value: String, options: CookieOptions = CookieOptions()): Unit

  /** Deletes a cookie by setting `Max-Age=0`. */
  def delete(name: String, options: CookieOptions = CookieOptions()): Unit

  /** Serializes all pending `Set-Cookie` header values. */
  def serialize: List[String]

/** Default implementation backed by mutable maps. */
private[meltkit] final class CookieJarImpl(requestCookies: Map[String, String]) extends CookieJar:

  private val pending = mutable.ListBuffer.empty[ResponseCookie]

  def get(name: String): Option[String] = requestCookies.get(name)

  def getAll: Map[String, String] = requestCookies

  def set(name: String, value: String, options: CookieOptions): Unit =
    pending += ResponseCookie(name, value, options)

  def delete(name: String, options: CookieOptions): Unit =
    pending += ResponseCookie.deleted(name, options.path)

  def serialize: List[String] = pending.toList.map(serializeCookie)

  private def serializeCookie(c: ResponseCookie): String =
    val sb = new StringBuilder
    sb.append(s"${ c.name }=${ c.value }")
    sb.append(s"; Path=${ c.options.path }")
    c.options.maxAge.foreach(ma => sb.append(s"; Max-Age=$ma"))
    c.options.domain.foreach(d => sb.append(s"; Domain=$d"))
    if c.options.httpOnly then sb.append("; HttpOnly")
    if c.options.secure || c.options.sameSite == "None" then sb.append("; Secure")
    sb.append(s"; SameSite=${ c.options.sameSite }")
    sb.result()

object CookieJar:

  /** Creates a [[CookieJar]] from parsed request cookies. */
  def apply(requestCookies: Map[String, String]): CookieJar =
    CookieJarImpl(requestCookies)

  /** Parses a raw `Cookie` header value into a `name -> value` map.
    *
    * {{{
    * CookieJar.parseCookieHeader("session=abc; theme=dark")
    * // Map("session" -> "abc", "theme" -> "dark")
    * }}}
    */
  def parseCookieHeader(header: String): Map[String, String] =
    if header.isEmpty then Map.empty
    else
      header
        .split(';')
        .iterator
        .map(_.trim)
        .filter(_.nonEmpty)
        .flatMap { pair =>
          val eqIdx = pair.indexOf('=')
          if eqIdx > 0 then Some(pair.substring(0, eqIdx).trim -> pair.substring(eqIdx + 1).trim)
          else None
        }
        .toMap
