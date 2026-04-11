/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** HTML void elements — tags that never have a closing tag in the HTML
  * serialisation.
  *
  * Matches Svelte 5's `VOID_ELEMENT_NAMES` set exactly: the 14 void elements
  * defined by HTML5, plus the deprecated `command`, `keygen`, and `param`
  * that Svelte still includes for legacy compatibility.
  *
  * Used by both `SpaCodeGen` and `SsrCodeGen` to decide whether to emit
  * `</tag>`. `!doctype` is also treated as void (Svelte 5 parity).
  */
object VoidElements:

  val set: Set[String] = Set(
    "area", "base", "br", "col", "command", "embed", "hr", "img", "input",
    "keygen", "link", "meta", "param", "source", "track", "wbr"
  )

  /** Returns `true` iff `tag` is a void element (case-insensitive). */
  def isVoid(tag: String): Boolean =
    val lower = tag.toLowerCase
    set.contains(lower) || lower == "!doctype"
