/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.ast

/** A fully parsed `.melt` file. */
case class MeltFile(
  script:   Option[ScriptSection],
  template: List[TemplateNode],
  style:    Option[StyleSection]
)

/** The `<script lang="scala">` section of a `.melt` file.
  *
  * @param code      the raw Scala source inside the script tags
  * @param propsType the value of the `props="..."` attribute, if present
  */
case class ScriptSection(
  code:      String,
  propsType: Option[String]
)

/** The `<style>` section of a `.melt` file.
  *
  * @param css raw CSS text (scope ID injection is done in Phase 3)
  */
case class StyleSection(css: String)

/** A node in the HTML template of a `.melt` file. */
enum TemplateNode:
  /** An HTML element such as `<div class="foo">...</div>`. */
  case Element(tag: String, attrs: List[Attr], children: List[TemplateNode])

  /** A plain text node. */
  case Text(content: String)

  /** A Scala expression enclosed in braces: `{count}`. */
  case Expression(code: String)

  /** A component reference — tag name starts with an uppercase letter: `<Counter />`. */
  case Component(name: String, attrs: List[Attr], children: List[TemplateNode])

  /** A Scala expression containing inline HTML template fragments.
    * The `parts` interleave Scala code with parsed HTML trees.
    * Example: `items.map(item =>` + `<li>...</li>` + `)`
    */
  case InlineTemplate(parts: List[InlineTemplatePart])

/** A part of an [[TemplateNode.InlineTemplate]] expression. */
enum InlineTemplatePart:
  /** A raw Scala code fragment. */
  case Code(code: String)

  /** A parsed HTML template fragment. */
  case Html(nodes: List[TemplateNode])

/** An attribute on an HTML element or component. */
enum Attr:
  /** A static string attribute: `class="foo"`. */
  case Static(name: String, value: String)

  /** A dynamic expression attribute: `class={cls}`. */
  case Dynamic(name: String, expr: String)

  /** A directive attribute: `bind:value={v}`, `class:active={flag}`.
    *
    * @param kind      the directive kind (`bind`, `class`, `style`, `use`, ...)
    * @param name      the directive target name (without modifiers)
    * @param expr      the optional expression value
    * @param modifiers pipe-separated modifiers e.g. `|global`, `|local`
    */
  case Directive(kind: String, name: String, expr: Option[String], modifiers: Set[String] = Set.empty)

  /** An event handler attribute: `onclick={handler}` → EventHandler("click", "handler"). */
  case EventHandler(event: String, expr: String)

  /** A boolean attribute present without a value: `disabled`, `checked`. */
  case BooleanAttr(name: String)

  /** A spread attribute that forwards all key-value pairs of an expression:
    * `{...counterProps}` → `Spread("counterProps")`.
    * Used in §10.2 (Component System) to pass props objects wholesale.
    */
  case Spread(expr: String)

  /** A shorthand attribute where the variable name is also the attribute name:
    * `{label}` → `Shorthand("label")`, equivalent to `label={label}`.
    * Used in §10.2 (Component System).
    */
  case Shorthand(varName: String)
