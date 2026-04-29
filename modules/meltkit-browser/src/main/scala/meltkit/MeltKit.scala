/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import org.scalajs.dom

/** Browser-specific [[MeltKit]] router with `F` fixed to [[Id]] and `C` fixed to [[org.scalajs.dom.Element]].
  *
  * {{{
  * val app = MeltKit()
  * app.get("todos") { ctx => ctx.render(TodoPage()) }
  * BrowserAdapter.mountWithShell(app, rootEl, Layout())
  * }}}
  */
class MeltKit extends MeltKitPlatform[Id, dom.Element]

object MeltKit:
  def apply(): MeltKit = new MeltKit()
