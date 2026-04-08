/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import scala.collection.mutable

import org.scalajs.dom

/** Registry that associates DOM elements with their component cleanup functions.
  *
  * Each generated component's `create()` calls [[register]] with the root element
  * and the `List[() => Unit]` collected by [[Cleanup.popScope]].  This replaces
  * the previous pattern where the `_cleanups` list was silently discarded.
  *
  * [[destroy]] should be called when a component is removed from the DOM (e.g.
  * from [[Mount.unmount]] or the testkit's `unmount()`).  [[destroyTree]] is a
  * convenience that destroys all registered descendants of a given root, which
  * is the right choice when a whole component subtree is unmounted.
  *
  * Because Scala.js is single-threaded a global mutable map is safe.
  */
object Lifecycle:

  private val registry = mutable.HashMap[dom.Element, mutable.ListBuffer[() => Unit]]()

  // ── Registration ──────────────────────────────────────────────────────────

  /** Associates [cleanups] with [el].
    * Called by generated `create()` code via [[Cleanup.popScope]].
    */
  def register(el: dom.Element, cleanups: List[() => Unit]): Unit =
    if cleanups.nonEmpty then
      registry.getOrElseUpdate(el, mutable.ListBuffer.empty) ++= cleanups

  /** Adds a single [cleanup] function to the registry entry for [el].
    * Called by [[OnMount.flush]] to register cleanup functions returned by
    * [[onMount]] callbacks.
    */
  def addCleanup(el: dom.Element, cleanup: () => Unit): Unit =
    registry.getOrElseUpdate(el, mutable.ListBuffer.empty) += cleanup

  // ── Destruction ───────────────────────────────────────────────────────────

  /** Runs and removes all cleanup functions registered for [el]. */
  def destroy(el: dom.Element): Unit =
    registry.remove(el).foreach(fns => Cleanup.runAll(fns.toList))

  /** Runs cleanup functions for [root] and every registered descendant element.
    *
    * Use this when unmounting a whole component subtree — it ensures that every
    * component's reactive subscriptions and resources are released even if the
    * parent was not explicitly destroyed.
    *
    * {{{
    * Lifecycle.destroyTree(appRootElement)
    * appRootElement.remove()
    * }}}
    */
  def destroyTree(root: dom.Element): Unit =
    // root.contains(el) is true when el == root OR el is a descendant of root
    val targets = registry.keys.filter(root.contains).toList
    targets.foreach(destroy)
