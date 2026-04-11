/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import scala.annotation.targetName

/** JVM no-op implementations of `onMount`.
  *
  * SSR has no DOM lifecycle — the HTML string is generated once and sent to
  * the client. Any `onMount` body is silently dropped on the JVM. If server-
  * side initialisation is needed, pass the data through `Props` instead, or
  * wait for Phase D / MeltKit which introduces a dedicated `load()` hook.
  * See `docs/meltc-ssr-design.md` §10.1.
  */
def onMount(fn: () => Unit): Unit = ()

@targetName("onMountWithCleanup")
def onMount(fn: () => (() => Unit)): Unit = ()
