/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** Context passed to [[onMount]] callbacks that need to register cleanup.
  *
  * Obtain an instance via the `onMount { ctx => ... }` overload. The context
  * is tied to the component's [[OwnerNode]], so any cleanup registered via
  * [[onCleanup]] runs automatically when the component is destroyed.
  *
  * {{{
  * onMount { ctx =>
  *   val id = setInterval(1000.0) { elapsed += 1 }
  *   ctx.onCleanup(() => clearInterval(id))
  * }
  * }}}
  */
trait MountContext:

  /** Registers [f] to run when this component is destroyed.
    *
    * Multiple calls accumulate; all cleanup functions are run in
    * reverse-registration (LIFO) order on component destruction.
    */
  def onCleanup(f: () => Unit): Unit
