/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

import org.scalajs.dom

import components.*
import meltkit.{ *, given }

/** SPA client entry point.
  *
  * Renders [[Layout]] once as a persistent shell, then routes URL changes
  * to the matching page component via [[BrowserAdapter.mountWithShell]].
  * The shell (navigation bar) stays in the DOM across navigations — only
  * the `[data-melt-outlet]` element inside [[Layout]] is replaced.
  *
  * {{{ sbt "~http4s-spa-server/reStart" }}}
  */
object Main:

  val userId = param[Int]("userId")

  def main(args: Array[String]): Unit =
    val rootEl = dom.document.getElementById("app")
    BrowserAdapter.mountWithShell(buildApp(), rootEl, Layout())

  private def buildApp(): MeltKit =
    val app = MeltKit()
    app.get("") { ctx => ctx.render(TodoPage()) }
    app.get("counter") { ctx => ctx.render(CounterPage()) }
    app.get("users") { ctx => ctx.render(UserPage()) }
    app.get("users" / userId) { ctx => ctx.render(UserDetailPage(UserDetailPage.Props(userId = ctx.params.userId))) }
    app
