/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import scala.annotation.targetName
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
    * Delegates to [[Owner.register]] so that cleanup functions are attributed
    * to the current [[OwnerNode]] rather than the legacy stack-based scope.
    * Falls back to the legacy stack scope when no Owner is active (backward compat).
    */
  def register(f: () => Unit): Unit =
    if Owner.current.isDefined then Owner.register(f)
    else if scopes.nonEmpty then scopes.top += f

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

/** Registers [fn] to run after this component is first inserted into the DOM.
  *
  * The callback executes synchronously after `appendChild`, before the browser
  * paints — identical to Svelte's `onMount` semantics.  This makes it safe to
  * read DOM geometry (e.g. `getBoundingClientRect`) inside [fn].
  *
  * Nested components follow child-before-parent execution order.
  *
  * {{{
  * <script lang="scala">
  *   val ref   = Ref[dom.Element]()
  *   val width = Var(0)
  *
  *   onMount { () =>
  *     width.set(ref.value.getBoundingClientRect().width.toInt)
  *   }
  * </script>
  * }}}
  */
def onMount(fn: () => Unit): Unit =
  OnMount.register(() => { fn(); None })

/** Registers [fn] to run after this component is first inserted into the DOM.
  *
  * If [fn] returns a cleanup function, that function is registered as a
  * component destructor — equivalent to calling [[onCleanup]] inside [fn].
  *
  * {{{
  * <script lang="scala">
  *   onMount { () =>
  *     val observer = new dom.IntersectionObserver(...)
  *     observer.observe(myEl)
  *     () => observer.disconnect()  // runs on component destroy
  *   }
  * </script>
  * }}}
  */
@targetName("onMountWithCleanup")
def onMount(fn: () => (() => Unit)): Unit =
  OnMount.register(() => Some(fn()))
