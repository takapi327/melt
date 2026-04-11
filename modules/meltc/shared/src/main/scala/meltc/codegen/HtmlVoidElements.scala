/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.codegen

/** HTML void element set used by `SsrCodeGen` to suppress `</tag>` emission.
  *
  * Deliberately duplicated from `melt.runtime.VoidElements` in the runtime
  * module: `meltc` is a compiler and must not depend on the runtime
  * library. Both lists must stay in sync with Svelte 5's
  * `VOID_ELEMENT_NAMES` (14 HTML5 elements + `command`, `keygen`, `param`
  * for legacy parity).
  */
object HtmlVoidElements:

  val set: Set[String] = Set(
    "area", "base", "br", "col", "command", "embed", "hr", "img", "input",
    "keygen", "link", "meta", "param", "source", "track", "wbr"
  )

  def isVoid(tag: String): Boolean =
    val lower = tag.toLowerCase
    set.contains(lower) || lower == "!doctype"
