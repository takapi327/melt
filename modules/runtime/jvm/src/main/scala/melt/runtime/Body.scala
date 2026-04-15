/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** JVM no-op implementation of Body.
  *
  * SSR has no live document.body — event listeners are silently ignored.
  */
object Body:
  def on(event: String)(handler: melt.runtime.dom.Event => Unit): Unit = ()
