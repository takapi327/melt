/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.impl

import melt.runtime.Signal

/** JVM factory referenced by the shared `object Signal`. */
private[runtime] object SignalFactory:
  def pure[A](value: A): Signal[A] = new JvmSignal[A](value)

/** JVM no-op implementation of [[Signal]]. */
private final class JvmSignal[A](initial: A) extends Signal[A]:
  def value:                         A          = initial
  def subscribe(f:  A => Unit):      () => Unit = () => ()
  def map[B](f:     A => B):         Signal[B]  = SignalFactory.pure(f(initial))
  def flatMap[B](f: A => Signal[B]): Signal[B]  = f(initial)

  private[runtime] def emit(v:          A):         Unit       = ()
  private[runtime] def subscribePre(f:  A => Unit): () => Unit = () => ()
  private[runtime] def subscribePost(f: A => Unit): () => Unit = () => ()
