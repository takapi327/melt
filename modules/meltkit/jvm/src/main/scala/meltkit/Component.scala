/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import melt.runtime.render.RenderResult

/** JVM-side handle for a server-rendered component.
  *
  * Wraps a [[melt.runtime.render.RenderResult]] produced by `meltc`-generated
  * SSR code. Platform-specific extension methods in [[ComponentOps]] make
  * `.melt`-compiled components auto-convert to [[Component]] when passed to
  * [[MeltContext.render]], so no explicit wrapping is needed at call sites:
  *
  * {{{
  * app.get("todos") { ctx =>
  *   IO.delay(ctx.render(TodoPage()))  // RenderResult auto-converts to Component
  * }
  * }}}
  */
opaque type Component = RenderResult

object Component:

  /** Wraps a [[RenderResult]] as a [[Component]]. */
  def wrap(r: RenderResult): Component = r

  /** Extracts the underlying [[RenderResult]]. Adapter-internal use only. */
  private[meltkit] def unwrap(c: Component): RenderResult = c
