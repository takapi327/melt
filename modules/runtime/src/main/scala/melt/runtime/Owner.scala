/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import scala.scalajs.js

/** A node in the owner tree that tracks reactive resources and cleanup functions.
  *
  * OwnerNode forms a doubly-linked tree (parent → first/last child, sibling ↔ sibling)
  * mirroring the approach used by Svelte 5's effect tree. This enables structured
  * resource management: when a node is destroyed, all its children are destroyed first
  * (FIFO order), then its own cleanups run in reverse registration order (LIFO).
  *
  * The tree is maintained as a doubly-linked list of siblings under each parent,
  * enabling O(1) insertion and O(1) unlink without a separate index.
  *
  * @param parent the parent node, if any
  */
final class OwnerNode(val parent: Option[OwnerNode]):

  // Doubly-linked sibling list.  Option is used rather than `OwnerNode | Null`
  // to keep the type consistent with `parent` and to avoid null in internal code.
  // The trade-off is a small allocation per `Some` wrapper on each pointer write,
  // which is acceptable for typical component trees.
  private var _firstChild:  Option[OwnerNode] = None
  private var _lastChild:   Option[OwnerNode] = None
  private var _nextSibling: Option[OwnerNode] = None
  private var _prevSibling: Option[OwnerNode] = None

  // js.Set preserves insertion order and is the standard Svelte 5 approach
  private val _cleanups: js.Set[() => Unit] = new js.Set()

  private var _destroyed = false

  def isDestroyed: Boolean = _destroyed

  /** Appends [child] to the end of this node's child list.
    * No-op if this node has already been destroyed.
    */
  private[runtime] def addChild(child: OwnerNode): Unit =
    if _destroyed then return
    _lastChild match
      case None =>
        _firstChild = Some(child)
        _lastChild  = Some(child)
      case Some(last) =>
        last._nextSibling  = Some(child)
        child._prevSibling = Some(last)
        _lastChild         = Some(child)

  /** Removes this node from its parent's child list.
    *
    * The Svelte 5 guard (`if p._firstChild.isDefined || p._lastChild.isDefined`) prevents
    * children from writing back into a parent that has already cleared its child list
    * during its own `destroy()` call, avoiding stale pointer overwrites.
    */
  private def unlinkFromParent(): Unit =
    parent.foreach { p =>
      // Guard: skip if parent has already cleared its child list (during parent.destroy())
      if p._firstChild.isDefined || p._lastChild.isDefined then
        val prev = _prevSibling
        val next = _nextSibling
        prev match
          case Some(prevNode) => prevNode._nextSibling = next
          case None           => p._firstChild = next
        next match
          case Some(nextNode) => nextNode._prevSibling = prev
          case None           => p._lastChild = prev
        _prevSibling = None
        _nextSibling = None
    }

  /** Registers a cleanup function to run when this node is destroyed.
    *
    * If the node is already destroyed, [f] is invoked immediately (best-effort)
    * so that callers never silently drop resources.
    */
  private[runtime] def addCleanup(f: () => Unit): Unit =
    if _destroyed then
      try f()
      catch case _: Throwable => ()
    else _cleanups.add(f)

  /** Destroys this node and all its descendants, then runs its own cleanups.
    *
    * Destruction order:
    *   1. Mark as destroyed and snapshot children / cleanups BEFORE clearing
    *      (Svelte 5 snapshot-before-clear pattern — prevents re-entrancy issues).
    *   2. Unlink from parent so the parent's list is consistent.
    *   3. Destroy children in FIFO (insertion) order — matches Svelte 5.
    *   4. Run own cleanups in LIFO (reverse-registration) order — matches Svelte 5.
    *
    * All child.destroy() and cleanup() calls are wrapped in try-catch to ensure
    * that one failing cleanup does not prevent the others from running.
    */
  def destroy(): Unit =
    if _destroyed then return
    _destroyed = true

    // Snapshot children and cleanups before clearing (Svelte 5 pattern)
    var child = _firstChild
    _firstChild = None
    _lastChild  = None

    val cleanupsSnap = js.Array.from(_cleanups)
    _cleanups.clear()

    // Unlink from parent's sibling list
    unlinkFromParent()

    // Destroy children FIFO
    while child.isDefined do
      val node = child.get
      val next = node._nextSibling
      try node.destroy()
      catch case _: Throwable => ()
      child = next

    // Run own cleanups LIFO
    var i = cleanupsSnap.length - 1
    while i >= 0 do
      try cleanupsSnap(i)()
      catch case _: Throwable => ()
      i -= 1

/** Global owner context managing the current reactive owner node.
  *
  * Provides a thread-local-equivalent (Scala.js is single-threaded) stack of owner
  * nodes so that any `Owner.register` call during reactive computation is automatically
  * attributed to the correct node without explicit passing.
  *
  * Also tracks reactive update depth to detect infinite reactive loops (equivalent to
  * Svelte 5's `infinite_loop_guard`).
  */
object Owner:

  private var _current: Option[OwnerNode] = None

  /** Depth counter for reactive update loop detection. */
  private[runtime] var _reactiveDepth: Int = 0
  private val MaxReactiveDepth = 1000

  /** Returns the current owner node, if any. */
  def current: Option[OwnerNode] = _current

  /** Increments the reactive depth counter and throws [[ReactiveLoopException]] if
    * the depth exceeds [[MaxReactiveDepth]].
    *
    * Must always be paired with [[exitReactive]] in a try/finally block.
    */
  private[runtime] def enterReactive(): Unit =
    _reactiveDepth += 1
    if _reactiveDepth > MaxReactiveDepth then
      _reactiveDepth = 0
      throw new ReactiveLoopException(
        s"Reactive update depth exceeded $MaxReactiveDepth. " +
          "This is likely caused by a cyclic reactive dependency."
      )

  /** Decrements the reactive depth counter. */
  private[runtime] def exitReactive(): Unit =
    if _reactiveDepth > 0 then _reactiveDepth -= 1

  /** Creates a new [[OwnerNode]], runs [body] with that node as current owner,
    * and returns both the result and the node.
    *
    * If [body] throws, the node is destroyed before re-throwing so that any
    * cleanups registered during the partial execution are run.
    *
    * @return `(result, ownerNode)` — caller is responsible for calling `node.destroy()`
    *         when the owned scope should be released.
    */
  def withNew[A](body: => A): (A, OwnerNode) =
    val prev = _current
    val node = new OwnerNode(prev)
    prev.foreach(_.addChild(node))
    _current = Some(node)
    try
      val result = body
      (result, node)
    catch
      case e: Throwable =>
        node.destroy()
        throw e
    finally _current = prev

  /** Runs [body] with [node] as the current owner.
    *
    * Returns `None` if [node] has already been destroyed — this prevents
    * registering resources into a dead scope.
    */
  def withOwner[A](node: OwnerNode)(body: => A): Option[A] =
    if node.isDestroyed then None
    else
      val prev = _current
      _current = Some(node)
      try Some(body)
      finally _current = prev

  /** Registers [f] as a cleanup on the current owner node.
    * No-op if there is no active owner (e.g. top-level or test context).
    */
  def register(f: () => Unit): Unit = _current.foreach(_.addCleanup(f))

/** Thrown when the reactive update depth exceeds the safety threshold.
  *
  * Equivalent to Svelte 5's `infinite_loop` error (`infinite_loop_guard` in effects.js).
  * After throwing, the depth counter is reset to zero so the runtime can recover.
  */
final class ReactiveLoopException(message: String) extends RuntimeException(message)
