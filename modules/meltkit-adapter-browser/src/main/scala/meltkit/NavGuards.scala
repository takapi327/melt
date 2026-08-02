/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** Registry of navigation guards consulted before each client-side navigation.
  *
  * A guard receives `(from, to)` paths and returns `false` to cancel the
  * navigation (e.g. an auth check, or an "unsaved changes" prompt). Kept free of
  * any DOM dependency so the decision logic is unit-testable; [[Router]] wires it
  * into the History API.
  */
object NavGuards:

  private var guards: List[(String, String) => Boolean] = Nil

  /** Registers a guard; returns a function that unregisters it. */
  def register(guard: (String, String) => Boolean): () => Unit =
    guards = guards :+ guard
    () => guards = guards.filterNot(_ eq guard)

  /** True iff every registered guard permits navigating from `from` to `to`. */
  def allows(from: String, to: String): Boolean = guards.forall(_(from, to))

  /** Number of currently-registered guards. */
  private[meltkit] def size: Int = guards.length

  /** Removes all guards (test helper). */
  private[meltkit] def clear(): Unit = guards = Nil
