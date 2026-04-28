/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.NamedTuple.AnyNamedTuple

import melt.runtime.render.RenderResult

/** On the JVM, provides a `ctx.render(result: => RenderResult)` overload that:
  *  1. Sets [[Router.currentPath]] to the current request path for the duration
  *     of SSR rendering via [[Router.withPath]], so that components can read the
  *     correct path from [[Router.currentPath]] without any manual wiring.
  *  2. Wraps the [[RenderResult]] into a [[Component]] and delegates to the
  *     abstract `ctx.render(Component)`.
  *
  * The `result` parameter is by-name, so the component is only evaluated
  * (i.e. SSR-rendered) inside the `Router.withPath` block.
  *
  * Imported automatically via `import meltkit.*` — no `import given` needed.
  *
  * {{{
  * // No manual Router.withPath needed — the request path is set automatically.
  * app.get("todos") { ctx => IO.delay(ctx.render(TodoPage())) }
  * }}}
  */
extension [F[_], P <: AnyNamedTuple, B](ctx: MeltContext[F, P, B])
  def render(result: => RenderResult): PlainResponse =
    ctx.render(Component.wrap(Router.withPath(ctx.requestPath)(result)))
