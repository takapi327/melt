/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.forms

/** SSR (JVM) mirror of the client `form.text` spread helper.
  *
  * Returns the `name` + seeded `value` as a `Map[String, Any]`, which the SSR
  * emitter passes to `ServerRenderer.spreadAttrs`. The client returns an
  * `HtmlAttrs` instead; both are produced from the same `form.text(_.field)`
  * call so a crossProject `.melt` compiles for SSR and hydration alike.
  */
extension [A](form: Form[A])
  inline def text[B](inline selector: A => B): Map[String, Any] =
    Map(
      "name"  -> form.nameOf(selector),
      "value" -> selector(form.data.value).toString
    )
