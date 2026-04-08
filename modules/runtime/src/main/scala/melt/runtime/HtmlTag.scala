/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** Type-safe HTML tag name for use with `<melt:element this={tag}>`.
  *
  * A Scala 3 literal-type union of all valid HTML5 element names.
  * Assigning an unknown name produces a compile-time type error:
  *
  * {{{
  * val tag: HtmlTag = "div"   // OK
  * val tag: HtmlTag = "hoge"  // compile error: "hoge" is not a HtmlTag
  * }}}
  *
  * For tag names that come from external sources (e.g. a CMS), use
  * [[HtmlTag.fromString]] (returns `Option[HtmlTag]`) or
  * [[HtmlTag.trusted]] (explicit escape hatch).
  */
type HtmlTag =
  "a" | "abbr" | "address" | "area" | "article" | "aside" | "audio" |
    "b" | "base" | "bdi" | "bdo" | "blockquote" | "br" | "button" |
    "canvas" | "caption" | "cite" | "code" | "col" | "colgroup" |
    "data" | "datalist" | "dd" | "del" | "details" | "dfn" | "dialog" |
    "div" | "dl" | "dt" |
    "em" | "embed" |
    "fieldset" | "figcaption" | "figure" | "footer" | "form" |
    "h1" | "h2" | "h3" | "h4" | "h5" | "h6" | "header" | "hgroup" | "hr" |
    "i" | "iframe" | "img" | "input" | "ins" |
    "kbd" |
    "label" | "legend" | "li" | "link" |
    "main" | "map" | "mark" | "menu" | "meta" | "meter" |
    "nav" | "noscript" |
    "object" | "ol" | "optgroup" | "option" | "output" |
    "p" | "picture" | "pre" | "progress" |
    "q" |
    "rp" | "rt" | "ruby" |
    "s" | "samp" | "script" | "search" | "section" | "select" | "slot" |
    "small" | "source" | "span" | "strong" | "style" | "sub" | "summary" | "sup" |
    "table" | "tbody" | "td" | "template" | "textarea" | "tfoot" | "th" | "thead" |
    "time" | "tr" | "track" |
    "u" | "ul" |
    "var" | "video" |
    "wbr"

object HtmlTag:

  /** Runtime set of all known HTML5 tag names.
    * Used by [[fromString]] and by the meltc compiler for string-literal validation.
    */
  val knownTags: Set[String] = Set(
    "a", "abbr", "address", "area", "article", "aside", "audio",
    "b", "base", "bdi", "bdo", "blockquote", "br", "button",
    "canvas", "caption", "cite", "code", "col", "colgroup",
    "data", "datalist", "dd", "del", "details", "dfn", "dialog",
    "div", "dl", "dt",
    "em", "embed",
    "fieldset", "figcaption", "figure", "footer", "form",
    "h1", "h2", "h3", "h4", "h5", "h6", "header", "hgroup", "hr",
    "i", "iframe", "img", "input", "ins",
    "kbd",
    "label", "legend", "li", "link",
    "main", "map", "mark", "menu", "meta", "meter",
    "nav", "noscript",
    "object", "ol", "optgroup", "option", "output",
    "p", "picture", "pre", "progress",
    "q",
    "rp", "rt", "ruby",
    "s", "samp", "script", "search", "section", "select", "slot",
    "small", "source", "span", "strong", "style", "sub", "summary", "sup",
    "table", "tbody", "td", "template", "textarea", "tfoot", "th", "thead",
    "time", "tr", "track",
    "u", "ul",
    "var", "video",
    "wbr"
  )

  /** Validates a runtime string and returns `Some(tag)` if it is a known HTML5 element
    * name, `None` otherwise.
    *
    * Use this when the tag name comes from an external source (CMS, API, etc.) and
    * you want safe conversion to `Option[HtmlTag]` for use with `<melt:element>`.
    */
  def fromString(s: String): Option[HtmlTag] =
    if knownTags.contains(s) then Some(s.asInstanceOf[HtmlTag]) else None

  /** Escape hatch: treats any string as a valid [[HtmlTag]] without compile-time checking.
    *
    * Use only when you are certain the value is a valid tag name and type-system
    * enforcement is not practical (e.g. hyphenated custom elements, or tag names
    * built dynamically from a guaranteed-valid set).
    */
  def trusted(s: String): HtmlTag = s.asInstanceOf[HtmlTag]
