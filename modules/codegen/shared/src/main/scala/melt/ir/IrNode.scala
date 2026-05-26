/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.ir

/** Typed intermediate representation of a template node.
  *
  * The key differences from [[melt.ast.TemplateNode]]:
  *
  *   1. `TemplateNode.Expression` is split into [[IrDynamicText]], [[IrList]],
  *      [[IrKeyedList]], [[IrConditional]], [[IrRawHtml]], [[IrDomResult]] and
  *      [[IrStaticText]] based on the semantic classification previously done
  *      by `SpaCodeGen.classifyExpr` at string-emission time.
  *
  *   2. `TemplateNode.InlineTemplate` (mixed Scala + HTML parts) is kept as
  *      an opaque [[IrInlineTemplate]] bridge node during Phases 1–3; full
  *      expansion into [[IrList]] / [[IrConditional]] is deferred to Phase 4.
  *
  *   3. Whether a node is "static" (no reactive children) is tracked via
  *      [[IrStaticElement]], enabling hoisting optimizations.
  */
enum IrNode:

  // ── Static nodes (no reactive bindings, no dynamic children) ─────────────

  /** A plain text node with no interpolation: `Hello World` */
  case IrStaticText(content: String)

  /** An element with only static attributes and static children.
    * May be hoisted out of reactive scopes by [[melt.ir.opt.StaticHoistPass]].
    */
  case IrStaticElement(
    tag:      String,
    ns:       Option[String],         // "svg" | "math" | None
    attrs:    List[IrAttr],
    children: List[IrNode],
    scopeId:  String
  )

  // ── Dynamic text ──────────────────────────────────────────────────────────

  /** `{count}`, `{name.value}`, `{a + b}` — plain reactive text */
  case IrDynamicText(expr: ScalaExpr)

  // ── Dynamic elements ──────────────────────────────────────────────────────

  /** An element with at least one dynamic attribute or reactive child. */
  case IrElement(
    tag:      String,
    ns:       Option[String],
    attrs:    List[IrAttr],
    children: List[IrNode],
    scopeId:  String
  )

  /** `<melt:element this={tagExpr}>` — dynamic tag name */
  case IrDynamicElement(
    tagExpr:  ScalaExpr,
    attrs:    List[IrAttr],
    children: List[IrNode],
    scopeId:  String
  )

  // ── Components ────────────────────────────────────────────────────────────

  /** `<Counter count={count} />` — component invocation.
    *
    * When `spreadExpr` is `Some(e)`, the component is called as `$Name($e)` directly,
    * bypassing the named-Props constructor.
    * At most one of `props` (non-empty) and `spreadExpr` (Some) should hold at once.
    * SSR: when `spreadExpr` is present the component is called as `$Name($e)`;
    * children are still supported alongside it.
    */
  case IrComponent(
    name:         String,
    props:        List[IrProp],
    children:     Option[IrChildrenSlot],
    spreadExpr:   Option[ScalaExpr] = None,
    hasStyled:    Boolean           = false,  // `styled` BooleanAttr — add _scopeId class to root
    bindThisExpr: Option[ScalaExpr] = None    // `bind:this={ref}` on a component
  )

  // ── List rendering ────────────────────────────────────────────────────────

  /** `{items.value.map(item => <li>...</li>)}` — unkeyed list rendering (Bind.list) */
  case IrList(
    source:   ScalaExpr,   // e.g. "todos"  (stripped of .value / .now())
    renderFn: ScalaExpr    // e.g. "todo => { ... }" (the map lambda body)
  )

  /** `{items.keyed(_.id).map(item => <li>...</li>)}` — keyed list rendering (Bind.each) */
  case IrKeyedList(
    source:   ScalaExpr,   // e.g. "todos"
    keyFn:    ScalaExpr,   // e.g. "_.id"
    renderFn: ScalaExpr    // e.g. "todo => { ... }"
  )

  // ── Conditional rendering ─────────────────────────────────────────────────

  /** `{if cond then <A /> else <B />}` — conditional DOM rendering (Bind.show) */
  case IrConditional(
    source:           Option[ScalaExpr],  // reactive source for Bind.show(v, render, anchor) overload; None = () overload
    conditionAndBody: ScalaExpr           // the full `if/match` expression returning dom.Node
    // ⚠ In Emitters, pattern variables are named (sourceOpt, condAndBody) to reflect that
    //   source is Option — do NOT call .code directly on source; always match on Some/None first.
  )

  // ── Raw HTML ──────────────────────────────────────────────────────────────

  /** `{TrustedHtml("...")}` — raw HTML insertion (Bind.htmlAnchor) */
  case IrRawHtml(
    source: Option[ScalaExpr], // reactive source if expr is signal-based
    expr:   ScalaExpr
  )

  // ── DOM-returning expression ──────────────────────────────────────────────

  /** An expression that directly returns a `dom.Element` (e.g. `Await(f) { ... }`). */
  case IrDomResult(expr: ScalaExpr)

  /** An expression that returns a `dom.DocumentFragment` (multi-child InlineTemplate). */
  case IrFragmentResult(expr: ScalaExpr)

  // ── Special elements ──────────────────────────────────────────────────────

  /** `<melt:head>` — inserts children into document.head */
  case IrHead(children: List[IrNode])

  /** `<melt:window bind:scrollY={y} onresize={...}>` */
  case IrWindow(attrs: List[IrAttr])

  /** `<melt:body onclick={...}>` */
  case IrBody(attrs: List[IrAttr])

  /** `<melt:document bind:visibilityState={v}>` */
  case IrDocument(attrs: List[IrAttr])

  // ── Structural ────────────────────────────────────────────────────────────

  /** `<melt:boundary onerror={h}> ... <melt:failed> ... </melt:boundary>` */
  case IrBoundary(
    children:  List[IrNode],
    pending:   Option[List[IrNode]],
    failed:    Option[IrFailedBlock],
    onError:   Option[ScalaExpr]
  )

  /** `<melt:key this={keyExpr}>` — destroys and re-mounts on key change */
  case IrKeyBlock(
    keyExpr:  ScalaExpr,
    children: List[IrNode]
  )

  /** `{#snippet render(item: Todo)} ... {/snippet}` */
  case IrSnippetDef(
    name:     String,
    params:   List[IrSnippetParam],
    children: List[IrNode]
  )

  /** `{@render snippetExpr}` */
  case IrRenderCall(expr: ScalaExpr)

  /** `{children}` — slot for parent-supplied children */
  case IrChildren

  // ── Static hoisting ───────────────────────────────────────────────────────

  /** Produced by [[melt.ir.opt.StaticHoistPass]] to replace an [[IrStaticElement]] in-place.
    * The [[melt.emit.SpaEmitter]] emits `_hoist_N.cloneNode(true)` at this position and
    * a corresponding `private val _hoist_N = ...` at object level.
    * [[melt.emit.SsrEmitter]] re-emits the hoisted element inline (cloning has no benefit
    * in SSR since each render is independent).
    */
  case IrHoistRef(id: String)

  // ── InlineTemplate (bridge) ───────────────────────────────────────────────

  /** Mixed Scala + HTML expression, e.g. `{items.map(item => <li>{item}</li>)}`.
    *
    * The parser produces [[melt.ast.TemplateNode.InlineTemplate]] when an
    * expression block contains inline HTML fragments.  Full IR normalisation
    * (expanding to [[IrList]] / [[IrConditional]]) requires recursive code
    * generation inside the lowering phase, which is deferred to Phase 4.
    *
    * During Phases 1–3 this node is kept opaque and the Emitters call the
    * existing `SpaCodeGen` / `SsrCodeGen` InlineTemplate logic as a bridge.
    *
    * **Important**: `parts` must carry *lowered* [[IrNode]] children, not the
    * original [[melt.ast.TemplateNode]] nodes, so that Emitters can recurse
    * into HTML fragments using their normal `emitNode` path.
    */
  case IrInlineTemplate(parts: List[IrInlineTemplatePart])

