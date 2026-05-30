/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** A string that has been explicitly marked as safe to insert as raw HTML.
  *
  * `Bind.html` requires `TrustedHtml` instead of a plain `String` so that
  * XSS-prone calls become visible at the call site. Use [[TrustedHtml.unsafe]]
  * for static, developer-controlled markup, or [[TrustedHtml.sanitize]] to
  * produce a `TrustedHtml` value from user input via an explicit sanitizer
  * function.
  *
  * {{{
  * // OK — static, developer-controlled markup
  * Bind.html(el, TrustedHtml.unsafe("<strong>Hello</strong>"))
  *
  * // OK — sanitised user input (JVM: jsoup, JS: DOMPurify, etc.)
  * Bind.html(el, State(TrustedHtml.sanitize(userInput, mySanitizer)))
  *
  * // Compile error — plain State[String] no longer accepted
  * Bind.html(el, State(userInput))
  * }}}
  *
  * == Implementation note: why `AnyVal` instead of `opaque type` ==
  *
  * An earlier design used `opaque type TrustedHtml = String`, which is only
  * distinguished at compile time and is fully erased to its underlying type
  * (`String`) at runtime. That erasure breaks any code that needs to tell
  * trusted from untrusted values at runtime — notably the SSR `Escape`
  * helpers, which do:
  *
  * {{{
  *   value match
  *     case th: TrustedHtml => th.value          // bypass escaping
  *     case s:  String      => escapeHtml(s)     // escape
  * }}}
  *
  * With an opaque type, `case th: TrustedHtml` is compiled as
  * `case _: String` and therefore matches **every** `String`, silently
  * letting unsanitised user input bypass HTML escaping — a critical XSS
  * vulnerability.
  *
  * `final class TrustedHtml private (val value: String) extends AnyVal` gives us:
  *
  *   1. A runtime-distinguishable type, so `isInstanceOf[TrustedHtml]`
  *      and the pattern match above behave as intended.
  *   2. The same zero-overhead ergonomics as an opaque type in most call
  *      sites (the JVM unboxes `AnyVal` wherever possible).
  *   3. An API surface identical to the opaque-type version —
  *      `TrustedHtml.unsafe(str)` wraps and `th.value` unwraps.
  */
final class TrustedHtml private (val value: String) extends AnyVal

object TrustedHtml:

  /** Marks a string as trusted HTML.
    *
    * '''Warning:''' Only pass content that is either developer-controlled
    * (static markup) or has been sanitised against XSS. Never pass raw user
    * input. For user-supplied content, prefer [[sanitize]].
    */
  def unsafe(html: String): TrustedHtml = new TrustedHtml(html)

  /** Sanitizes a string using the provided sanitizer function and wraps the
    * result as trusted HTML.
    *
    * The sanitizer is responsible for removing or escaping unsafe content
    * (script tags, event handlers, dangerous URLs, etc.). If the sanitizer
    * throws, the exception propagates to the caller — there is no silent
    * failure.
    *
    * {{{
    * // JVM: jsoup ベース
    * TrustedHtml.sanitize(userInput, Jsoup.clean(_, Safelist.basic()))
    *
    * // JS: DOMPurify ベース
    * TrustedHtml.sanitize(userInput, DOMPurify.sanitize(_))
    * }}}
    *
    * @param html      sanitize 対象の HTML 文字列
    * @param sanitizer String => String のサニタイザ関数。例外を投げてよい
    */
  def sanitize(html: String, sanitizer: String => String): TrustedHtml =
    new TrustedHtml(sanitizer(html))
