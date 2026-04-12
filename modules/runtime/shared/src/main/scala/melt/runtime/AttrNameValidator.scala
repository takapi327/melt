/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** HTML attribute name validation.
  *
  * Mirrors Svelte 5's `INVALID_ATTR_NAME_CHAR_REGEX` character class. An
  * attribute name is considered invalid if it contains any of the
  * following code points:
  *
  *   - Whitespace (any codepoint for which `Character.isWhitespace` returns true)
  *   - `'`, `"`, `>`, `/`, `=`
  *   - Unicode non-characters in the BMP: `U+FDD0`–`U+FDEF`
  *   - Plane-terminator non-characters: `U+FFFE`, `U+FFFF`, `U+1FFFE`, …,
  *     `U+10FFFE`, `U+10FFFF`
  *
  * Everything else — ASCII alphanumerics, `-`, `_`, `:`, `.`, `@`, and any
  * multi-byte Unicode letter — is allowed. Empty names are invalid.
  *
  * Used both at compile time (to reject static invalid names in `AttrNameChecker`)
  * and at runtime (to drop invalid keys from spread attributes).
  */
object AttrNameValidator:

  /** Returns `true` iff `name` is a syntactically valid HTML attribute name. */
  def isValid(name: String): Boolean =
    if name.isEmpty then false
    else
      var i     = 0
      var valid = true
      while valid && i < name.length do
        val cp = name.codePointAt(i)
        if isInvalidCodePoint(cp) then valid = false
        i += Character.charCount(cp)
      valid

  private def isInvalidCodePoint(cp: Int): Boolean =
    // Whitespace (via Character.isWhitespace, which handles BMP correctly)
    Character.isWhitespace(cp) ||
      // Literal characters forbidden in attribute names
      cp == '\'' || cp == '"' || cp == '>' || cp == '/' || cp == '=' ||
      // Unicode non-characters in the BMP: U+FDD0..U+FDEF
      (cp >= 0xfdd0 && cp <= 0xfdef) ||
      // Plane-terminator non-characters U+xxFFFE / U+xxFFFF for x = 0..10
      ((cp & 0xfffe) == 0xfffe && cp <= 0x10ffff)
