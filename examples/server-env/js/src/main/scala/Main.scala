/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

import org.scalajs.dom

import components.*
import meltkit.{ *, given }

/** SPA client entry. Renders [[HomePage]], which reads the generated `PublicEnv`
  * (browser-safe) and the server greeting (derived from private env). It cannot
  * reference `PrivateEnv` — that is a JVM-only compile boundary.
  *
  * {{{ sbt "server-env-server/run" }}}
  */
object Main:

  def main(args: Array[String]): Unit =
    val app = MeltKit()
    app.get("")(ctx => ctx.render(HomePage()))
    BrowserAdapter.mount(app, dom.document.getElementById("app"))
