/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import melt.runtime.dom.Element

/** JVM no-op implementations of built-in actions.
  *
  * SSR does not execute actions — these stubs exist for type compatibility
  * in shared code.
  */
val autoFocus: Action[Unit] = Action.simple { _ =>
  () => ()
}

val clickOutside: Action[() => Unit] = Action[(() => Unit)] { (_, _) =>
  () => ()
}

val trapFocus: Action[Unit] = Action.simple { _ =>
  () => ()
}
