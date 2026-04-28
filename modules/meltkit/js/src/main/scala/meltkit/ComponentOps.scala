/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.NamedTuple.AnyNamedTuple

import org.scalajs.dom

/** On JS, provides a `ctx.render(element: dom.Element)` overload that wraps
  * the element produced by a `.melt`-compiled component into a [[Component]]
  * and passes it to the abstract `ctx.render(Component)`.
  *
  * Imported automatically via `import meltkit.*` — no `import given` needed.
  *
  * {{{
  * // shared route handler — works on JS without any explicit wrapping
  * app.get("todos") { ctx => ctx.render(TodoPage()) }
  * }}}
  */
extension [F[_], P <: AnyNamedTuple, B](ctx: MeltContext[F, P, B])
  def render(element: dom.Element): PlainResponse =
    ctx.render(Component.wrap(element))
