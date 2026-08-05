/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** Minimal, platform-neutral read-only view of a request for CORS decisions.
  *
  * Each server adapter (http4s, Node, Undertow) adapts its native request to this
  * so the CORS logic in [[Cors]] stays pure and shared. Header lookup must be
  * case-insensitive.
  */
trait CorsRequestView:
  /** The HTTP method, upper-case (e.g. `"OPTIONS"`). */
  def method: String

  /** The first value of the named request header (case-insensitive), if present. */
  def header(name: String): Option[String]

/** Pure CORS decision logic, shared by all server adapters.
  *
  * The adapter calls [[isPreflight]] first: if true, it responds `204` with
  * [[preflightHeaders]] and does not route; otherwise it routes normally and adds
  * [[actualHeaders]] to the response.
  */
object Cors:

  /** A CORS preflight is an `OPTIONS` carrying `Access-Control-Request-Method`.
    * (Method is matched by string because `OPTIONS` is not a routable [[HttpMethod]].) */
  def isPreflight(req: CorsRequestView): Boolean =
    req.method.equalsIgnoreCase("OPTIONS") && req.header("Access-Control-Request-Method").isDefined

  /** The `Access-Control-Allow-Origin` value for `origin`, or `None` when it is not
    * allowed. `Any` yields `"*"`; an allowlist reflects a matching origin verbatim. */
  def allowOrigin(cfg: CorsConfig, origin: String): Option[String] =
    cfg.allowedOrigins match
      case CorsOrigins.Any            => Some("*")
      case CorsOrigins.Allowlist(set) => if set.contains(origin) then Some(origin) else None

  /** Headers to add to a normal (non-preflight) response. Empty when the request
    * carries no `Origin` (not a CORS request) or the origin is not allowed. */
  def actualHeaders(cfg: CorsConfig, req: CorsRequestView): Map[String, String] =
    withAllowedOrigin(cfg, req) { allow =>
      var hs = Map("Access-Control-Allow-Origin" -> allow)
      if cfg.allowCredentials then hs += "Access-Control-Allow-Credentials" -> "true"
      if cfg.exposedHeaders.nonEmpty then hs += "Access-Control-Expose-Headers" -> cfg.exposedHeaders.mkString(", ")
      if allow != "*" then hs += "Vary" -> "Origin"
      hs
    }

  /** Headers for the `204` preflight response. Empty when the origin is not allowed
    * (the adapter still returns `204`, but without CORS headers, so the browser blocks). */
  def preflightHeaders(cfg: CorsConfig, req: CorsRequestView): Map[String, String] =
    withAllowedOrigin(cfg, req) { allow =>
      var hs = Map("Access-Control-Allow-Origin" -> allow)
      hs += "Access-Control-Allow-Methods" -> (cfg.allowedMethods.toList.sorted :+ "OPTIONS").distinct.mkString(", ")
      allowHeadersValue(cfg, req).foreach(v => hs += "Access-Control-Allow-Headers" -> v)
      if cfg.allowCredentials then hs += "Access-Control-Allow-Credentials" -> "true"
      cfg.maxAge.foreach(d => hs += "Access-Control-Max-Age" -> d.toSeconds.toString)
      if allow != "*" then
        hs += "Vary" -> "Origin, Access-Control-Request-Method, Access-Control-Request-Headers"
      hs
    }

  /** Merges `value` into an existing (possibly absent) `Vary` header value, avoiding
    * duplicate tokens. Used by adapters that already emit `Vary` (e.g. SSR). */
  def mergeVary(existing: Option[String], value: String): String =
    val have = existing.toList.flatMap(_.split(",")).map(_.trim).filter(_.nonEmpty)
    val add  = value.split(",").map(_.trim).filter(_.nonEmpty)
    (have ++ add).distinct.mkString(", ")

  private def withAllowedOrigin(cfg: CorsConfig, req: CorsRequestView)(
    f: String => Map[String, String]
  ): Map[String, String] =
    req.header("Origin") match
      case None         => Map.empty
      case Some(origin) => allowOrigin(cfg, origin).fold(Map.empty[String, String])(f)

  private def allowHeadersValue(cfg: CorsConfig, req: CorsRequestView): Option[String] =
    cfg.allowedHeaders match
      case CorsHeaders.Reflect         => req.header("Access-Control-Request-Headers")
      case CorsHeaders.Explicit(names) => if names.nonEmpty then Some(names.mkString(", ")) else None
