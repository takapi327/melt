/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** A string that has been explicitly marked as safe to insert as raw HTML.
  *
  * `Bind.html` requires `TrustedHtml` instead of a plain `String` so that
  * XSS-prone calls become visible at the call site. Callers must opt in by
  * wrapping their string with [[TrustedHtml.unsafe]], which signals that the
  * content is either static or has already been sanitised.
  *
  * {{{
  * // OK — static, developer-controlled markup
  * Bind.html(el, TrustedHtml.unsafe("<strong>Hello</strong>"))
  *
  * // OK — sanitised user input
  * val sanitised = sanitise(userInput)
  * Bind.html(el, Var(TrustedHtml.unsafe(sanitised)))
  *
  * // Compile error — plain Var[String] no longer accepted
  * Bind.html(el, Var(userInput))
  * }}}
  */
opaque type TrustedHtml = String

object TrustedHtml:

  /** Marks a string as trusted HTML.
    *
    * **Warning:** Only pass content that is either developer-controlled (static
    * markup) or has been sanitised against XSS. Never pass raw user input.
    */
  def unsafe(html: String): TrustedHtml = html

  extension (th: TrustedHtml)
    /** Returns the underlying string value. */
    def value: String = th
