/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** Configuration for CSP nonce injection in [[meltkit.adapter.http4s.Http4sAdapter]].
  *
  * Pass to `Http4sAdapter.apply` to enable per-request nonce generation and
  * automatic `Content-Security-Policy` header attachment.
  *
  * Nonce is automatically appended to `script-src` and `style-src` directives.
  * If only `default-src` is set without an explicit `script-src`, the nonce
  * will not be appended — add `script-src` explicitly to enable nonce injection.
  *
  * {{{
  * Http4sAdapter(
  *   app, clientDistDir, manifest,
  *   cspConfig = Some(CspConfig(
  *     directives = Map(
  *       "script-src" -> List("'self'"),
  *       "style-src"  -> List("'self'")
  *     )
  *   ))
  * )
  * }}}
  *
  * @param directives CSP directives; nonce is automatically appended to `script-src` and `style-src`
  * @param reportOnly if true, sends `Content-Security-Policy-Report-Only` instead of enforcing
  */
case class CspConfig(
  directives: Map[String, List[String]] = Map.empty,
  reportOnly: Boolean                   = false
)

object CspConfig:
  val default: CspConfig = CspConfig()
