/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.css

/** Top-level CSS node. A stylesheet is represented as `List[CssNode]`. */
sealed trait CssNode

object CssNode:

  /** Style rule: `selector { ... }`
    *
    * `body` may contain a mix of nested rules and declarations ([[RawText]]) (CSS Nesting).
    */
  case class StyleRule(
    selector: String,
    body:     List[CssNode]
  ) extends CssNode

  /** At-rule: `@name prelude { ... }` or `@name prelude;`
    *
    * @param name    the at-rule name without `@`, e.g. `"media"`, `"layer"`, `"keyframes"`
    * @param prelude the text before `{` or `;` (condition, identifier, etc.)
    * @param body    `Some(list of inner nodes)` for block at-rules,
    *                `None` for bodyless at-rules (`@charset`, `@import`, `@layer base;`)
    */
  case class AtRule(
    name:    String,
    prelude: String,
    body:    Option[List[CssNode]]
  ) extends CssNode

  /** Raw text inside a declaration block (property declarations, whitespace, etc.).
    *
    * Declarations such as `color: red;` are kept as-is without further parsing.
    * This representation is sufficient because CSS scoping never needs to transform declarations.
    */
  case class RawText(text: String) extends CssNode

  /** A `/* ... */` comment. Preserved as-is in the output. */
  case class Comment(text: String) extends CssNode

  /** The set of at-rule names whose blocks contain only descriptors, not CSS rules.
    *
    * Defined here in `CssNode` as the single source of truth shared by both
    * `CssParser` and `CssScoper`.
    * `CssParser` stores these blocks as `RawText`;
    * `CssScoper` passes them through as `other` without calling `scopeNodes`.
    *
    * **Both classes must always reference this set** to stay in sync.
    */
  val PassthroughAtRules: Set[String] = Set(
    "keyframes",
    "-webkit-keyframes",
    "font-face",
    "page",
    "counter-style",
    "font-palette-values",
    "property",
    "color-profile",
    "viewport"
  )
