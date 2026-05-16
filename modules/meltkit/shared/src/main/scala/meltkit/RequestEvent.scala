/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** The request context passed to [[ServerHook]] handlers, inspired by
  * SvelteKit's `RequestEvent`.
  *
  * Provides read-only access to headers, query params, and cookies,
  * plus mutable cookie access via [[cookieJar]] and the request-scoped
  * [[locals]] store.
  *
  * This replaces the former `RequestInfo` and `Middleware` types —
  * all request interception is done via [[ServerHook]] / `app.use()`.
  */
trait RequestEvent[F[_]]:

  /** The HTTP method name in uppercase (e.g. `"GET"`, `"POST"`). */
  def method: String

  /** The path component of the request URL (e.g. `"/users/42"`). */
  def requestPath: String

  /** Returns the first value of the named query parameter, if present. */
  def query(name: String): Option[String]

  /** Returns all values of the named query parameter. */
  def queryAll(name: String): List[String]

  /** Returns all query parameters as a multi-valued map. */
  def queryParams: Map[String, List[String]]

  /** Returns the value of the named request header (case-insensitive). */
  def header(name: String): Option[String]

  /** Returns all request headers as a lowercase-keyed name→value map. */
  def headers: Map[String, String]

  /** Returns the value of the named cookie from the `Cookie` header, if present. */
  def cookie(name: String): Option[String]

  /** Returns all cookies from the `Cookie` header as a name→value map. */
  def cookies: Map[String, String]

  /** The request-scoped local store. */
  def locals: Locals

  /** Mutable cookie manager for setting/deleting response cookies. */
  def cookieJar: CookieJar

  /** The full parsed URL. */
  def url: Url

  /** The route identifier, if matched. */
  def routeId: Option[String]

  /** Whether this is a data-only request (Phase 2). */
  def isDataRequest: Boolean
