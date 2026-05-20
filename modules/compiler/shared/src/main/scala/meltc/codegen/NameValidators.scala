/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.codegen

/** Compile-time copies of the HTML attribute / tag / component name
  * validators. Kept in sync with `melt.runtime.AttrNameValidator`,
  * `melt.runtime.TagNameValidator`, and `melt.runtime.ComponentNameValidator`
  * in the runtime module.
  *
  * Duplication rationale: meltc must not depend on the runtime library, so
  * the validators that run at compile time are local to the codegen
  * package. Both copies follow Svelte 5's rules exactly (see
  * `docs/meltc-ssr-design.md` §12.1.2 / §12.1.3).
  */
object NameValidators:

  /** Validates an HTML attribute name. Mirrors `AttrNameValidator.isValid`. */
  def isValidAttrName(name: String): Boolean =
    if name.isEmpty then false
    else
      var i     = 0
      var valid = true
      while valid && i < name.length do
        val cp = name.codePointAt(i)
        if isInvalidAttrCodePoint(cp) then valid = false
        i += Character.charCount(cp)
      valid

  private def isInvalidAttrCodePoint(cp: Int): Boolean =
    Character.isWhitespace(cp) ||
      cp == '\'' || cp == '"' || cp == '>' || cp == '/' || cp == '=' ||
      (cp >= 0xfdd0 && cp <= 0xfdef) ||
      ((cp & 0xfffe) == 0xfffe && cp <= 0x10ffff)

  /** Validates an HTML tag name. Mirrors `TagNameValidator.isValid`. */
  def isValidTagName(name: String): Boolean =
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
            if !isValidExtendedTagChar(cp) then valid = false
          else if !isAsciiAlphaNum(cp) then valid     = false
          i += Character.charCount(cp)
        valid

  private val componentNamePattern = """^[A-Z][A-Za-z0-9_]*$""".r

  /** Validates a component (Scala type) name. Mirrors
    * `ComponentNameValidator.isValid`.
    */
  def isValidComponentName(name: String): Boolean =
    componentNamePattern.matches(name)

  private def isAsciiLetter(cp: Int): Boolean =
    (cp >= 'a' && cp <= 'z') || (cp >= 'A' && cp <= 'Z')

  private def isAsciiAlphaNum(cp: Int): Boolean =
    isAsciiLetter(cp) || (cp >= '0' && cp <= '9')

  private def isValidExtendedTagChar(cp: Int): Boolean =
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
