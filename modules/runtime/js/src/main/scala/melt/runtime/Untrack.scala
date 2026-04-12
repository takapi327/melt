/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** Reads a reactive value without registering a dependency.
  * Since Melt uses explicit dependency specification (not auto-tracking),
  * this is equivalent to calling `.now()`. Provided for API compatibility
  * with frameworks that use automatic dependency tracking.
  */
def untrack[A](v: Var[A]):    A = v.now()
def untrack[A](s: Signal[A]): A = s.now()
def untrack[A](f: => A):      A = f
