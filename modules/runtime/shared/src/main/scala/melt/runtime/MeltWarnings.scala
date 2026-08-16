/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** The last-resort destination for runtime warnings.
  *
  * Every warning-producing entry point ([[Escape]], the SSR spread helpers) takes
  * a `sink: MeltWarning => Unit` parameter that defaults to [[emit]]. Server-side
  * rendering supplies its own sink through `ServerRenderer.Config.warningSink`, so
  * the handler installed here is only consulted when nothing closer is in scope —
  * client-side code, or a direct `Escape.url(...)` call outside a render.
  *
  * == Thread-safety contract ==
  *
  * `setHandler` decides where warnings go for the whole process and is intended to
  * be called '''exactly once at application startup''', before any render. Do not
  * swap it per request or per test: the underlying `@volatile var` gives memory
  * visibility but no isolation, so a concurrent swap loses or misattributes
  * warnings. To capture warnings for one render, pass a `sink` instead.
  */
object MeltWarnings:

  private val defaultHandler: MeltWarning => Unit =
    w => System.err.println(s"[melt] WARN: ${ w.message }")

  @volatile private var handler: MeltWarning => Unit = defaultHandler

  /** The default sink: routes to the handler installed by [[setHandler]]. */
  def emit(w: MeltWarning): Unit = handler(w)

  /** Installs the process-wide warning handler.
    *
    * '''Contract''': call this exactly once at application startup, before any
    * render. See the thread-safety note on this object.
    */
  def setHandler(h: MeltWarning => Unit): Unit = handler = h

  /** Mutes warnings process-wide. Intended for CLI tools that produce their own
    * diagnostics. Tests should pass a no-op `sink` to the call under test instead,
    * so that concurrently running suites are unaffected.
    */
  def mute(): Unit = handler = _ => ()

  /** Restores the default stderr handler. */
  def resetHandler(): Unit = handler = defaultHandler
