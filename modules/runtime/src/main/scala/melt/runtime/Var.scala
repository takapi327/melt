/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** A reactive mutable value.
  *
  * Phase 0: stub implementation A complete implementation will be provided in Phase 1.
  */
class Var[A] private (private var current: A)

object Var:
  def apply[A](initial: A): Var[A] = new Var(initial)
