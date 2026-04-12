/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** A memoized reactive value that only propagates when the computed value changes. */
trait Memo[A] extends Signal[A]

/** JVM no-op `memo`. Computes exactly once against the initial value and
  * returns a frozen [[Signal]].
  */
def memo[A, B](dep: Signal[A])(f: A => B): Signal[B] = Signal.pure(f(dep.now()))

def memo[A, B](dep: Var[A])(f: A => B): Signal[B] = Signal.pure(f(dep.now()))
