/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** Type-safe SVG element name for use with `<melt:element this={tag}>` inside an SVG context.
  *
  * A Scala 3 literal-type union of all standard SVG element names.
  * Elements created with this type use `createElementNS` with the SVG namespace URI,
  * which is required to produce proper SVG DOM nodes.
  *
  * {{{
  * val shape: SvgTag = "path"   // OK
  * val shape: SvgTag = "hoge"   // compile error: "hoge" is not an SvgTag
  * }}}
  *
  * For tag names from external sources use [[SvgTag.fromString]] or [[SvgTag.trusted]].
  */
type SvgTag =
  "animate" | "animateMotion" | "animateTransform" | "circle" | "clipPath" |
    "defs" | "desc" | "ellipse" | "feBlend" | "feColorMatrix" | "feComponentTransfer" |
    "feComposite" | "feConvolveMatrix" | "feDiffuseLighting" | "feDisplacementMap" |
    "feFlood" | "feGaussianBlur" | "feImage" | "feMerge" | "feMorphology" |
    "feOffset" | "feSpecularLighting" | "feTile" | "feTurbulence" | "filter" |
    "foreignObject" | "g" | "image" | "line" | "linearGradient" | "marker" |
    "mask" | "metadata" | "mpath" | "path" | "pattern" | "polygon" | "polyline" |
    "radialGradient" | "rect" | "set" | "stop" | "svg" | "switch" | "symbol" |
    "text" | "textPath" | "title" | "tspan" | "use" | "view"

object SvgTag:

  /** SVG namespace URI used with `createElementNS`. */
  val namespace: String = "http://www.w3.org/2000/svg"

  /** Runtime set of all known SVG element names.
    * Used by [[fromString]] and by the meltc compiler for string-literal validation.
    */
  val knownTags: Set[String] = Set(
    "animate",
    "animateMotion",
    "animateTransform",
    "circle",
    "clipPath",
    "defs",
    "desc",
    "ellipse",
    "feBlend",
    "feColorMatrix",
    "feComponentTransfer",
    "feComposite",
    "feConvolveMatrix",
    "feDiffuseLighting",
    "feDisplacementMap",
    "feFlood",
    "feGaussianBlur",
    "feImage",
    "feMerge",
    "feMorphology",
    "feOffset",
    "feSpecularLighting",
    "feTile",
    "feTurbulence",
    "filter",
    "foreignObject",
    "g",
    "image",
    "line",
    "linearGradient",
    "marker",
    "mask",
    "metadata",
    "mpath",
    "path",
    "pattern",
    "polygon",
    "polyline",
    "radialGradient",
    "rect",
    "set",
    "stop",
    "svg",
    "switch",
    "symbol",
    "text",
    "textPath",
    "title",
    "tspan",
    "use",
    "view"
  )

  /** Validates a runtime string and returns `Some(tag)` if it is a known SVG element
    * name, `None` otherwise.
    */
  def fromString(s: String): Option[SvgTag] =
    if knownTags.contains(s) then Some(s.asInstanceOf[SvgTag]) else None

  /** Escape hatch: treats any string as a valid [[SvgTag]] without compile-time checking. */
  def trusted(s: String): SvgTag = s.asInstanceOf[SvgTag]
