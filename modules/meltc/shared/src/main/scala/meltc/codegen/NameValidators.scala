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
    (cp >= 0xFDD0 && cp <= 0xFDEF) ||
    ((cp & 0xFFFE) == 0xFFFE && cp <= 0x10FFFF)

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
          else if !isAsciiAlphaNum(cp) then valid = false
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
    cp == 0x00B7 ||
    (cp >= 0x00C0 && cp <= 0x00D6) ||
    (cp >= 0x00D8 && cp <= 0x00F6) ||
    (cp >= 0x00F8 && cp <= 0x037D) ||
    (cp >= 0x037F && cp <= 0x1FFF) ||
    (cp >= 0x200C && cp <= 0x200D) ||
    (cp >= 0x203F && cp <= 0x2040) ||
    (cp >= 0x2070 && cp <= 0x218F) ||
    (cp >= 0x2C00 && cp <= 0x2FEF) ||
    (cp >= 0x3001 && cp <= 0xD7FF) ||
    (cp >= 0xF900 && cp <= 0xFDCF) ||
    (cp >= 0xFDF0 && cp <= 0xFFFD) ||
    (cp >= 0x10000 && cp <= 0xEFFFF)
