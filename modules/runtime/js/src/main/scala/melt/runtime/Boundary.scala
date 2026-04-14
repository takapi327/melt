/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import org.scalajs.dom

/** Error boundary component that catches exceptions during child rendering
  * and displays a fallback UI.
  *
  * Returns a [[dom.DocumentFragment]] containing two comment-node anchors
  * (`<!-- melt-b -->` / `<!-- /melt-b -->`). No wrapper element is created,
  * matching Svelte 5 behaviour. When `appendChild`'d to a parent element the
  * fragment's children (anchors + content) move directly into the parent.
  */
object Boundary:

  case class Props(
    children: () => dom.Element,
    pending:  Option[() => dom.Element]              = None,
    fallback: (Throwable, () => Unit) => dom.Element = (_, _) => dom.document.createElement("div"),
    onError:  Throwable => Unit                      = _ => ()
  )

  def create(props: Props): dom.DocumentFragment =
    val startAnchor = dom.document.createComment("melt-b")
    val endAnchor   = dom.document.createComment("/melt-b")
    var generation  = 0
    var childCleanups: List[() => Unit] = Nil

    /** Remove all nodes between startAnchor and endAnchor, and run cleanups. */
    def clearBetweenAnchors(): Unit =
      Cleanup.runAll(childCleanups)
      childCleanups = Nil
      var node = startAnchor.nextSibling
      while node != null && !(node eq endAnchor) do
        val next = node.nextSibling
        startAnchor.parentNode.removeChild(node)
        node = next

    /** Insert `node` immediately before endAnchor (works in fragment and in DOM). */
    def insertBefore(node: dom.Node): Unit =
      endAnchor.parentNode.insertBefore(node, endAnchor)

    def render(): Unit =
      generation += 1
      val myGen = generation
      clearBetweenAnchors()

      val ctx = new AsyncBoundaryCtx

      Cleanup.pushScope()
      try
        val childEl = BoundaryScope.withContext(ctx) { props.children() }
        childCleanups = Cleanup.popScope()

        if ctx.hasPending && props.pending.isDefined then
          // Show pending UI while futures resolve.
          // childEl (the Await placeholder) is inserted into the DOM immediately so that
          // Await's onComplete callback can always call replaceWith/replaceChild on it
          // regardless of callback ordering.  The pending UI is removed once all futures
          // have settled.
          val pendingEl = props.pending.get()
          insertBefore(pendingEl)
          insertBefore(childEl)
          ctx.onAllResolved = () =>
            if generation == myGen then
              Option(pendingEl.parentNode).foreach(_.removeChild(pendingEl))
        else
          // No pending UI (or no pending futures): show children immediately.
          insertBefore(childEl)

      catch
        // JavaScriptException must come before Exception (it extends Exception).
        case e: scalajs.js.JavaScriptException =>
          childCleanups = Cleanup.popScope()
          val wrapped = new RuntimeException(e.getMessage)
          props.onError(wrapped)
          val fb = props.fallback(wrapped, () => render())
          insertBefore(fb)
        case e: Exception =>
          childCleanups = Cleanup.popScope()
          props.onError(e)
          val fb = props.fallback(e, () => render())
          insertBefore(fb)

    val frag = dom.document.createDocumentFragment()
    frag.appendChild(startAnchor)
    frag.appendChild(endAnchor)
    render()
    frag
