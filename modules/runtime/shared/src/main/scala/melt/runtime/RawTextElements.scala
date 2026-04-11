/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** HTML raw-text and escapable-raw-text elements.
  *
  * Matches Svelte 5's `RAW_TEXT_ELEMENTS` exactly: `script`, `style`,
  * `textarea`, `title`. Inside these tags the HTML parser does not
  * recognise child tags or entity references (or only a restricted set),
  * which means `{expr}` interpolation is unsafe: escaping would not apply.
  *
  * `SsrCodeGen` rejects `{expr}` inside any of these elements at compile
  * time, with the sole exception of `<title>` when it appears directly
  * inside `<melt:head>` (handled by a dedicated `SsrRenderer.head.title`
  * helper that escapes the content correctly).
  *
  * '''Not raw-text''': `noscript`, `iframe`. Svelte 5 treats them as normal
  * elements and so do we.
  */
object RawTextElements:

  val set: Set[String] = Set(
    "script",   // raw text
    "style",    // raw text
    "textarea", // escapable raw text
    "title"     // escapable raw text
  )

  /** Returns `true` iff `tag` is a raw-text element (case-insensitive). */
  def isRawText(tag: String): Boolean = set.contains(tag.toLowerCase)
