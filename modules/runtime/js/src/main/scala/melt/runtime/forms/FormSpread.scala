/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.forms

import melt.runtime.forms.codec.FieldEncoder
import melt.runtime.HtmlAttrs

/** Spread helper for a text input, deriving both `name` and the seeded `value`
  * from one type-checked field selector:
  *
  * {{{
  * <input {...form.text(_.email)} type="email"/>
  * }}}
  *
  * `_.email` fixes the `name` at compile time (a typo is a compile error and it
  * matches the server's `FormDataDecoder`), and the `value` is the field's current
  * value. The client returns an [[HtmlAttrs]] whose `apply(el)` the SPA spread
  * calls; the JVM mirror returns a `Map[String, Any]` for the SSR renderer.
  *
  * The value is a one-shot seed — inputs keep what the user typed, so there is no
  * need to make it reactive (unlike error displays, which use `form.data`).
  */
extension [A](form: Form[A])
  inline def text[B](inline selector: A => B)(using encoder: FieldEncoder[B]): HtmlAttrs =
    HtmlAttrs(
      Map(
        "name"  -> form.nameOf(selector),
        "value" -> encoder.encodeValue(selector(form.data.value))
      )
    )

  /** `<input type="checkbox" {...form.checkbox(_.remember)}/>` — name + `checked`
    * from a Boolean field (`value="true"` so a checked box decodes as `true`).
    */
  inline def checkbox(inline selector: A => Boolean): HtmlAttrs =
    strAttrs(ControlAttrs.checkbox(form.nameOf(selector), selector(form.data.value)))

  /** `<input type="radio" {...form.radio(_.role, Role.Admin)}/>` — name + value +
    * `checked` when the field currently equals this option.
    */
  inline def radio[B](inline selector: A => B, option: B)(using encoder: FieldEncoder[B]): HtmlAttrs =
    strAttrs(
      ControlAttrs.radio(form.nameOf(selector), encoder.encodeValue(option), selector(form.data.value) == option)
    )

  /** `<select {...form.select(_.role)}>` — sets `name`; the chosen option is
    * marked by [[option]].
    */
  inline def select[B](inline selector: A => B): HtmlAttrs =
    strAttrs(ControlAttrs.select(form.nameOf(selector)))

  /** `<option {...form.option(_.role, Role.Admin)}>` — value + `selected` when the
    * field currently equals this option.
    */
  inline def option[B](inline selector: A => B, opt: B)(using encoder: FieldEncoder[B]): HtmlAttrs =
    strAttrs(ControlAttrs.option(encoder.encodeValue(opt), selector(form.data.value) == opt))

  /** By-name text spread `{...form.field("email")}` — the string-driven mirror of
    * [[text]]. Resolves the field and its encoder from a literal name so the
    * FormBindingPass can inject it for a plain `<input name="email">`; a name with
    * no matching field is a compile error, just like `_.email`.
    */
  inline def field(inline name: String): HtmlAttrs = strAttrs(fieldMap(form, name))

  /** State-only variant used by auto-binding, where the user already wrote the
    * `name` attribute: returns just the seeded `value` (re-emitting `name` would
    * duplicate the attribute the user typed).
    */
  inline def fieldValue(inline name: String): HtmlAttrs = strAttrs(fieldValueMap(form, name))

  /** By-name mirror of [[checkbox]] — the field must be Boolean. */
  inline def checkboxField(inline name: String): HtmlAttrs = strAttrs(checkboxFieldMap(form, name))

  /** By-name mirror of [[radio]] — `checked` when the field equals `option`. */
  inline def radioField(inline name: String, inline option: String): HtmlAttrs =
    strAttrs(radioFieldMap(form, name, option))

  /** By-name mirror of [[select]] — sets `name`. */
  inline def selectField(inline name: String): HtmlAttrs = strAttrs(selectFieldMap(form, name))

  /** By-name mirror of [[option]] — `selected` when the field equals `option`. */
  inline def optionField(inline name: String, inline option: String): HtmlAttrs =
    strAttrs(optionFieldMap(form, name, option))

  /** State-only checkbox for auto-binding (the user already wrote name + type). */
  inline def checkedState(inline name: String): HtmlAttrs = strAttrs(checkedStateMap(form, name))

  /** State-only radio for auto-binding (the user already wrote name + type + value). */
  inline def radioState(inline name: String, inline option: String): HtmlAttrs =
    strAttrs(radioStateMap(form, name, option))

  /** State-only `<option>` for auto-binding (the user already wrote value). */
  inline def optionState(inline name: String, inline option: String): HtmlAttrs =
    strAttrs(optionStateMap(form, name, option))

  /** The field's current wire value, for seeding a `<textarea>`'s child text. */
  inline def fieldText(inline name: String): String =
    ${ FormMacros.fieldTextImpl[A]('form, 'name) }

// The macro splice must be the whole RHS (it cannot be wrapped in `strAttrs`), so
// these thin helpers carry the splice and the extension methods convert the result.
private inline def fieldMap[A](form: Form[A], inline name: String): Map[String, Any] =
  ${ FormMacros.fieldAttrsImpl[A]('form, 'name, true) }

private inline def fieldValueMap[A](form: Form[A], inline name: String): Map[String, Any] =
  ${ FormMacros.fieldAttrsImpl[A]('form, 'name, false) }

private inline def checkboxFieldMap[A](form: Form[A], inline name: String): Map[String, Any] =
  ${ FormMacros.checkboxAttrsImpl[A]('form, 'name) }

private inline def radioFieldMap[A](form: Form[A], inline name: String, inline option: String): Map[String, Any] =
  ${ FormMacros.radioAttrsImpl[A]('form, 'name, 'option) }

private inline def selectFieldMap[A](form: Form[A], inline name: String): Map[String, Any] =
  ${ FormMacros.selectAttrsImpl[A]('form, 'name) }

private inline def optionFieldMap[A](form: Form[A], inline name: String, inline option: String): Map[String, Any] =
  ${ FormMacros.optionAttrsImpl[A]('form, 'name, 'option) }

private inline def checkedStateMap[A](form: Form[A], inline name: String): Map[String, Any] =
  ${ FormMacros.checkedStateImpl[A]('form, 'name) }

private inline def radioStateMap[A](form: Form[A], inline name: String, inline option: String): Map[String, Any] =
  ${ FormMacros.radioStateImpl[A]('form, 'name, 'option) }

private inline def optionStateMap[A](form: Form[A], inline name: String, inline option: String): Map[String, Any] =
  ${ FormMacros.optionStateImpl[A]('form, 'name, 'option) }

private inline def strAttrs(attrs: Map[String, Any]): HtmlAttrs =
  HtmlAttrs(attrs.map { case (k, v) => k -> v.toString })
