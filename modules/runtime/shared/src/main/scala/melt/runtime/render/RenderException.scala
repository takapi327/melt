/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.render

/** Raised by the SSR runtime when a render cannot be completed.
  *
  * Used for:
  *   - Component nesting depth exceeding [[ServerRenderer.Config.maxComponentDepth]]
  *     (`§12.2.1` — guards against runaway recursion)
  *   - Output size exceeding [[ServerRenderer.Config.maxOutputBytes]]
  *     (`§12.2.2` — guards against unbounded list rendering)
  *   - A user-code `StackOverflowError` caught at the `render()` entry
  *     boundary and converted to a richer diagnostic
  *
  * Generally consumed at the HTTP handler layer — for example, an http4s
  * route can catch `RenderException` and return HTTP 500 without
  * destabilising the JVM.
  */
class RenderException(message: String, cause: Throwable = null) extends RuntimeException(message, cause)
