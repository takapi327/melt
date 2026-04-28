/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** Platform-neutral handle for a rendered UI component.
  *
  * On the JVM (SSR), this wraps a `melt.runtime.render.RenderResult`.
  * On JS  (SPA),     this wraps a `org.scalajs.dom.Element`.
  *
  * Platform-specific [[scala.Conversion]] givens in `meltkit-jvm` and
  * `meltkit-js` make `.melt`-compiled components auto-convert to
  * [[Component]] when passed to [[MeltContext.melt]], so no explicit
  * wrapping is needed at call sites:
  *
  * {{{
  * // shared route handler (compiles on both JVM and JS)
  * app.get("todos") { ctx =>
  *   F.pure(ctx.melt(TodoPage()))  // auto-converts on each platform
  * }
  * }}}
  */
opaque type Component = Any

object Component:

  /** Wraps a platform-specific value as a [[Component]].
    *
    * Not needed in user route handlers — call `ctx.melt(MyPage())` directly;
    * the [[AsComponent]] typeclass handles the wrapping automatically.
    * Provided for adapter and code-generator internals.
    */
  def wrap(a: Any): Component = a

  /** Extracts the underlying platform value. Adapter-internal use only. */
  private[meltkit] def unwrap(c: Component): Any = c
