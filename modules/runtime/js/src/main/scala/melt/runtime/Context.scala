/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import scala.collection.mutable

/** Parent-to-descendant dependency injection bound to the owner tree.
  *
  * `provide` attaches a value to the current [[OwnerNode]]; `inject` walks up the
  * owner ancestry to the nearest provider. Because lookup follows structural
  * ownership rather than temporal push/pop order, it stays correct across async
  * boundaries, event handlers, and sibling subtrees — where a global stack would
  * return whichever provider ran last. The value is released when the owning node
  * is destroyed, so no explicit cleanup is needed.
  *
  * When there is no active owner (top-level scripts, tests), it falls back to a
  * per-instance stack with cleanup-driven pop, preserving the previous behaviour.
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
  private val legacyStack = mutable.Stack[A]()

  /** Provides a value for descendant components. Released with the owner node,
    * or on cleanup in the ownerless fallback path. */
  def provide(value: A): Unit =
    if !Owner.provideContext(this, value) then
      legacyStack.push(value)
      onCleanup(() =>
        legacyStack.pop(); ()
      )

  /** Reads the nearest ancestor's provided value, or the default. */
  def inject(): A =
    Owner.injectContext(this) match
      case Some(value) => value.asInstanceOf[A]
      case None        => if legacyStack.nonEmpty then legacyStack.top else default

/** Optional context without a default value. */
final class OptionalContext[A] private[runtime]:
  private val legacyStack = mutable.Stack[A]()

  def provide(value: A): Unit =
    if !Owner.provideContext(this, value) then
      legacyStack.push(value)
      onCleanup(() =>
        legacyStack.pop(); ()
      )

  def inject(): Option[A] =
    Owner.injectContext(this) match
      case Some(value) => Some(value.asInstanceOf[A])
      case None        => if legacyStack.nonEmpty then Some(legacyStack.top) else None

object Context:
  def create[A](default: A): Context[A]         = new Context(default)
  def createOptional[A]:     OptionalContext[A] = new OptionalContext[A]
