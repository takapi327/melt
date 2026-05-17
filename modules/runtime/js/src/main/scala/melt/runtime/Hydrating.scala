/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import scala.collection.mutable

import org.scalajs.dom

/** Claim-based hydration coordinator.
  *
  * During initial hydration the generated `_meltHydrateEntry` function
  * calls [[withCursor]] with a [[HydrationCursor]] that walks the
  * SSR-rendered DOM.  Inside that scope every element/text-node creation
  * call **claims** (reuses) the existing SSR node instead of creating a
  * fresh one, and every `appendChild` call is skipped because the nodes
  * are already correctly positioned.
  *
  * Dynamic sections (list rendering, conditional rendering) are wrapped
  * with `<!--[melt:dyn-->` / `<!--]melt:dyn-->` SSR comment markers;
  * [[dynAnchor]] removes the stale SSR content and hands the closing
  * comment back to `Bind.list` / `Bind.show` / `Bind.each` as the
  * insertion anchor.
  *
  * Code inside render lambdas (the functions passed to `Bind.list` etc.)
  * runs under a sentinel `HydrationCursor(null)` pushed by the generated
  * code so that [[isActive]] returns `false` there, reverting to normal
  * `createElement` / `appendChild` behaviour inside those lambdas.
  *
  * ==Life-cycle==
  *  1. `_meltHydrateEntry` calls `withCursor(new HydrationCursor(startSibling))`.
  *  2. `apply(_props)` runs: elements are claimed, text nodes claimed, dyn
  *     sections consumed.
  *  3. `withCursor` returns; cursor stack is empty.
  *  4. `flush()` runs `onMount` callbacks.
  */
object Hydrating:

  private val stack = mutable.ArrayStack.empty[HydrationCursor]

  /** True when the cursor stack is non-empty **and** the top cursor has a
    * non-null current node.
    *
    * Sentinel cursors (`HydrationCursor(null)`) pushed around render
    * lambdas have `current == null`, which makes [[isActive]] return
    * `false` — restoring normal SPA creation/append behaviour inside
    * those lambdas.
    */
  def isActive: Boolean = stack.nonEmpty && (stack.top.current != null)

  // ── Node creation / claiming ──────────────────────────────────────────────

  /** Claims the next matching element from the cursor, or creates a fresh one. */
  def element(tag: String): dom.Element =
    if stack.nonEmpty then
      stack.top.nextElement(tag) match
        case null => dom.document.createElement(tag)
        case el   => el
    else dom.document.createElement(tag)

  /** Claims the next matching namespaced element, or creates a fresh one. */
  def elementNS(ns: String, tag: String): dom.Element =
    if stack.nonEmpty then
      stack.top.nextElement(tag) match
        case null => dom.document.createElementNS(ns, tag)
        case el   => el
    else dom.document.createElementNS(ns, tag)

  /** Claims the next text node from the cursor, or creates a fresh one.
    * Does NOT append; the caller's parent handles insertion conditionally.
    */
  def textNode(content: String): dom.Text =
    if stack.nonEmpty then
      stack.top.nextText() match
        case null => dom.document.createTextNode(content)
        case t    => t
    else dom.document.createTextNode(content)

  // ── Reactive text binding ─────────────────────────────────────────────────

  /** Claims/creates a text node for a `Var` and subscribes reactively.
    * When claiming, skips `parent.appendChild` (node is already in DOM).
    * When not active, delegates to [[Bind.text]].
    */
  def text(v: Var[?], parent: dom.Node): dom.Text =
    if isActive then
      val node = stack.top.nextText() match
        case null => dom.document.createTextNode(v.value.toString)
        case t    => t
      val cancel = v.subscribe(a => node.textContent = a.toString)
      Cleanup.register(cancel)
      node
    else Bind.text(v, parent)

  /** Claims/creates a text node for a `Signal` and subscribes reactively. */
  def text(signal: Signal[?], parent: dom.Node): dom.Text =
    if isActive then
      val node = stack.top.nextText() match
        case null => dom.document.createTextNode(signal.value.toString)
        case t    => t
      val cancel = signal.subscribe(a => node.textContent = a.toString)
      Cleanup.register(cancel)
      node
    else Bind.text(signal, parent)

  /** Claims/creates a static-string text node.  Skips append when claiming. */
  def text(value: String, parent: dom.Node): dom.Text =
    if isActive then
      stack.top.nextText() match
        case null => dom.document.createTextNode(value)
        case t    => t
    else Bind.text(value, parent)

  /** Claims/creates a static-Int text node.  Skips append when claiming. */
  def text(value: Int, parent: dom.Node): dom.Text =
    if isActive then
      stack.top.nextText() match
        case null => dom.document.createTextNode(value.toString)
        case t    => t
    else Bind.text(value, parent)

  // ── Dynamic section anchor ────────────────────────────────────────────────

  /** Locates and consumes the `<!--[melt:dyn-->` / `<!--]melt:dyn-->`
    * SSR marker pair inside `parent`, returning the closing comment as
    * the insertion anchor for `Bind.list` / `Bind.show` / `Bind.each`.
    *
    * When not active, creates a new comment and appends it to `parent`
    * (normal SPA behaviour).
    */
  def dynAnchor(parent: dom.Element): dom.Comment =
    if isActive then
      stack.top.consumeDyn(parent) match
        case null =>
          val c = dom.document.createComment("melt")
          parent.appendChild(c)
          c
        case anchor => anchor
    else
      val c = dom.document.createComment("melt")
      parent.appendChild(c)
      c

  // ── Cursor scope management ───────────────────────────────────────────────

  /** Pushes a child cursor for `parent.firstChild` while `f` runs,
    * then pops it.  Used to claim children of a claimed element.
    */
  def withChildren[A](parent: dom.Element)(f: => A): A =
    val childCursor = new HydrationCursor(parent.firstChild)
    stack.push(childCursor)
    try f
    finally stack.pop()

  /** Pushes `cursor` onto the stack while `f` runs, then pops it.
    * Used by the hydration entry to activate claim mode for the
    * component tree, and by generated code to push sentinel
    * `HydrationCursor(null)` around render lambdas.
    */
  def withCursor[A](cursor: HydrationCursor)(f: => A): A =
    stack.push(cursor)
    try f
    finally stack.pop()

  // ── Post-hydration flush ──────────────────────────────────────────────────

  /** Runs all pending `onMount` callbacks.  Called after the hydration
    * entry's `withCursor` block exits, mirroring [[Mount.apply]]'s
    * `OnMount.flush()` call.
    */
  def flush(): Unit = OnMount.flush()
