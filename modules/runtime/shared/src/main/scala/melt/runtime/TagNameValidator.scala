/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** HTML tag name validation.
  *
  * Mirrors Svelte 5's `REGEX_VALID_TAG_NAME` (WHATWG HTML + Custom Elements
  * naming rules).
  *
  * The rules are:
  *
  *   - The first character must be an ASCII letter (`a-zA-Z`).
  *   - Before any hyphen, subsequent characters must be ASCII alphanumerics
  *     (`a-zA-Z0-9`).
  *   - After a hyphen appears (i.e. inside a custom element name) the
  *     extended character set kicks in: ASCII alphanumerics plus `.`, `-`,
  *     `_`, and the Unicode ranges listed in the Custom Elements spec.
  *   - `:` is never allowed (Svelte parity). `melt:head` / `melt:window` /
  *     `melt:body` are converted to their own AST nodes before this check
  *     is applied, so they never reach `TagNameValidator.isValid`.
  *   - Empty tag names are invalid.
  *
  * Used by the compile-time `TagNameChecker`; SSR / SPA code generation
  * does not re-validate at runtime.
  */
object TagNameValidator:

  def isValid(name: String): Boolean =
    if name.isEmpty then false
    else
      val firstCp = name.codePointAt(0)
      if !isAsciiLetter(firstCp) then false
      else
        var i          = Character.charCount(firstCp)
        var seenHyphen = false
        var valid      = true
        while valid && i < name.length do
          val cp = name.codePointAt(i)
          if cp == '-' then seenHyphen = true
          else if seenHyphen then
            if !isValidExtendedChar(cp) then valid = false
          else if !isAsciiAlphaNum(cp) then valid  = false
          i += Character.charCount(cp)
        valid

  private def isAsciiLetter(cp: Int): Boolean =
    (cp >= 'a' && cp <= 'z') || (cp >= 'A' && cp <= 'Z')

  private def isAsciiAlphaNum(cp: Int): Boolean =
    isAsciiLetter(cp) || (cp >= '0' && cp <= '9')

  private def isValidExtendedChar(cp: Int): Boolean =
    isAsciiAlphaNum(cp) ||
      cp == '.' || cp == '-' || cp == '_' ||
      cp == 0x00b7 ||
      (cp >= 0x00c0 && cp <= 0x00d6) ||
      (cp >= 0x00d8 && cp <= 0x00f6) ||
      (cp >= 0x00f8 && cp <= 0x037d) ||
      (cp >= 0x037f && cp <= 0x1fff) ||
      (cp >= 0x200c && cp <= 0x200d) ||
      (cp >= 0x203f && cp <= 0x2040) ||
      (cp >= 0x2070 && cp <= 0x218f) ||
      (cp >= 0x2c00 && cp <= 0x2fef) ||
      (cp >= 0x3001 && cp <= 0xd7ff) ||
      (cp >= 0xf900 && cp <= 0xfdcf) ||
      (cp >= 0xfdf0 && cp <= 0xfffd) ||
      (cp >= 0x10000 && cp <= 0xeffff)

/** Validator for component (Scala type) names used by `<MyComponent />`.
  *
  * Component names must be valid Scala type identifiers starting with an
  * uppercase letter, so that the generated code can emit
  * `MyComponent.render(...)` without escaping.
  */
object ComponentNameValidator:

  private val pattern = """^[A-Z][A-Za-z0-9_]*$""".r

  def isValid(name: String): Boolean = pattern.matches(name)
