/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import scala.collection.mutable

/** Parent-to-descendant dependency injection via a stack-based approach.
  *
  * Safe for single-threaded Scala.js. `provide` pushes a value onto the stack
  * during component creation; `inject` reads the top (nearest ancestor).
  *
  * {{{
  * // Define
  * val ThemeCtx = Context.create("light")
  *
  * // Parent component
  * ThemeCtx.provide("dark")
  *
  * // Child component
  * val theme = ThemeCtx.inject()  // "dark"
  * }}}
  */
final class Context[A] private (default: A):
  private val stack = mutable.Stack[A]()

  /** Provides a value for descendant components. Automatically removed on cleanup. */
  def provide(value: A): Unit =
    stack.push(value)
    onCleanup(() => { stack.pop(); () })

  /** Reads the nearest ancestor's provided value, or the default. */
  def inject(): A =
    if stack.nonEmpty then stack.top else default

/** Optional context without a default value. */
final class OptionalContext[A] private[runtime]:
  private val stack = mutable.Stack[A]()

  def provide(value: A): Unit =
    stack.push(value)
    onCleanup(() => { stack.pop(); () })

  def inject(): Option[A] =
    if stack.nonEmpty then Some(stack.top) else None

object Context:
  def create[A](default: A): Context[A]         = new Context(default)
  def createOptional[A]:     OptionalContext[A] = new OptionalContext[A]
