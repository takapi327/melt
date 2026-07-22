/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.ir.opt

import melt.ir.{ mapChildren, IrAttr, IrComponent, IrNode, ScalaExpr }

/** Auto-binds plain `name` inputs under a `<form use:form={f}>` to the fields of
  * `f`, so a `.melt` author can write `<input name="email">` instead of
  * `<input {...form.field("email")}>` and still get compile-time name checking and
  * SSR value prefill.
  *
  * For each structural `<input>` in a `use:form` scope with a static `name`, the
  * pass appends a '''state-only''' spread — `f.fieldValue("email")` (value),
  * `f.checkedState("remember")` (checkbox) or `f.radioState("role", "admin")`
  * (radio) — which the SSR renderer and SPA hydration expand exactly like a
  * hand-written `{...form.text(_.email)}`. The `use:form` directive itself is
  * stripped (it is only a marker; the runtime `Form` is not a client action).
  *
  * == Why this runs before StaticHoistPass ==
  * A plain `<input name="email" type="text">` is fully static and would be hoisted
  * to an object-level `val` by [[StaticHoistPass]]. Injecting a spread makes it
  * dynamic, so this pass runs '''first''' and promotes any injected
  * [[IrNode.IrStaticElement]] to an [[IrNode.IrElement]] — after which the hoist
  * pass correctly leaves it alone.
  *
  * == Scope (Phase 2) ==
  * Only structural `<input>` elements are handled. Reactive-region inputs
  * (`{items.map(i => <input …>)}`) live inside a `ScalaExpr` string, not the IR
  * tree, and are never reached — those use the hand-written `{...form.field(...)}`
  * form. `<select>` / `<option>` / `<textarea>` need structural handling and are
  * deferred to a follow-up.
  */
object FormBindingPass extends IrPass:
  val name = "FormBindingPass"

  /** `<input>` types that carry no value to prefill (submit/button/reset/image) or
    * cannot be seeded (file). See design C3.
    */
  private val nonBindableInputTypes = Set("submit", "button", "reset", "image", "file")

  def run(ir: IrComponent): IrComponent =
    ir.copy(template = ir.template.map(process(_, None)))

  /** @param scope the `use:form={f}` form expression the node currently sits inside. */
  private def process(node: IrNode, scope: Option[ScalaExpr]): IrNode = node match
    case e: IrNode.IrElement =>
      val (declared, stripped) = extractUseForm(e.attrs)
      val childScope           = declared.orElse(scope) // a nested use:form re-scopes its subtree
      val children             = e.children.map(process(_, childScope))
      val attrs                = inject(e.tag, stripped, scope).getOrElse(stripped)
      e.copy(attrs = attrs, children = children)

    case e: IrNode.IrStaticElement =>
      // A static element carries no directives, so it never declares a scope, but it
      // may be a plain `<input name="…">` to bind, or an ancestor (e.g. a wrapping
      // `<label>`) of one. Injecting a spread makes a node dynamic; a static element
      // must then be promoted to IrElement — both when injected on directly AND when
      // any descendant was promoted — otherwise StaticHoistPass would hoist the whole
      // static subtree and the binding would be lost (silently, on the SPA side).
      val children = e.children.map(process(_, scope))
      inject(e.tag, e.attrs, scope) match
        case Some(attrs)                        => IrNode.IrElement(e.tag, e.ns, attrs, children, e.scopeId)
        case None if children.exists(isDynamic) =>
          IrNode.IrElement(e.tag, e.ns, e.attrs, children, e.scopeId)
        case None => e.copy(children = children)

    case other => other.mapChildren(process(_, scope))

  /** A node StaticHoistPass would treat as non-static (so a static ancestor holding
    * it must be promoted). Only [[IrNode.IrStaticText]] / [[IrNode.IrStaticElement]] /
    * [[IrNode.IrHoistRef]] children keep an element hoistable. */
  private def isDynamic(node: IrNode): Boolean = node match
    case _: IrNode.IrStaticText | _: IrNode.IrStaticElement | _: IrNode.IrHoistRef => false
    case _                                                                         => true

  /** Splits `use:form={f}` out of `attrs`, returning the form expression and the
    * remaining attrs. The directive is dropped so no `Bind.action` is emitted for it
    * (a `Form` is not a client action).
    */
  private def extractUseForm(attrs: List[IrAttr]): (Option[ScalaExpr], List[IrAttr]) =
    attrs.collectFirst { case IrAttr.UseAction("form", Some(f)) => f } match
      case Some(f) => (Some(f), attrs.filterNot { case IrAttr.UseAction("form", _) => true; case _ => false })
      case None    => (None, attrs)

  /** Appends the state-only spread for an injectable input under `scope`. */
  private def inject(tag: String, attrs: List[IrAttr], scope: Option[ScalaExpr]): Option[List[IrAttr]] =
    scope.flatMap(form => spreadFor(tag, attrs, form).map(attrs :+ _))

  private def spreadFor(tag: String, attrs: List[IrAttr], form: ScalaExpr): Option[IrAttr] =
    if tag != "input" then None        // select/option/textarea: deferred
    else if hasIgnore(attrs) then None // data-form-ignore escape hatch
    else
      staticAttr(attrs, "name") match
        case None     => None // dynamic or absent name: not statically checkable, skip
        case Some(nm) =>
          staticAttr(attrs, "type").map(_.toLowerCase) match
            case Some(t) if nonBindableInputTypes.contains(t) => None // C3
            case Some("checkbox")                             =>
              if hasChecked(attrs) then None // C4: user set state explicitly
              else Some(spread(s"""${ form.code }.checkedState("$nm")"""))
            case Some("radio") =>
              if hasChecked(attrs) then None // C4
              else
                staticAttr(attrs, "value") match
                  case Some(v) => Some(spread(s"""${ form.code }.radioState("$nm", "$v")"""))
                  case None    => None // a radio needs a static value option to bind
            case _ =>
              if hasValue(attrs) then None // C4: user set value / bind:value
              else Some(spread(s"""${ form.code }.fieldValue("$nm")"""))

  private def spread(code: String): IrAttr = IrAttr.Spread(ScalaExpr(code))

  private def staticAttr(attrs: List[IrAttr], key: String): Option[String] =
    attrs.collectFirst { case IrAttr.StaticAttr(k, v) if k == key => v }

  private def hasIgnore(attrs: List[IrAttr]): Boolean =
    attrs.exists {
      case IrAttr.StaticAttr("data-form-ignore", _) => true
      case IrAttr.BooleanAttr("data-form-ignore")   => true
      case _                                        => false
    }

  private def hasValue(attrs: List[IrAttr]): Boolean =
    attrs.exists {
      case IrAttr.StaticAttr("value", _)  => true
      case IrAttr.DynamicAttr("value", _) => true
      case IrAttr.BindInputValue(_)       => true
      case IrAttr.BindInputValueInt(_)    => true
      case IrAttr.BindInputValueDouble(_) => true
      case _                              => false
    }

  private def hasChecked(attrs: List[IrAttr]): Boolean =
    attrs.exists {
      case IrAttr.StaticAttr("checked", _)  => true
      case IrAttr.BooleanAttr("checked")    => true
      case IrAttr.DynamicAttr("checked", _) => true
      case IrAttr.BindChecked(_)            => true
      case IrAttr.BindGroup(_, _)           => true
      case _                                => false
    }
