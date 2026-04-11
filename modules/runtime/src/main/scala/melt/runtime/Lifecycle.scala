/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import scala.collection.mutable

import org.scalajs.dom

/** Registry that associates DOM elements with their component [[OwnerNode]].
  *
  * Each generated component's `create()` calls [[register]] with the root element
  * and the [[OwnerNode]] produced by `Owner.withNew { ... }`.  On component
  * destruction, [[destroy]] or [[destroyTree]] triggers `owner.destroy()`, which
  * cascades through the entire owner tree releasing all subscriptions and resources.
  *
  * Because Scala.js is single-threaded a global mutable map is safe.
  */
object Lifecycle:

  private val registry = mutable.HashMap[dom.Element, OwnerNode]()

  // ── Registration ──────────────────────────────────────────────────────────

  /** Associates [owner] with [el].
    * Called by generated `create()` code via `Owner.withNew { ... }`.
    */
  def register(el: dom.Element, owner: OwnerNode): Unit =
    registry(el) = owner

  /** Adds a single [cleanup] function to the [[OwnerNode]] registered for [el].
    * Called by [[OnMount.flush]] to register cleanup functions returned by
    * [[onMount]] callbacks.
    * No-op if no OwnerNode is registered for [el]; if the node is already
    * destroyed, [cleanup] is invoked immediately by [[OwnerNode.addCleanup]].
    */
  def addCleanup(el: dom.Element, cleanup: () => Unit): Unit =
    registry.get(el).foreach(_.addCleanup(cleanup))

  // ── Destruction ───────────────────────────────────────────────────────────

  /** Destroys the [[OwnerNode]] registered for [el] and removes it from the registry. */
  def destroy(el: dom.Element): Unit =
    registry.remove(el).foreach(_.destroy())

  /** Destroys all registered elements within [root] (inclusive).
    *
    * The correct order is:
    *   1. Snapshot the root's [[OwnerNode]] **before** removing from registry.
    *   2. Collect **all** descendant elements (including root itself).
    *   3. Remove all of them from the registry (prevents double-destroy).
    *   4. Destroy **only** the root's OwnerNode — the owner tree cascades.
    *
    * Collecting targets before any destruction prevents the OOM scenario where
    * a cleanup function removes DOM nodes, causing `root.contains` to return
    * `false` for elements that were not yet processed.
    *
    * {{{
    * Lifecycle.destroyTree(appRootElement)
    * appRootElement.remove()
    * }}}
    */
  def destroyTree(root: dom.Element): Unit =
    // root.contains(el) is true when el == root OR el is a descendant of root
    val targets = registry.keys.filter(root.contains).toList
    val nodes   = targets.flatMap(registry.get)
    targets.foreach(registry.remove)
    // Destroy all nodes. OwnerNode.destroy() is idempotent — children that are
    // destroyed by a parent's cascade become no-ops when reached directly.
    nodes.foreach(_.destroy())
