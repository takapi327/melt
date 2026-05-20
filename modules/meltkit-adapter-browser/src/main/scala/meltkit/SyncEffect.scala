/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import org.scalajs.dom

/** The identity effect type for synchronous browser-side routing.
  *
  * In a SPA, route handlers run synchronously — `ctx.render(Page())` mutates
  * the DOM immediately and returns a [[PlainResponse]] value. There is no
  * asynchronous effect system involved, so `F = Id` (the identity functor) is
  * always the right choice for [[MeltKit]] and [[BrowserAdapter]] in the browser.
  *
  * Importing `meltkit.*` brings both the type alias and the [[AsyncRunner]]
  * given instance into scope, so users do not need to define them manually:
  *
  * {{{
  * import meltkit.*
  *
  * object Main:
  *   def main(args: Array[String]): Unit =
  *     val app = MeltKit()
  *     app.get("") { ctx => ctx.render(TodoPage()) }
  *     BrowserAdapter.mountWithShell(app, rootEl, Layout())
  * }}}
  */
type Id = [A] =>> A

/** [[AsyncRunner]] for [[Id]]: discards the [[Response]] value.
  *
  * By the time [[BrowserAdapter]] calls `runAndForget`, the route handler has
  * already performed all DOM mutations as a synchronous side effect. The
  * returned [[Response]] value carries no further work, so it is safely
  * discarded.
  */
given AsyncRunner[Id] with
  override def runAndForget(fa: Response): Unit = ()
