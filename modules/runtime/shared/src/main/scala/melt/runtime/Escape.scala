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
  *   - `TrustedHtml`  → value extracted and HTML-escaped ([[html]]) or
  *                      attribute-escaped ([[attr]]); to emit raw HTML
  *                      without escaping use `bind:innerHTML={expr}` in
  *                      the template (generates `renderer.push(expr.value)`)
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
  * NullPointerException surface. See `docs/melt-ssr-design.md` §12.3.1.
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

  /** Escapes `value` for use as a CSS property value (e.g. inside
    * `style="prop: VALUE"`).
    *
    * Blocks CSS-specific attack vectors that plain attribute escaping
    * misses:
    *
    *   - `url(javascript:...)` / `url(vbscript:...)` — script protocol
    *   - `url(data:text/...)` / `url(data:image/svg...)` /
    *     `url(data:application/...)` — non-raster-image data URIs that can
    *     carry arbitrary HTML, CSS, or JavaScript
    *   - `url(blob:...)` — attacker-controlled blob content (fonts, SVG, etc.)
    *   - `url(file:...)` — local file access via CSS
    *   - `expression(...)` — IE-era JavaScript expressions
    *   - `@import "..."` — pulls in arbitrary stylesheets
    *
    * Safe raster-image data URIs (`url(data:image/png;base64,...)` etc.)
    * are not blocked.
    *
    * Detection is case-insensitive and tolerates whitespace / control
    * characters inside the dangerous construct (mirroring
    * [[isDangerousUrl]]). Quoted `url()` forms (`url('...')` and
    * `url("...")`) are normalised to the unquoted form before matching,
    * so quoting cannot be used to bypass the checks. When a dangerous
    * pattern is found the whole value is replaced with an empty string
    * and a warning is emitted.
    *
    * '''Known limitation''': CSS Unicode escape sequences
    * (e.g. `\6A` for `j`) are not decoded before matching. A crafted
    * value like `url(\6Aavascript:...)` would bypass string detection.
    * In practice modern browsers do not execute JavaScript from CSS
    * `url()` values, so the practical risk is low. A future version may
    * add a CSS-escape decoder pass.
    *
    * `null` / `None` collapse to empty string as elsewhere in this object.
    */
  def cssValue(value: Any): String =
    normalize(value) match
      case None      => ""
      case Some(str) =>
        if isDangerousCss(str) then
          MeltWarnings.warn(s"Blocked dangerous CSS value: ${ truncate(str, 80) }")
          ""
        else escapeAttrInner(str)

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
      case null           => ""
      case None           => ""
      case Some(inner)    => url(inner) // recurse for nested Option
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
      case sig: Signal[?]  => normalize(sig.value)
      case v: State[?]     => normalize(v.value)
      case other           =>
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
        case '&'  => buf ++= "&amp;"
        case '<'  => buf ++= "&lt;"
        case '>'  => buf ++= "&gt;"
        case '"'  => buf ++= "&quot;"
        case '\'' => buf ++= "&#39;"
        case '\n' => buf ++= "&#10;"
        case '\r' => buf ++= "&#13;"
        case '\t' => buf ++= "&#9;"
        case c    => buf += c
      i += 1
    buf.toString

  /** Detects dangerous patterns inside a CSS property value.
    *
    * Normalises whitespace, tabs, and newlines before matching, which
    * covers most bypass attempts (`java  script:`, `expre\nssion(...)`).
    * Returns `true` if any of the following patterns are found:
    *
    *   - `javascript:` / `vbscript:` — script protocols anywhere in the value
    *   - `url(file:...)` — local file access
    *   - `url(blob:...)` — attacker-controlled blob content
    *   - `url(data:text/...)` — text/html, text/css, text/javascript, etc.
    *   - `url(data:image/svg...)` — SVG can carry inline scripts
    *   - `url(data:application/...)` — application/javascript, etc.
    *   - `expression(` — IE CSS expressions
    *   - `@import` — stylesheet injection
    *
    * Safe raster-image data URIs (`url(data:image/png;base64,...)`) do not
    * match any of the above and are therefore allowed.
    */
  private def isDangerousCss(raw: String): Boolean =
    val normalized = raw.filterNot { c =>
      val code = c.toInt
      (code >= 0x00 && code <= 0x1f) || code == 0x7f ||
      c == '\u0020' || c == '\u0009' ||
      c == '\u000A' || c == '\u000D'
    }.toLowerCase
    // Normalise quoted url() forms: url('...') and url("...") → url(...)
    // so that url('blob:...') is treated identically to url(blob:...).
    val deQuoted = normalized.replace("url('", "url(").replace("url(\"", "url(")

    deQuoted.contains("javascript:") ||
    deQuoted.contains("vbscript:") ||
    deQuoted.contains("url(file:") ||
    deQuoted.contains("url(blob:") ||
    deQuoted.contains("url(data:text/") ||
    deQuoted.contains("url(data:image/svg") ||
    deQuoted.contains("url(data:application/") ||
    deQuoted.contains("expression(") ||
    deQuoted.contains("@import")

  /** Detects dangerous URL protocols after normalising whitespace and
    * control characters (browsers do the same before parsing the scheme).
    *
    * Blocked schemes: `javascript:`, `vbscript:`, `file:`, `blob:`,
    * and `data:` except raster image subtypes (`data:image/png` etc.).
    */
  private def isDangerousUrl(raw: String): Boolean =
    val normalized = raw.filterNot { c =>
      val code = c.toInt
      (code >= 0x00 && code <= 0x1f) || code == 0x7f ||
      c == '\u0020' || c == '\u0009' ||
      c == '\u000A' || c == '\u000D'
    }.toLowerCase

    if normalized.startsWith("javascript:") then true
    else if normalized.startsWith("vbscript:") then true
    else if normalized.startsWith("file:") then true
    else if normalized.startsWith("blob:") then true
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
