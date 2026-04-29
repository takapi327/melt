/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import melt.runtime.render.RenderResult

/** Type alias for the Node.js server router: `MeltKit[F, RenderResult]`.
  *
  * {{{
  * import meltkit.*
  *
  * def buildApp(): NodeApp[IO] =
  *   val app = NodeApp[IO]()
  *   app.get("api/todos") { ctx => IO.pure(ctx.ok(todos)) }
  *   app.get("todos")     { ctx => IO.delay(ctx.render(TodoPage())) }
  *   app
  * }}}
  */
type NodeApp[F[_]] = MeltKit[F, RenderResult]

object NodeApp:
  def apply[F[_]](): NodeApp[F] = new MeltKit[F, RenderResult]()
