/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** HTML escaping utilities used by both SPA and SSR code generation.
  *
  * All entry points accept `Any` and handle:
  *
  *   - `null`, `None` → empty string
  *   - `Some(x)`      → recursively processes `x`
  *   - `TrustedHtml`  → value extracted and returned as-is (html) or
  *                      attribute-escaped ([[attr]])
  *   - `TrustedUrl`   → value extracted and attribute-escaped
  *                      ([[url]] only — bypasses protocol checks)
  *   - everything else → `toString` then escape
  *
  * If `toString` itself returns `null`, the result is the empty string.
  *
  * == Null safety contract ==
  *
  * Following Svelte 5's `escape_html`, all escape helpers coerce absent
  * values to the empty string rather than raising. This matches typical
  * Scala idioms (`Option[T]` for optional props) and eliminates
  * NullPointerException surface. See `docs/meltc-ssr-design.md` §12.3.1.
  */
object Escape:

  /** Escapes `value` for HTML text content. */
  def html(value: Any): String =
    normalize(value) match
      case None      => ""
      case Some(str) => escapeHtmlInner(str)

  /** Escapes `value` for HTML attribute content (includes `"` escaping). */
  def attr(value: Any): String =
    normalize(value) match
      case None      => ""
      case Some(str) => escapeAttrInner(str)

  /** Escapes `value` for use in a URL attribute (e.g. `href`, `src`).
    *
    * Dangerous protocols (`javascript:`, `vbscript:`, `file:`,
    * `data:text/html`, `data:image/svg+xml`, …) are blocked: the value is
    * replaced with an empty string and a warning is emitted through
    * [[MeltWarnings]]. `TrustedUrl` values bypass this check (the developer
    * has taken responsibility for validating the URL).
    */
  def url(value: Any): String =
    value match
      case null          => ""
      case None          => ""
      case Some(inner)   => url(inner) // recurse for nested Option
      case tu: TrustedUrl =>
        // AnyVal value class — the runtime type check is preserved despite
        // AnyVal's unboxing optimisation (unlike opaque types).
        escapeAttrInner(tu.value)
      case other =>
        val s = other.toString
        if s == null then ""
        else if isDangerousUrl(s) then
          MeltWarnings.warn(s"Blocked dangerous URL: ${ truncate(s, 80) }")
          ""
        else escapeAttrInner(s)

  // ── Internal helpers ───────────────────────────────────────────────────

  /** Normalises `value` to `Option[String]` while honouring the `Trusted*`
    * value classes.
    *
    * Note that `TrustedUrl` is handled only by [[url]] (which needs the
    * validation-bypass semantics); here we simply unwrap it so that the
    * wrapped string is escaped as any other attribute value.
    */
  private def normalize(value: Any): Option[String] =
    value match
      case null            => None
      case None            => None
      case Some(inner)     => normalize(inner)
      case th: TrustedHtml => Some(th.value)
      case tu: TrustedUrl  => Some(tu.value)
      case other =>
        val s = other.toString
        if s == null then None else Some(s)

  private def escapeHtmlInner(s: String): String =
    val buf = new StringBuilder(s.length)
    var i   = 0
    while i < s.length do
      s.charAt(i) match
        case '&' => buf ++= "&amp;"
        case '<' => buf ++= "&lt;"
        case '>' => buf ++= "&gt;"
        case c   => buf += c
      i += 1
    buf.toString

  private def escapeAttrInner(s: String): String =
    val buf = new StringBuilder(s.length)
    var i   = 0
    while i < s.length do
      s.charAt(i) match
        case '&' => buf ++= "&amp;"
        case '<' => buf ++= "&lt;"
        case '>' => buf ++= "&gt;"
        case '"' => buf ++= "&quot;"
        case c   => buf += c
      i += 1
    buf.toString

  /** Detects dangerous URL protocols after normalising whitespace and
    * control characters (browsers do the same before parsing the scheme).
    */
  private def isDangerousUrl(raw: String): Boolean =
    val normalized = raw
      .filterNot { c =>
        val code = c.toInt
        (code >= 0x00 && code <= 0x1F) || code == 0x7F ||
        c == '\u0020' || c == '\u0009' ||
        c == '\u000A' || c == '\u000D'
      }
      .toLowerCase

    if normalized.startsWith("javascript:") then true
    else if normalized.startsWith("vbscript:") then true
    else if normalized.startsWith("file:") then true
    else if normalized.startsWith("data:") then
      // data: URLs are OK for raster images only. SVG can carry <script>
      // and HTML data: URLs render arbitrary markup, so both are blocked.
      if normalized.startsWith("data:image/svg+xml") then true
      else if normalized.startsWith("data:image/") then false
      else true
    else false

  private def truncate(s: String, max: Int): String =
    if s.length <= max then s
    else s.substring(0, max) + "..."
