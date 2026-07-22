/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.ir.opt

import melt.ir.{ mapChildren, IrAttr, IrComponent, IrNode, ReactiveKind, ScalaExpr }

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
  * == Scope ==
  * Only structural controls (`<input>` / `<select>` / `<option>` / `<textarea>`) are
  * handled. Reactive-region inputs (`{items.map(i => <input …>)}`) live inside a
  * `ScalaExpr` string, not the IR tree, and are never reached — those use the
  * hand-written `{...form.field(...)}` form.
  */
object FormBindingPass extends IrPass:
  val name = "FormBindingPass"

  /** `<input>` types that carry no value to prefill (submit/button/reset/image) or
    * cannot be seeded (file). See design C3.
    */
  private val nonBindableInputTypes = Set("submit", "button", "reset", "image", "file")

  def run(ir: IrComponent): IrComponent =
    ir.copy(template = ir.template.map(process(_, None, None)))

  /** @param form       the `use:form={f}` expression the node currently sits inside.
    * @param selectName the `name` of the enclosing bound `<select>`, propagated so an
    *                   `<option>` can bind against the same field.
    */
  private def process(node: IrNode, form: Option[ScalaExpr], selectName: Option[String]): IrNode = node match
    case e: IrNode.IrElement =>
      val (declared, stripped) = extractUseForm(e.attrs)
      val childForm            = declared.orElse(form) // a nested use:form re-scopes its subtree
      val childSelect          = selectScope(e.tag, stripped, childForm, selectName)
      val children             = e.children.map(process(_, childForm, childSelect))
      val attrs                = stripped ++ injectAttr(e.tag, stripped, form, selectName).toList
      e.copy(attrs = attrs, children = children ++ injectChild(e.tag, stripped, children, form).toList)

    case e: IrNode.IrStaticElement =>
      // A static element carries no directives, so it never declares a scope, but it
      // may be an injectable control (or an ancestor of one, e.g. a wrapping `<label>`
      // or a `<select>` around bound `<option>`s). Injecting makes a node dynamic; a
      // static element must then be promoted to IrElement — when injected on directly
      // AND when any descendant was promoted — otherwise StaticHoistPass would hoist
      // the whole static subtree and the binding would be lost (silently, on SPA).
      val childSelect = selectScope(e.tag, e.attrs, form, selectName)
      val children0   = e.children.map(process(_, form, childSelect))
      val newAttr     = injectAttr(e.tag, e.attrs, form, selectName)
      val newChild    = injectChild(e.tag, e.attrs, children0, form)
      val children    = children0 ++ newChild.toList
      if newAttr.isDefined || newChild.isDefined || children.exists(isDynamic) then
        IrNode.IrElement(e.tag, e.ns, e.attrs ++ newAttr.toList, children, e.scopeId)
      else e.copy(children = children)

    case other => other.mapChildren(process(_, form, selectName))

  /** A node StaticHoistPass would treat as non-static (so a static ancestor holding
    * it must be promoted). Only [[IrNode.IrStaticText]] / [[IrNode.IrStaticElement]] /
    * [[IrNode.IrHoistRef]] children keep an element hoistable. */
  private def isDynamic(node: IrNode): Boolean = node match
    case _: IrNode.IrStaticText | _: IrNode.IrStaticElement | _: IrNode.IrHoistRef => false
    case _                                                                         => true

  /** The `<select>` name in effect for the children of `tag` — set when entering a
    * bound `<select>`, otherwise inherited. */
  private def selectScope(
    tag:        String,
    attrs:      List[IrAttr],
    form:       Option[ScalaExpr],
    selectName: Option[String]
  ): Option[String] =
    if tag == "select" && form.isDefined then staticAttr(attrs, "name") else selectName

  /** Splits `use:form={f}` out of `attrs`, returning the form expression and the
    * remaining attrs. The directive is dropped so no `Bind.action` is emitted for it
    * (a `Form` is not a client action).
    */
  private def extractUseForm(attrs: List[IrAttr]): (Option[ScalaExpr], List[IrAttr]) =
    attrs.collectFirst { case IrAttr.UseAction("form", Some(f)) => f } match
      case Some(f) => (Some(f), attrs.filterNot { case IrAttr.UseAction("form", _) => true; case _ => false })
      case None    => (None, attrs)

  /** The state-only attribute spread to append for an `<input>` / `<option>` under a
    * `use:form` scope, if any. */
  private def injectAttr(
    tag:        String,
    attrs:      List[IrAttr],
    form:       Option[ScalaExpr],
    selectName: Option[String]
  ): Option[IrAttr] =
    form.flatMap { f =>
      tag match
        case "input"  => inputSpread(attrs, f)
        case "option" => optionSpread(attrs, f, selectName)
        case _        => None // <select> keeps the user's name; nothing to inject
    }

  /** A `<textarea>`'s value is child text, not an attribute (C6) — the seed is
    * injected as a text node when the author has not written content themselves. */
  private def injectChild(
    tag:      String,
    attrs:    List[IrAttr],
    children: List[IrNode],
    form:     Option[ScalaExpr]
  ): Option[IrNode] =
    form.flatMap { f =>
      if tag != "textarea" || hasIgnore(attrs) then None
      else
        staticAttr(attrs, "name") match
          case Some(nm) if !children.exists(isContent) =>
            Some(IrNode.IrDynamicText(ScalaExpr(s"""${ f.code }.fieldText("$nm")"""), ReactiveKind.LikelyStatic))
          case _ => None
    }

  private def inputSpread(attrs: List[IrAttr], form: ScalaExpr): Option[IrAttr] =
    if hasIgnore(attrs) then None // data-form-ignore escape hatch
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

  private def optionSpread(attrs: List[IrAttr], form: ScalaExpr, selectName: Option[String]): Option[IrAttr] =
    if hasIgnore(attrs) then None
    else
      selectName match
        case None      => None // not inside a bound <select>
        case Some(sel) =>
          staticAttr(attrs, "value") match
            case Some(v) if !hasSelected(attrs) => Some(spread(s"""${ form.code }.optionState("$sel", "$v")"""))
            case _                              => None // needs a static value, and yields to an explicit `selected`

  private def spread(code: String): IrAttr = IrAttr.Spread(ScalaExpr(code))

  /** A child that counts as author-written textarea content (a non-blank text node or
    * any non-static node) — its presence suppresses the seed injection (C4). */
  private def isContent(node: IrNode): Boolean = node match
    case IrNode.IrStaticText(c) => c.trim.nonEmpty
    case _                      => true

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

  private def hasSelected(attrs: List[IrAttr]): Boolean =
    attrs.exists {
      case IrAttr.StaticAttr("selected", _)  => true
      case IrAttr.BooleanAttr("selected")    => true
      case IrAttr.DynamicAttr("selected", _) => true
      case _                                 => false
    }
