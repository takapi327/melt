/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** Centralised runtime warning channel used by [[Escape]] and the SSR
  * helpers (e.g. when a dangerous URL protocol is blocked or a spread
  * attribute is dropped).
  *
  * The default handler writes to `System.err`, which on Scala.js maps to
  * `console.error`, so both platforms behave sensibly out of the box.
  *
  * == Thread-safety contract ==
  *
  * `setHandler` is intended to be called '''exactly once at application
  * startup''', before any render. Dynamic per-request swapping is not
  * supported — the underlying `@volatile var` provides memory visibility
  * but no synchronisation. See `docs/meltc-ssr-design.md` §12.3.3.
  */
object MeltWarnings:

  @volatile private var handler: String => Unit =
    msg => System.err.println(s"[melt] WARN: $msg")

  /** Emits a warning through the currently installed handler. */
  def warn(msg: String): Unit = handler(msg)

  /** Installs a custom warning handler.
    *
    * '''Contract''': call this exactly once at application startup, before
    * any render. Dynamic per-request replacement is unsupported.
    */
  def setHandler(h: String => Unit): Unit = handler = h

  /** Mutes all warnings. Intended for tests or CLI tools that produce their
    * own diagnostics.
    */
  def mute(): Unit = handler = _ => ()

  /** Restores the default stderr handler. Primarily used by tests. */
  def resetHandler(): Unit =
    handler = msg => System.err.println(s"[melt] WARN: $msg")
