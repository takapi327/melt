/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** Type-safe MathML element name for use with `<melt:element this={tag}>` inside a MathML context.
  *
  * A Scala 3 literal-type union of all standard MathML element names.
  * Elements created with this type use `createElementNS` with the MathML namespace URI,
  * which is required to produce proper MathML DOM nodes.
  *
  * {{{
  * val elem: MathTag = "mfrac"  // OK
  * val elem: MathTag = "hoge"   // compile error: "hoge" is not a MathTag
  * }}}
  *
  * For tag names from external sources use [[MathTag.fromString]] or [[MathTag.trusted]].
  */
type MathTag =
  "annotation" | "annotation-xml" | "math" | "merror" | "mfrac" | "mi" |
    "mn" | "mo" | "mover" | "mpadded" | "mphantom" | "mroot" | "mrow" |
    "ms" | "msqrt" | "mspace" | "mstyle" | "msub" | "msubsup" | "msup" |
    "mtable" | "mtd" | "mtext" | "mtr" | "munder" | "munderover" | "semantics"

object MathTag:

  /** MathML namespace URI used with `createElementNS`. */
  val namespace: String = "http://www.w3.org/1998/Math/MathML"

  /** Runtime set of all known MathML element names.
    * Used by [[fromString]] and by the meltc compiler for string-literal validation.
    */
  val knownTags: Set[String] = Set(
    "annotation",
    "annotation-xml",
    "math",
    "merror",
    "mfrac",
    "mi",
    "mn",
    "mo",
    "mover",
    "mpadded",
    "mphantom",
    "mroot",
    "mrow",
    "ms",
    "msqrt",
    "mspace",
    "mstyle",
    "msub",
    "msubsup",
    "msup",
    "mtable",
    "mtd",
    "mtext",
    "mtr",
    "munder",
    "munderover",
    "semantics"
  )

  /** Validates a runtime string and returns `Some(tag)` if it is a known MathML element
    * name, `None` otherwise.
    */
  def fromString(s: String): Option[MathTag] =
    if knownTags.contains(s) then Some(s.asInstanceOf[MathTag]) else None

  /** Escape hatch: treats any string as a valid [[MathTag]] without compile-time checking. */
  def trusted(s: String): MathTag = s.asInstanceOf[MathTag]
