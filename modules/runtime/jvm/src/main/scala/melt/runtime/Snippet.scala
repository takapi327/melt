/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** JVM / SSR stub for [[Snippet]].
  *
  * On the JVM, snippets are not rendered (SSR components skip snippet calls).
  * The type alias uses `Any` so that shared Props definitions compile without
  * pulling in `org.scalajs.dom`.
  */
type Snippet[A] = A => Any
