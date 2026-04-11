/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import melt.runtime.impl.JsSignal

/** Creates a memoized [[Signal]] that only propagates when the computed value changes.
  *
  * Unlike `Signal.map`, which always emits downstream, `memo` checks referential
  * equality (`!=`) and suppresses redundant updates.
  *
  * {{{
  * val count  = Var(0)
  * val isEven = memo(count)(_ % 2 == 0) // only emits when parity changes
  * }}}
  */
def memo[A, B](dep: Signal[A])(f: A => B): Signal[B] =
  val derived = JsSignal.create[B](f(dep.now()))
  val cancel  = dep.subscribe { a =>
    val newVal = f(a)
    if newVal != derived.now() then derived.emit(newVal)
  }
  Cleanup.register(cancel)
  derived

def memo[A, B](dep: Var[A])(f: A => B): Signal[B] =
  memo(dep.signal)(f)
