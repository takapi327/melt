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
