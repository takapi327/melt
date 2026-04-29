/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import org.scalajs.dom

/** Browser-specific [[MeltKit]] router with `C` fixed to [[org.scalajs.dom.Element]].
  *
  * {{{
  * val app = MeltKit[Id]()
  * app.get("todos") { ctx => ctx.render(TodoPage()) }
  * BrowserAdapter.mountWithShell(app, rootEl, Layout())
  * }}}
  */
class MeltKit[F[_]] extends MeltKitPlatform[F, dom.Element]

object MeltKit:
  def apply[F[_]](): MeltKit[F] = new MeltKit[F]()
