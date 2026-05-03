/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** Configuration for [[Middleware.csrf]].
  *
  * @param enabled            Whether to enable CSRF checking (default: true; set to false to disable in tests).
  * @param trustedOrigins     Additional origins to allow beyond the server's own origin (full URL, e.g. `"https://app.example.com"`).
  * @param exemptPaths        Paths to skip CSRF validation (prefix-matched with path-separator awareness).
  * @param trustForwardedHost Whether to trust `X-Forwarded-Host` / `X-Forwarded-Proto` headers from a reverse proxy.
  */
case class CsrfConfig(
  enabled:            Boolean      = true,
  trustedOrigins:     Set[String]  = Set.empty,
  exemptPaths:        List[String] = Nil,
  trustForwardedHost: Boolean      = false
)

object CsrfConfig:
  /** Default configuration: CSRF protection enabled with no trusted origins or exempt paths. */
  val default: CsrfConfig = CsrfConfig()

  /** Disabled configuration for use in test environments. */
  val disabled: CsrfConfig = CsrfConfig(enabled = false)
