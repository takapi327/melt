/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** Refreshes live queries by tag — a '''no-op on the JVM'''.
  *
  * SSR never invalidates (each render is one-shot and `Query.refresh` is itself a
  * JVM no-op). This object exists only so a `.melt` component that calls
  * `Invalidate(...)` compiles for the JVM (SSR) target as well as JS; the real
  * behaviour lives in the JS counterpart. Kept as a platform split (JVM no-op / JS
  * registry) rather than a JS-only file, exactly like `Query.refresh`.
  */
object Invalidate:

  /** Refreshes every live query carrying `tag`. No-op on the JVM. */
  def apply(tag: String): Unit = ()

  /** Refreshes every live query on the current page. No-op on the JVM. */
  def all(): Unit = ()
