/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import melt.runtime.render.RenderResult

/** Node.js-specific [[MeltKit]] router with `C` fixed to [[RenderResult]].
  *
  * {{{
  * val app = MeltKit[IO]()
  * app.get("api/todos") { ctx => IO.pure(ctx.ok(todos)) }
  * app.get("todos")     { ctx => IO.delay(ctx.render(TodoPage())) }
  * }}}
  */
class MeltKit[F[_]] extends MeltKitPlatform[F, RenderResult]

object MeltKit:
  def apply[F[_]](): MeltKit[F] = new MeltKit[F]()
