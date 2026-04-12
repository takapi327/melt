/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** A URL that the developer has explicitly opted in to as trustworthy.
  *
  * `Escape.url()` normally blocks dangerous protocols such as
  * `javascript:`, `vbscript:`, `data:text/html`, etc. Wrapping a string in
  * `TrustedUrl.unsafe(...)` bypasses this check. The method name
  * deliberately contains `unsafe` — the caller is responsible for
  * guaranteeing that the URL is safe.
  *
  * == Implementation note: why `AnyVal` instead of `opaque type` ==
  *
  * An earlier design used `opaque type TrustedUrl = String`, which is only
  * distinguished at compile time and is fully erased to `String` at runtime.
  * That erasure breaks `Escape.url`'s pattern match:
  *
  * {{{
  *   value match
  *     case tu: TrustedUrl => tu.value         // bypass validation
  *     case s:  String     => validateUrl(s)   // block dangerous URLs
  * }}}
  *
  * With an opaque type, `case tu: TrustedUrl` is compiled as
  * `case _: String` and matches '''every''' `String` — silently letting
  * unsanitised user input bypass URL validation. A critical vulnerability.
  *
  * `final class TrustedUrl(val value: String) extends AnyVal` gives us:
  *
  *   1. A runtime-distinguishable type, so `isInstanceOf[TrustedUrl]` and
  *      the pattern match above behave as intended.
  *   2. The same zero-overhead ergonomics as an opaque type in most call
  *      sites (the JVM unboxes `AnyVal` wherever possible).
  *   3. An API surface identical to the opaque-type version —
  *      `TrustedUrl.unsafe(str)` wraps and `tu.value` unwraps.
  */
final class TrustedUrl(val value: String) extends AnyVal

object TrustedUrl:

  /** Marks a string as trusted. Only pass content that you have already
    * validated against protocol / injection attacks.
    */
  def unsafe(url: String): TrustedUrl = new TrustedUrl(url)
