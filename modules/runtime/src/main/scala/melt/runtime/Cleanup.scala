/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import scala.collection.mutable

/** Scoped cleanup mechanism for component subscription management.
  *
  * Each component's `create()` method calls `pushScope()` at the start
  * and `popScope()` at the end. Any reactive subscriptions registered
  * via `register()` during that window are collected and can be run
  * on component destruction to prevent memory leaks.
  *
  * This uses a global stack, which is safe because Scala.js is single-threaded.
  *
  * {{{
  * Cleanup.pushScope()
  * val cancel = someVar.subscribe(...)
  * Cleanup.register(cancel)
  * val cleanups = Cleanup.popScope()
  * // later, on component destroy:
  * Cleanup.runAll(cleanups)
  * }}}
  */
object Cleanup:

  private val scopes = mutable.Stack[mutable.ListBuffer[() => Unit]]()

  /** Begin a new component scope. */
  def pushScope(): Unit =
    scopes.push(mutable.ListBuffer.empty)

  /** End the current scope and return all registered cleanup functions. */
  def popScope(): List[() => Unit] =
    if scopes.nonEmpty then scopes.pop().toList
    else Nil

  /** Register a cleanup function in the current scope.
    * No-op if no scope is active (e.g. during testing without pushScope).
    */
  def register(f: () => Unit): Unit =
    if scopes.nonEmpty then scopes.top += f

  /** Execute all cleanup functions from a scope. */
  def runAll(cleanups: List[() => Unit]): Unit =
    cleanups.foreach(_())

/** Registers a cleanup function in the current component scope.
  *
  * Call this inside `<script lang="scala">` to register code that
  * runs when the component is destroyed.
  *
  * {{{
  * val cancel = someVar.subscribe(v => println(v))
  * onCleanup(() => cancel())
  * }}}
  */
def onCleanup(f: () => Unit): Unit = Cleanup.register(f)