/** A part of an [[IrNode.IrInlineTemplate]] with HTML children already lowered
  * to [[IrNode]].  Mirrors [[melt.ast.InlineTemplatePart]] but uses `IrNode`
  * instead of `TemplateNode` so that Emitters can recurse into HTML fragments.
  */
enum IrInlineTemplatePart:
  /** A raw Scala code fragment (e.g. `items.map(item =>`) */
  case Code(code: String)
  /** An HTML fragment with children already lowered to [[IrNode]]. */
  case Html(nodes: List[IrNode])

/** Recursively transforms child nodes of this node.
  * Leaf nodes (no children) return `this` unchanged.
  * Used by [[melt.ir.opt.StaticHoistPass]] to walk the tree.
  */
extension (node: IrNode)
  def mapChildren(f: IrNode => IrNode): IrNode = node match
    case e: IrNode.IrElement        => e.copy(children = e.children.map(f))
    case e: IrNode.IrStaticElement  => e.copy(children = e.children.map(f))
    case e: IrNode.IrDynamicElement => e.copy(children = e.children.map(f))
    case e: IrNode.IrComponent      => e.copy(children = e.children.map(slot => slot.copy(nodes = slot.nodes.map(f))))
    case e: IrNode.IrHead           => e.copy(children = e.children.map(f))
    case e: IrNode.IrBoundary       =>
      e.copy(
        children = e.children.map(f),
        pending  = e.pending.map(_.map(f)),
        failed   = e.failed.map(b => b.copy(children = b.children.map(f)))
      )
    case e: IrNode.IrKeyBlock       => e.copy(children = e.children.map(f))
    case e: IrNode.IrSnippetDef     => e.copy(children = e.children.map(f))
    case leaf                       => leaf  // IrStaticText, IrDynamicText, IrHoistRef, etc.

/** Children slot passed to a component invocation.
  * Renamed from `IrChildren` to avoid ambiguity with the [[IrNode.IrChildren]]
  * singleton case (which represents the `{children}` expression inside a template).
  */
case class IrChildrenSlot(nodes: List[IrNode])

case class IrFailedBlock(errorVar: String, resetVar: String, children: List[IrNode])
case class IrSnippetParam(name: String, typeAnnotation: Option[String])
