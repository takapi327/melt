/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** Hydration-marker constants shared between the SSR and SPA runtimes.
  *
  * During server-side rendering the component tree is wrapped in
  * HTML-comment markers like:
  *
  * {{{
  *   <!--[melt:counter-->
  *   <div class="melt-…">…</div>
  *   <!--]melt:counter-->
  * }}}
  *
  * Client-side hydration walks the DOM looking for these markers to
  * locate the boundaries of each SSR-rendered component so that it can
  * attach reactive state. Svelte 5 uses the same technique with
  * `<!--[-->` / `<!--]-->`; we prefix ours with `melt:` and include the
  * component `moduleID` so that a page-level hydrator can dispatch to
  * the correct component by name.
  *
  * == Security (§12.1.7) ==
  *
  * The marker payload (module ID) is attacker-derivable only if a
  * component name contains unusual characters. To keep the SSR output
  * impossible to prematurely end the containing `<!-- ... -->` comment,
  * [[escapeForComment]] replaces any `<` / `>` with their Unicode
  * escape sequences (`\u003c` / `\u003e`). This matches Svelte 5's
  * `#serialize_failed_boundary` strategy.
  *
  * Both JS and JVM runtimes must agree on these constants, so they live
  * in the shared source tree.
  */
object HydrationMarkers:

  /** Prefix emitted before every opening marker. Generated form:
    * `<!--[melt:NAME-->`.
    */
  val OpenPrefix: String = "<!--[melt:"

  /** Prefix emitted before every closing marker. Generated form:
    * `<!--]melt:NAME-->`.
    */
  val ClosePrefix: String = "<!--]melt:"

  /** Common suffix closing an HTML comment. */
  val Suffix: String = "-->"

  /** Builds an opening marker comment for the given component `moduleID`. */
  def open(moduleId: String): String =
    s"$OpenPrefix${ escapeForComment(moduleId) }$Suffix"

  /** Builds a closing marker comment. */
  def close(moduleId: String): String =
    s"$ClosePrefix${ escapeForComment(moduleId) }$Suffix"

  /** Escapes any character that could prematurely terminate the
    * containing `<!-- ... -->` comment, plus a handful of whitespace
    * control codes that would confuse client-side parsers.
    *
    * The replacement uses the HTML entity `&#xNN;` for visible `<` / `>`
    * and removes NUL / control characters outright.
    */
  def escapeForComment(raw: String): String =
    if raw == null then ""
    else
      val buf = new StringBuilder(raw.length)
      var i   = 0
      while i < raw.length do
        val c = raw.charAt(i)
        c match
          case '<'                                                   => buf ++= "&#x3c;"
          case '>'                                                   => buf ++= "&#x3e;"
          case '-' if i + 1 < raw.length && raw.charAt(i + 1) == '-' =>
            // `--` inside an HTML comment is illegal per HTML5; break it
            // with a zero-width joiner so parsers don't get confused.
            buf ++= "-&#x2d;"
          case c if c.toInt < 0x20 || c.toInt == 0x7f => () // drop control chars
          case c                                      => buf += c
        i += 1
      buf.toString
