/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import org.scalajs.dom

/** JS-side handle for a client-rendered component.
  *
  * Wraps a [[org.scalajs.dom.Element]] produced by `meltc`-generated SPA code.
  * Platform-specific extension methods in [[ComponentOps]] make `.melt`-compiled
  * components auto-convert to [[Component]] when passed to [[MeltContext.render]],
  * so no explicit wrapping is needed at call sites:
  *
  * {{{
  * app.get("todos") { ctx =>
  *   ctx.render(TodoPage())  // dom.Element auto-converts to Component
  * }
  * }}}
  */
opaque type Component = dom.Element

object Component:

  /** Wraps a [[dom.Element]] as a [[Component]]. */
  def wrap(e: dom.Element): Component = e

  /** Extracts the underlying [[dom.Element]]. Adapter-internal use only. */
  private[meltkit] def unwrap(c: Component): dom.Element = c
