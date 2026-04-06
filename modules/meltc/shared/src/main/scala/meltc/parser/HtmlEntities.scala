/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.parser

/** Decodes HTML character references (named, decimal, and hexadecimal) in text.
  *
  * {{{
  * HtmlEntities.decode("&amp; &lt;3")   // → "& <3"
  * HtmlEntities.decode("&#123;")        // → "{"
  * HtmlEntities.decode("&#x7B;")        // → "{"
  * }}}
  */
private[parser] object HtmlEntities:

  private val NamedEntities: Map[String, String] = Map(
    "amp"    -> "&",
    "lt"     -> "<",
    "gt"     -> ">",
    "quot"   -> "\"",
    "apos"   -> "'",
    "nbsp"   -> "\u00a0",
    "copy"   -> "\u00a9",
    "reg"    -> "\u00ae",
    "trade"  -> "\u2122",
    "hellip" -> "\u2026",
    "mdash"  -> "\u2014",
    "ndash"  -> "\u2013",
    "laquo"  -> "\u00ab",
    "raquo"  -> "\u00bb",
    "lsquo"  -> "\u2018",
    "rsquo"  -> "\u2019",
    "ldquo"  -> "\u201c",
    "rdquo"  -> "\u201d",
    "bull"   -> "\u2022",
    "middot" -> "\u00b7",
    "times"  -> "\u00d7",
    "divide" -> "\u00f7",
    "lbrace" -> "{",
    "rbrace" -> "}"
  )

  /** Decodes all HTML character references in `text`. Unrecognised references are left as-is. */
  def decode(text: String): String =
    if !text.contains('&') then return text

    val buf = new StringBuilder
    var i   = 0
    while i < text.length do
      if text(i) == '&' then
        val semi = text.indexOf(';', i + 1)
        if semi < 0 || semi - i > 10 then
          buf += '&'
          i += 1
        else
          val ref = text.substring(i + 1, semi)
          decodeRef(ref) match
            case Some(decoded) =>
              buf ++= decoded
              i = semi + 1
            case None =>
              buf += '&'
              i += 1
      else
        buf += text(i)
        i += 1
    buf.toString

  private def decodeRef(ref: String): Option[String] =
    if ref.startsWith("#x") || ref.startsWith("#X") then
      try Some(Integer.parseInt(ref.substring(2), 16).toChar.toString)
      catch case _: NumberFormatException => None
    else if ref.startsWith("#") then
      try Some(Integer.parseInt(ref.substring(1)).toChar.toString)
      catch case _: NumberFormatException => None
    else NamedEntities.get(ref)
