/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package example

import scala.scalajs.js.annotation.JSExportTopLevel

import org.scalajs.dom

import components.*
import meltkit.{ *, given }

/** Client hydration entry for the nested-layouts example.
  *
  * The SSR bootstrap (generated with `routerHydration = Some("app")`) is a single
  * `import(appChunk).then(m => m.hydrate())`, so the whole layout+page tree is
  * hydrated by this one entry instead of one hydrate call per component. It rebuilds
  * the same routes + layouts as the server and calls [[BrowserAdapter.hydrate]],
  * which claims the server-rendered DOM in place and then takes over navigation.
  */
object Main:

  @JSExportTopLevel("hydrate", moduleID = "app")
  def hydrate(): Unit =
    BrowserAdapter.hydrate(buildApp(), dom.document.getElementById("app"))

  /** The same routes + layouts the JVM main registers for SSR. */
  private def buildApp(): MeltKit =
    val app = MeltKit()
    app.layout("")(c => AppShell(children = c))
    app.layout("dashboard")(c => DashboardLayout(children = c))
    app.get("")(ctx => ctx.render(HomePage()))
    app.get("dashboard/stats")(ctx => ctx.render(StatsPage()))
    app
