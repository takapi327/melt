/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** HTML5 element/attribute pairs whose values are URLs.
  *
  * Used by `SsrCodeGen` / `SpaCodeGen` to decide whether a dynamic
  * attribute value must go through [[Escape.url]] (for protocol validation)
  * rather than the regular [[Escape.attr]] path.
  *
  * Static attribute values are trusted (they are written verbatim in the
  * `.melt` source by the developer, who takes responsibility for their
  * content). Only dynamic and spread attributes receive URL validation.
  */
object UrlAttributes:

  /** (tag, attribute) pairs specific to certain elements. */
  private val specific: Set[(String, String)] = Set(
    "a"          -> "href",
    "area"       -> "href",
    "base"       -> "href",
    "link"       -> "href",
    "img"        -> "src",
    "img"        -> "srcset",
    "source"     -> "src",
    "source"     -> "srcset",
    "track"      -> "src",
    "iframe"     -> "src",
    "script"     -> "src",
    "embed"      -> "src",
    "audio"      -> "src",
    "video"      -> "src",
    "video"      -> "poster",
    "input"      -> "src",
    "input"      -> "formaction",
    "button"     -> "formaction",
    "form"       -> "action",
    "object"     -> "data",
    "blockquote" -> "cite",
    "q"          -> "cite",
    "del"        -> "cite",
    "ins"        -> "cite"
  )

  /** Tag-independent URL attributes (e.g. XLink). */
  private val global: Set[String] = Set("xlink:href")

  /** Returns `true` iff the given `(tag, attrName)` pair is a URL attribute. */
  def isUrlAttribute(tag: String, attrName: String): Boolean =
    val t = tag.toLowerCase
    val a = attrName.toLowerCase
    global.contains(a) || specific.contains((t, a))
