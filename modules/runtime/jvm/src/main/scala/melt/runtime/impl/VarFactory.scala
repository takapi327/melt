/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.impl

import melt.runtime.{ Signal, Var }

/** JVM factory referenced by the shared `object Var`. Produces a no-op
  * wrapper suitable for SSR rendering, where event handlers are stripped
  * and only the initial value is ever observed.
  */
private[runtime] object VarFactory:
  def create[A](initial: A): Var[A] = new JvmVar[A](initial)

/** JVM no-op implementation of [[Var]].
  *
  * `set` / `update` do nothing and no subscribers are ever invoked.
  * `map` / `flatMap` compute exactly once against the initial value and
  * return a frozen [[Signal]]. This matches SSR semantics: the template
  * renders an initial snapshot and the generated code has no event handlers
  * that could ever mutate the value.
  */
private final class JvmVar[A](initial: A) extends Var[A]:
  def value:                   A          = initial
  def set(value:   A):         Unit       = ()
  def update(f:    A => A):    Unit       = ()
  def subscribe(f: A => Unit): () => Unit = () => ()

  def map[B](f:     A => B):         Signal[B] = Signal.pure(f(initial))
  def flatMap[B](f: A => Signal[B]): Signal[B] = f(initial)

  lazy val signal: Signal[A] = Signal.pure(initial)

  private[runtime] def subscribePre(f:  A => Unit): () => Unit = () => ()
  private[runtime] def subscribePost(f: A => Unit): () => Unit = () => ()
