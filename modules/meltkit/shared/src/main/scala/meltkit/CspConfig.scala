/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** Configuration for CSP nonce injection in Melt server adapters.
  *
  * Pass as `cspConfig` (or via `app.csp(...)`) to enable per-request nonce generation
  * and automatic `Content-Security-Policy` header attachment.
  *
  * Nonce is automatically appended to `script-src` and `style-src` directives.
  * If only `default-src` is set without an explicit `script-src` or `style-src`,
  * those directives are derived from `default-src` so the nonce is always injected
  * into the directives that govern inline content.
  *
  * {{{
  * // Http4sAdapter
  * Http4sAdapter(app, clientDistDir, manifest, cspConfig = Some(CspConfig.recommended))
  *
  * // UndertowServer / NodeServer
  * app.csp(CspConfig.recommended)
  * UndertowServer.builder(app).start()
  * }}}
  *
  * @param directives CSP directives; nonce is automatically appended to `script-src` and
  *                   `style-src` (derived from `default-src` when absent)
  * @param reportOnly if true, sends `Content-Security-Policy-Report-Only` instead of enforcing
  */
case class CspConfig(
  directives: Map[String, List[String]] = Map.empty,
  reportOnly: Boolean                   = false
):

  /** Returns the HTTP header name: `"Content-Security-Policy"` or
    * `"Content-Security-Policy-Report-Only"` when [[reportOnly]] is `true`.
    */
  def headerName: String =
    if reportOnly then "Content-Security-Policy-Report-Only"
    else "Content-Security-Policy"

  /** Builds the CSP header value, appending `'nonce-{nonce}'` to `script-src` and
    * `style-src`. When either directive is absent but `default-src` is present, it
    * is derived from `default-src` so the nonce always reaches the directives that
    * govern inline content.
    */
  def buildHeaderValue(nonce: String): String =
    val nonceToken   = s"'nonce-$nonce'"
    val nonceTargets = Set("script-src", "style-src")
    // Derive script-src / style-src from default-src when absent so that the nonce
    // is injected even if the caller only configured default-src.
    val withScriptSrc =
      if directives.contains("script-src") then directives
      else directives.get("default-src").fold(directives)(ds => directives + ("script-src" -> ds))
    val effective =
      if withScriptSrc.contains("style-src") then withScriptSrc
      else withScriptSrc.get("default-src").fold(withScriptSrc)(ds => withScriptSrc + ("style-src" -> ds))
    effective
      .map {
        case (d, vs) =>
          val fv = if nonceTargets.contains(d) then vs :+ nonceToken else vs
          s"$d ${ fv.mkString(" ") }"
      }
      .mkString("; ")

object CspConfig:
  val default: CspConfig = CspConfig()

  /** Recommended CSP configuration for Melt SSR applications.
    *
    * Uses nonce-based script and style protection with strict source defaults.
    * Nonce values are automatically injected by all Melt server adapters.
    *
    * {{{
    * Http4sAdapter(app, clientDistDir, manifest, cspConfig = Some(CspConfig.recommended))
    * // or
    * app.csp(CspConfig.recommended)
    * }}}
    */
  val recommended: CspConfig = CspConfig(
    directives = Map(
      "default-src" -> List("'self'"),
      "script-src"  -> List("'self'"),
      "style-src"   -> List("'self'"),
      "img-src"     -> List("'self'", "data:"),
      "font-src"    -> List("'self'"),
      "connect-src" -> List("'self'"),
      "frame-src"   -> List("'none'"),
      "object-src"  -> List("'none'"),
      "base-uri"    -> List("'self'"),
      "form-action" -> List("'self'")
    )
  )
