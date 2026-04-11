/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** JVM no-op `memo`. Computes exactly once against the initial value and
  * returns a frozen [[Signal]] — SSR never re-emits, so no equality check is
  * needed.
  */
def memo[A, B](dep: Signal[A])(f: A => B): Signal[B] = Signal.pure(f(dep.now()))

def memo[A, B](dep: Var[A])(f: A => B): Signal[B] = Signal.pure(f(dep.now()))
