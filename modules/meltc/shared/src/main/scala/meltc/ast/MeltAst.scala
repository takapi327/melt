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

  /** A `<melt:head>` block — children are inserted into `document.head` for the lifetime of the component. */
  case Head(children: List[TemplateNode])

  /** A `<melt:window>` element — attaches event listeners and reactive bindings to `window`. */
  case Window(attrs: List[Attr])

  /** A `<melt:body>` element — attaches event listeners and actions to `document.body`. */
  case Body(attrs: List[Attr])

  /** A `<melt:document>` element — attaches event listeners and reactive bindings to `document`. */
  case Document(attrs: List[Attr])

  /** A `<melt:element this={tagExpr}>` — an HTML element whose tag name is determined at runtime.
    *
    * @param tagExpr the raw Scala expression for the tag name (e.g. `"headingTag"`, `"\"div\""`)
    * @param attrs   attributes, event handlers, and directives (same as [[Element]])
    * @param children child nodes (same as [[Element]])
    */
  case DynamicElement(tagExpr: String, attrs: List[Attr], children: List[TemplateNode])

  /** A `<melt:boundary>` block — catches synchronous rendering errors and manages async pending state.
    *
    * @param attrs    element attributes, typically `onerror={handler}`
    * @param children the main content nodes rendered inside the boundary
    * @param pending  optional [[PendingBlock]] shown while `Await` futures are unresolved
    * @param failed   optional [[FailedBlock]] shown when a synchronous rendering error is caught
    */
  case Boundary(
    attrs:    List[Attr],
    children: List[TemplateNode],
    pending:  Option[PendingBlock],
    failed:   Option[FailedBlock]
  )

  /** A `<melt:key this={keyExpr}>` block — destroys and re-creates content
    * whenever the key expression changes.
    *
    * @param keyExpr  the raw Scala expression (a [[Var]] or [[Signal]]) whose changes trigger re-mounting
    * @param children the content nodes to be destroyed and re-created on each key change
    */
  case KeyBlock(keyExpr: String, children: List[TemplateNode])

/** The `<melt:pending>` block inside a `<melt:boundary>` — shown while `Await` futures are pending. */
case class PendingBlock(children: List[TemplateNode])

/** The `<melt:failed (error, reset)>` block inside a `<melt:boundary>` — shown on synchronous error.
  *
  * @param errorVar the Scala identifier bound to the caught [[Throwable]] (default: `"error"`)
  * @param resetVar the Scala identifier bound to the reset callback (default: `"reset"`)
  * @param children the fallback UI nodes
  */
case class FailedBlock(errorVar: String, resetVar: String, children: List[TemplateNode])

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
