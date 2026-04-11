/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** JVM no-op implementations of `effect` / `layoutEffect`.
  *
  * SSR has no DOM lifecycle to run side-effects against, so these are all
  * no-ops. The effect body is never executed on JVM — if your `.melt` file
  * needs server-side initialisation, move that logic into `Props` (Phase A)
  * or a dedicated `load()` hook (Phase D / MeltKit). See
  * `docs/meltc-ssr-design.md` §10.1.
  */
def effect[A](dep: Var[A])(f:    A => Unit): Unit = ()
def effect[A](dep: Signal[A])(f: A => Unit): Unit = ()

def effect[A, B](depA: Var[A], depB: Var[B])(f: (A, B) => Unit): Unit = ()

def layoutEffect[A](dep: Var[A])(f:    A => Unit): Unit = ()
def layoutEffect[A](dep: Signal[A])(f: A => Unit): Unit = ()

def layoutEffect[A, B](depA: Var[A], depB: Var[B])(f: (A, B) => Unit): Unit = ()
