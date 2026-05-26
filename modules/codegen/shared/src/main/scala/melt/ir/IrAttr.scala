/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.ir

/** Typed representation of an element attribute after semantic lowering.
  *
  * Unlike [[melt.ast.Attr]] which mirrors the raw syntax, [[IrAttr]] variants
  * correspond to distinct runtime operations. For example, all 20+ `bind:`
  * directives become distinct `IrBind*` cases instead of a single
  * `Directive("bind", name, expr, mods)`.
  */
enum IrAttr:

  // ── Static / dynamic HTML attributes ──────────────────────────────────────

  /** `name="value"` — static string attribute */
  case StaticAttr(name: String, value: String)

  /** `name={expr}` — dynamic attribute bound to a Scala expression */
  case DynamicAttr(name: String, expr: ScalaExpr)

  /** `disabled`, `checked` etc. — boolean attribute (no value) */
  case BooleanAttr(name: String)

  /** `{...counterProps}` — spreads all entries of an HtmlAttrs expression */
  case Spread(expr: ScalaExpr)

  /** `class={dynamicExpr}` — passes the full class string expression to `Bind.cls`.
    * Distinct from [[ClassToggle]] which conditionally adds/removes a *single* class name.
    * SPA: `Bind.cls($elem, $expr)`.
    * SSR: included in `emitScopedClassAttr` as a dynamic segment appended to the class
    * attribute string (same slot as `class:name={expr}` toggles).
    */
  case DynamicClass(expr: ScalaExpr)

  /** `disabled={boolExpr}`, `checked={boolExpr}` etc. — dynamic boolean attribute.
    * Uses `Bind.booleanAttr` (adds/removes the attribute based on a boolean Signal)
    * rather than `Bind.attr` (always writes a string value).
    * Resolved from [[melt.codegen.SpaCodeGen.htmlBooleanAttrs]] at lowering time.
    */
  case DynamicBooleanAttr(name: String, expr: ScalaExpr)

  // ── Event handlers ─────────────────────────────────────────────────────────

  /** `onclick={handler}` */
  case EventHandler(event: String, handler: ScalaExpr)

  /** `on:click|preventDefault={handler}` */
  case EventHandlerWithModifier(event: String, handler: ScalaExpr, modifiers: Set[String])

  // ── bind: directives ───────────────────────────────────────────────────────
  // Each bind target is its own case to avoid stringly-typed dispatch at Emitter level.

  /** `bind:value={state}` on `<input>`.
    * Uses `Bind.inputValue` with cast to `dom.html.Input`.
    * **Not** used for `<textarea>` — see [[BindTextareaValue]].
    */
  case BindInputValue(expr: ScalaExpr)

  /** `bind:value={state}` on `<textarea>`.
    * Uses `Bind.textareaValue` with cast to `dom.html.TextArea`.
    * Distinct from [[BindInputValue]] because the runtime method and DOM cast differ.
    * SSR: the bound expression becomes the element body (not an attribute), matching
    * Svelte 5 semantics — `emitElement` dispatches to `emitTextareaBindValue`.
    */
  case BindTextareaValue(expr: ScalaExpr)

  /** `bind:value={state}` on `<select>` (deferred — emitted after children) */
  case BindSelectValue(expr: ScalaExpr, multiple: Boolean)

  /** `bind:value-int={state}` */
  case BindInputValueInt(expr: ScalaExpr)

  /** `bind:value-double={state}` */
  case BindInputValueDouble(expr: ScalaExpr)

  /** `bind:checked={state}` */
  case BindChecked(expr: ScalaExpr)

  /** `bind:group={state}` on radio / checkbox.
    * `isCheckbox` is pre-resolved at lowering time by inspecting the sibling `type="checkbox"`
    * attribute, so the Emitter does not need to re-scan sibling attrs.
    * `true` → `Bind.checkboxGroup`, `false` → `Bind.radioGroup`.
    */
  case BindGroup(expr: ScalaExpr, isCheckbox: Boolean)

  /** `bind:this={ref}` — stores the DOM element reference */
  case BindThis(expr: ScalaExpr)

  /** `bind:clientWidth={state}` / `clientHeight` / `offsetWidth` / `offsetHeight` */
  case BindDimension(property: String, expr: ScalaExpr)

  /** `bind:innerHTML={trustedHtml}` */
  case BindInnerHtml(expr: ScalaExpr)

  /** `bind:textContent={state}` */
  case BindTextContent(expr: ScalaExpr)

  /** `bind:currentTime`, `bind:paused`, `bind:volume` etc. on media elements */
  case BindMedia(property: String, expr: ScalaExpr)

  // ── class: / style: directives ────────────────────────────────────────────

  /** `class:active={flag}` — conditionally adds/removes a CSS class */
  case ClassToggle(className: String, expr: ScalaExpr)

  /** `style:color={expr}` — reactively sets a CSS property */
  case StyleProp(property: String, expr: ScalaExpr)

  // ── melt:window / melt:document bind: ────────────────────────────────────
  // These targets are NOT regular DOM elements, so their bind: directives are
  // distinct from element-level BindDimension / BindMedia and must be kept
  // separate to avoid routing them to the wrong Emitter method.

  /** `bind:scrollY={state}` / `scrollX` / `innerWidth` / `innerHeight` /
    * `outerWidth` / `outerHeight` / `devicePixelRatio` / `online` on `<melt:window>`.
    * Maps to `Window.bind<Property>($expr)` in [[melt.emit.SpaEmitter]].
    * SSR silently drops this (no meaning in server rendering).
    */
  case BindWindow(property: String, expr: ScalaExpr)

  /** `bind:visibilityState={state}` / `fullscreenElement` / `pointerLockElement` /
    * `activeElement` on `<melt:document>`.
    * Maps to `Document.bind<Property>($expr)` in [[melt.emit.SpaEmitter]].
    * SSR silently drops this.
    */
  case BindDocument(property: String, expr: ScalaExpr)

  // ── use: (actions) ────────────────────────────────────────────────────────

  /** `use:autoFocus` / `use:tooltip={params}` */
  case UseAction(actionName: String, params: Option[ScalaExpr])

  // ── transition: / in: / out: / animate: ───────────────────────────────────

  /** `transition:fade={params}` / `in:fly` / `out:scale` */
  case Transition(direction: TransitionDirection, name: String, params: Option[ScalaExpr], global: Boolean)

  /** `animate:flip={params}` */
  case Animate(name: String, params: Option[ScalaExpr])

enum TransitionDirection:
  case Both, In, Out

/** A `key = expr` argument in a component Props call */
case class IrProp(name: String, value: IrPropValue)

enum IrPropValue:
  case Static(value: String)
  case Dynamic(expr: ScalaExpr)
  case Shorthand(varName: String)
  case BooleanTrue
