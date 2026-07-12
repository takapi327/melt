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
