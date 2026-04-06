/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** Acquires a resource and registers its release for automatic cleanup.
  *
  * When used at component top-level, the resource is released on component
  * destruction. When used inside `effect`, it is released on each
  * re-execution (enabling dynamic reconnection patterns).
  *
  * {{{
  * val ws = managed(
  *   new dom.WebSocket("ws://localhost:8080"),
  *   ws => ws.close()
  * )
  * }}}
  */
def managed[A](acquire: => A, release: A => Unit): A =
  val resource = acquire
  onCleanup(() => release(resource))
  resource
