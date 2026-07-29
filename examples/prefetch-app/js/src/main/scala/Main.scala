/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

import org.scalajs.dom

import components.*
import meltkit.{ *, given }

/** SPA client entry.
  *
  * Mounts [[Shell]] once as a persistent shell and routes navigations into its
  * `[data-melt-outlet]`. `app.prefetch("items")` declares what to warm before a
  * navigation to `/items`; the `data-melt-preload` attribute on the nav (see
  * `Shell.melt`) makes hovering the Items link run it, so the page then renders
  * with no loading flash.
  *
  * {{{ sbt "prefetch-app-server/run" }}}
  */
object Main:

  def main(args: Array[String]): Unit =
    val rootEl = dom.document.getElementById("app")
    BrowserAdapter.mountWithShell(buildApp(), rootEl, Shell())

  private def buildApp(): MeltKit =
    val app = MeltKit()
    app.get("")(ctx => ctx.render(HomePage()))
    app.get("items")(ctx => ctx.render(ItemsPage()))
    app.prefetch("items")(() => Api.items.prefetch())
    app
