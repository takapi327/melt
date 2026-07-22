/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.forms

import melt.runtime.forms.codec.FieldEncoder

/** SSR (JVM) mirror of the client `form.text` spread helper.
  *
  * Returns the `name` + seeded `value` as a `Map[String, Any]`, which the SSR
  * emitter passes to `ServerRenderer.spreadAttrs`. The client returns an
  * `HtmlAttrs` instead; both are produced from the same `form.text(_.field)`
  * call so a crossProject `.melt` compiles for SSR and hydration alike.
  */
extension [A](form: Form[A])
  inline def text[B](inline selector: A => B)(using encoder: FieldEncoder[B]): Map[String, Any] =
    Map(
      "name"  -> form.nameOf(selector),
      "value" -> encoder.encodeValue(selector(form.data.value))
    )

  /** SSR mirror of the client `form.checkbox`. */
  inline def checkbox(inline selector: A => Boolean): Map[String, Any] =
    ControlAttrs.checkbox(form.nameOf(selector), selector(form.data.value))

  /** SSR mirror of the client `form.radio`. */
  inline def radio[B](inline selector: A => B, option: B)(using encoder: FieldEncoder[B]): Map[String, Any] =
    ControlAttrs.radio(form.nameOf(selector), encoder.encodeValue(option), selector(form.data.value) == option)

  /** SSR mirror of the client `form.select`. */
  inline def select[B](inline selector: A => B): Map[String, Any] =
    ControlAttrs.select(form.nameOf(selector))

  /** SSR mirror of the client `form.option`. */
  inline def option[B](inline selector: A => B, opt: B)(using encoder: FieldEncoder[B]): Map[String, Any] =
    ControlAttrs.option(encoder.encodeValue(opt), selector(form.data.value) == opt)

  /** By-name text spread `{...form.field("email")}` — the string-driven mirror of
    * [[text]]. Resolves the field and its encoder from a literal name so the
    * FormBindingPass can inject it for a plain `<input name="email">`; a name with
    * no matching field is a compile error, just like `_.email`.
    */
  inline def field(inline name: String): Map[String, Any] =
    ${ FormMacros.fieldAttrsImpl[A]('form, 'name, true) }

  /** State-only variant used by auto-binding, where the user already wrote the
    * `name` attribute: returns just the seeded `value` (re-emitting `name` would
    * duplicate the attribute the user typed).
    */
  inline def fieldValue(inline name: String): Map[String, Any] =
    ${ FormMacros.fieldAttrsImpl[A]('form, 'name, false) }

  /** By-name mirror of [[checkbox]] — the field must be Boolean. */
  inline def checkboxField(inline name: String): Map[String, Any] =
    ${ FormMacros.checkboxAttrsImpl[A]('form, 'name) }

  /** By-name mirror of [[radio]] — `checked` when the field equals `option`. */
  inline def radioField(inline name: String, inline option: String): Map[String, Any] =
    ${ FormMacros.radioAttrsImpl[A]('form, 'name, 'option) }

  /** By-name mirror of [[select]] — sets `name`. */
  inline def selectField(inline name: String): Map[String, Any] =
    ${ FormMacros.selectAttrsImpl[A]('form, 'name) }

  /** By-name mirror of [[option]] — `selected` when the field equals `option`. */
  inline def optionField(inline name: String, inline option: String): Map[String, Any] =
    ${ FormMacros.optionAttrsImpl[A]('form, 'name, 'option) }

  /** State-only checkbox for auto-binding (the user already wrote name + type). */
  inline def checkedState(inline name: String): Map[String, Any] =
    ${ FormMacros.checkedStateImpl[A]('form, 'name) }

  /** State-only radio for auto-binding (the user already wrote name + type + value). */
  inline def radioState(inline name: String, inline option: String): Map[String, Any] =
    ${ FormMacros.radioStateImpl[A]('form, 'name, 'option) }
