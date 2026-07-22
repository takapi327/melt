/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.forms

import scala.quoted.*

import melt.runtime.forms.codec.FieldEncoder

/** Compile-time support for [[Form.nameOf]].
  *
  * Extracts a field name from a simple selector lambda (`_.email`) so that the
  * HTML `name` attribute of a form input can be derived from a type-checked
  * accessor rather than a hand-written string — a typo (`_.emial`) becomes a
  * compile error, and the derived name is exactly the case-class field label
  * that `FormDataDecoder` reads.
  */
private[forms] object FormMacros:

  def nameOfImpl[T: Type](selector: Expr[T])(using Quotes): Expr[String] =
    import quotes.reflect.*

    def bail(term: Term): Nothing =
      report.errorAndAbort(
        s"nameOf expects a field selector such as `_.email` or a nested `_.address.city`, but got: ${ term.show }"
      )

    // Only case-class fields form the path — a method call like `_.email.trim`
    // is rejected so the derived name always matches a real (decodable) field.
    def isField(sel: Select): Boolean = sel.symbol.flags.is(Flags.CaseAccessor)

    // Unwrap the inlined lambda and collect the chain of field accesses, so
    // `_.email` -> ["email"] and `_.address.city` -> ["address", "city"].
    def segments(term: Term): List[String] =
      term match
        case Inlined(_, _, inner)                     => segments(inner)
        case Block(List(defDef: DefDef), _)           => defDef.rhs.fold(bail(term))(segments) // lambda body
        case Block(Nil, inner)                        => segments(inner)
        case Typed(inner, _)                          => segments(inner)
        case Ident(_)                                 => Nil                                   // the lambda parameter
        case sel @ Select(qual, name) if isField(sel) => segments(qual) :+ name                // x.field / x.a.b
        case Apply(sel @ Select(qual, name), Nil) if isField(sel) => segments(qual) :+ name // getter form
        case _                                                    => bail(term)

    val path = segments(selector.asTerm)
    if path.isEmpty then bail(selector.asTerm)
    Expr(path.mkString("."))

  /** Compile-time support for the by-name field spread (`form.field("email")`),
    * the string-driven counterpart of [[nameOfImpl]] used by form auto-binding.
    *
    * Where the selector form (`form.text(_.email)`) lets the Scala typer infer the
    * field type and summon its `FieldEncoder`, the untyped `.melt` compiler can
    * only inject a string literal — so this macro does that resolution itself:
    * it walks `name` (possibly dotted for a nested field) over the case fields of
    * `A`, builds the value accessor `form.data.value.<path>`, resolves the field
    * type via `memberType`, and summons `FieldEncoder` for it. A name with no
    * matching field, or a field whose type has no encoder in scope, is a compile
    * error — exactly as strong as the selector form.
    *
    * @param includeName when true the result carries the HTML `name` (hand-written
    *   `{...form.field("x")}`); when false only the state `value` is returned
    *   (auto-injection, where the user already wrote `name="x"`, so re-emitting it
    *   would duplicate the attribute).
    */
  def fieldAttrsImpl[A: Type](
    form:        Expr[Form[A]],
    name:        Expr[String],
    includeName: Boolean
  )(using Quotes): Expr[Map[String, Any]] =
    import quotes.reflect.*
    val (accessor, cur, nm) = resolveField[A](form, name)
    // Bind the dynamically-resolved field type so `Expr.summon` has a static type.
    cur.asType match
      case '[b] =>
        val enc   = summonEncoder[b](nm)
        val value = '{ $enc.encodeValue(${ accessor.asExprOf[b] }) }
        if includeName then '{ Map[String, Any]("name" -> ${ Expr(nm) }, "value" -> $value) }
        else '{ Map[String, Any]("value" -> $value) }

  /** By-name mirror of `form.checkbox` — requires a Boolean field. */
  def checkboxAttrsImpl[A: Type](form: Expr[Form[A]], name: Expr[String])(using Quotes): Expr[Map[String, Any]] =
    import quotes.reflect.*
    val (accessor, cur, nm) = resolveField[A](form, name)
    if !(cur =:= TypeRepr.of[Boolean]) then
      report.errorAndAbort(s"Checkbox field '$nm' must be a Boolean field, but found ${ cur.show }")
    '{ ControlAttrs.checkbox(${ Expr(nm) }, ${ accessor.asExprOf[Boolean] }) }

  /** By-name mirror of `form.radio` — `checked` when the field's wire value equals
    * `option` (both sides encoded through the field's `FieldEncoder`).
    */
  def radioAttrsImpl[A: Type](form: Expr[Form[A]], name: Expr[String], option: Expr[String])(using
    Quotes
  ): Expr[Map[String, Any]] =
    import quotes.reflect.*
    val (accessor, cur, nm) = resolveField[A](form, name)
    val opt                 = literal(option, "radioField")
    cur.asType match
      case '[b] =>
        val enc = summonEncoder[b](nm)
        '{
          ControlAttrs.radio(
            ${ Expr(nm) },
            ${ Expr(opt) },
            $enc.encodeValue(${ accessor.asExprOf[b] }) == ${ Expr(opt) }
          )
        }

  /** By-name mirror of `form.select` — sets `name` (option marks the selection). */
  def selectAttrsImpl[A: Type](form: Expr[Form[A]], name: Expr[String])(using Quotes): Expr[Map[String, Any]] =
    val (_, _, nm) = resolveField[A](form, name)
    '{ ControlAttrs.select(${ Expr(nm) }) }

  /** State-only checkbox (`checked` only) for auto-binding — the user wrote `name`
    * and `type="checkbox"`, so only the reflected state is injected. Boolean field.
    */
  def checkedStateImpl[A: Type](form: Expr[Form[A]], name: Expr[String])(using Quotes): Expr[Map[String, Any]] =
    import quotes.reflect.*
    val (accessor, cur, nm) = resolveField[A](form, name)
    if !(cur =:= TypeRepr.of[Boolean]) then
      report.errorAndAbort(s"Checkbox field '$nm' must be a Boolean field, but found ${ cur.show }")
    '{ ControlAttrs.checkedState(${ accessor.asExprOf[Boolean] }) }

  /** State-only radio (`checked` only) for auto-binding — the user wrote `name`,
    * `type="radio"` and `value`, so only the reflected state is injected.
    */
  def radioStateImpl[A: Type](form: Expr[Form[A]], name: Expr[String], option: Expr[String])(using
    Quotes
  ): Expr[Map[String, Any]] =
    import quotes.reflect.*
    val (accessor, cur, nm) = resolveField[A](form, name)
    val opt                 = literal(option, "radio auto-binding")
    cur.asType match
      case '[b] =>
        val enc = summonEncoder[b](nm)
        '{ ControlAttrs.checkedState($enc.encodeValue(${ accessor.asExprOf[b] }) == ${ Expr(opt) }) }

  /** State-only `<option>` (`selected` only) for auto-binding — the user wrote the
    * `<option value>`; `selected` reflects whether the (parent-`<select>`) field
    * currently equals this option.
    */
  def optionStateImpl[A: Type](form: Expr[Form[A]], name: Expr[String], option: Expr[String])(using
    Quotes
  ): Expr[Map[String, Any]] =
    import quotes.reflect.*
    val (accessor, cur, nm) = resolveField[A](form, name)
    val opt                 = literal(option, "option auto-binding")
    cur.asType match
      case '[b] =>
        val enc = summonEncoder[b](nm)
        '{ ControlAttrs.selectedState($enc.encodeValue(${ accessor.asExprOf[b] }) == ${ Expr(opt) }) }

  /** The current wire value of a field as a plain `String`, for seeding a
    * `<textarea>`'s child text (its value is content, not an attribute — C6).
    */
  def fieldTextImpl[A: Type](form: Expr[Form[A]], name: Expr[String])(using Quotes): Expr[String] =
    import quotes.reflect.*
    val (accessor, cur, nm) = resolveField[A](form, name)
    cur.asType match
      case '[b] =>
        val enc = summonEncoder[b](nm)
        '{ $enc.encodeValue(${ accessor.asExprOf[b] }) }

  /** By-name mirror of `form.option` — `selected` when the field's wire value equals `option`. */
  def optionAttrsImpl[A: Type](form: Expr[Form[A]], name: Expr[String], option: Expr[String])(using
    Quotes
  ): Expr[Map[String, Any]] =
    import quotes.reflect.*
    val (accessor, cur, nm) = resolveField[A](form, name)
    val opt                 = literal(option, "optionField")
    cur.asType match
      case '[b] =>
        val enc = summonEncoder[b](nm)
        '{ ControlAttrs.option(${ Expr(opt) }, $enc.encodeValue(${ accessor.asExprOf[b] }) == ${ Expr(opt) }) }

  /** Resolves a dotted `name` against `A`, returning the value accessor
    * (`form.data.value.<path>`), the resolved (substituted) field type, and the
    * literal name. A non-literal name or an unknown field is a compile error.
    */
  private def resolveField[A: Type](form: Expr[Form[A]], name: Expr[String])(using
    q: Quotes
  ): (q.reflect.Term, q.reflect.TypeRepr, String) =
    import q.reflect.*
    val nm = literal(name, "form.field/fieldValue")
    var accessor: Term     = '{ $form.data.value }.asTerm
    var cur:      TypeRepr = TypeRepr.of[A]
    for seg <- nm.split('.').toList do
      val owner    = cur.typeSymbol
      val fieldSym = owner.caseFields
        .find(_.name == seg)
        .getOrElse(
          report.errorAndAbort(
            s"Form model ${ TypeRepr.of[A].show } has no field '$seg'" +
              (if owner.caseFields.nonEmpty then s". Available: ${ owner.caseFields.map(_.name).mkString(", ") }"
               else "")
          )
        )
      accessor = Select(accessor, fieldSym)
      cur      = cur.memberType(fieldSym)
    (accessor, cur, nm)

  /** Reads a compile-time string literal or aborts with a `who`-specific message. */
  private def literal(expr: Expr[String], who: String)(using Quotes): String =
    import quotes.reflect.*
    expr.value.getOrElse(
      report.errorAndAbort(
        s"$who requires a string literal; a non-constant expression cannot be type-checked at compile time"
      )
    )

  /** Summons `FieldEncoder[b]` for a resolved field type or aborts. */
  private def summonEncoder[b: Type](nm: String)(using Quotes): Expr[FieldEncoder[b]] =
    import quotes.reflect.*
    val shown = TypeRepr.of[b].show
    Expr
      .summon[FieldEncoder[b]]
      .getOrElse(
        report.errorAndAbort(
          s"No FieldEncoder in scope for field '$nm' of type $shown. " +
            s"Provide a `given FieldEncoder[$shown]` (or a FieldCodec)."
        )
      )
