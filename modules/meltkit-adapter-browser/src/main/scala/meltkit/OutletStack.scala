/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.collection.mutable.ListBuffer

import org.scalajs.dom

import melt.runtime.{ Lifecycle, Mount }

/** Persistent nested-outlet manager for SPA navigation (Phase 2b).
  *
  * On the first render the whole layout+page tree is composed
  * ([[MeltKitPlatform.wrapLayouts]]) and mounted (or, when hydrating, claimed in
  * place); then the DOM is walked to capture the '''persistent frame stack''' — the
  * chain of layouts, from the root, that each expose a single `[data-melt-outlet]`
  * element wrapping their `{children}`.
  *
  * On every subsequent navigation the new path's layout prefixes are diffed against
  * the captured frames: shared outer layouts stay mounted (their state, effects and
  * subscriptions untouched), and only the content below the deepest shared outlet is
  * destroyed ([[Lifecycle]]) and rebuilt. This mirrors SvelteKit / Next.js App
  * Router, where navigating within a section swaps only the inner page.
  *
  * A layout with no `[data-melt-outlet]` ends the persistent chain: it and
  * everything under it form one rigid blob that is rebuilt whole on navigation
  * (the pre-2b behaviour). Persistence is therefore opt-in per layout, and apps
  * with no marked outlet keep working unchanged — just without layout retention.
  *
  * The outlet element must be the one that wraps `{children}` in the layout
  * template, e.g. `<section data-melt-outlet>{children}</section>`; the manager
  * mounts inner content directly into it.
  *
  * @param app        the router, for `layoutsWithPrefixFor` / `wrapLayouts`
  * @param rootOutlet the base outlet — `rootEl` in [[BrowserAdapter.mount]] /
  *                   [[BrowserAdapter.hydrate]], or the shell's `[data-melt-outlet]`
  *                   in [[BrowserAdapter.mountWithShell]]
  */
private[meltkit] final class OutletStack(
  app:        MeltKitPlatform[?, dom.Element],
  rootOutlet: dom.Element
):

  /** One mounted, retained layout: its prefix (for diffing), its root element and
    * the `[data-melt-outlet]` its children are mounted into. */
  private final case class Frame(prefix: List[PathSegment], layoutEl: dom.Element, outlet: dom.Element)

  private var frames:      List[Frame] = Nil
  private var initialized: Boolean     = false

  /** Renders `path`'s page composed inside its layouts, retaining shared layouts
    * across navigations. The first call performs the full mount (or hydration
    * claim); later calls swap only the diverging subtree. */
  def render(path: String, page: () => dom.Element, hydrating: Boolean): Unit =
    if !initialized then
      initialized = true
      if hydrating then
        // The adapter has an active Hydrating cursor: wrapLayouts claims the SSR
        // DOM in place (no mount). Then capture the frame stack from that DOM.
        val _ = app.wrapLayouts(path, page)
      else
        val tree = app.wrapLayouts(path, page)
        rootOutlet.innerHTML = ""
        Mount(rootOutlet, tree)
      frames = capture(rootOutlet, app.layoutsWithPrefixFor(path).map(_._1))
    else navigate(path, page)

  /** Incremental navigation: keep the shared outer layouts, rebuild the rest. */
  private def navigate(path: String, page: () => dom.Element): Unit =
    val newLayouts = app.layoutsWithPrefixFor(path)
    // Shared persistent frames = the leading run whose prefixes still match. `frames`
    // only holds outlet-bearing layouts, so this never keeps a non-persistent one.
    val sharedCount = newLayouts
      .zip(frames)
      .takeWhile { case ((prefix, _), frame) => prefix == frame.prefix }
      .length

    val swapOutlet = if sharedCount > 0 then frames(sharedCount - 1).outlet else rootOutlet

    // Tear down the old subtree below the swap point (runs owner cleanups), keeping
    // the swap outlet's own owner — destroy each child subtree, never the outlet.
    destroyContentOf(swapOutlet)
    swapOutlet.innerHTML = ""

    val suffixLayouts = newLayouts.drop(sharedCount)
    val blob          = foldLayouts(suffixLayouts, page)
    Mount(swapOutlet, blob)

    frames = frames.take(sharedCount) ::: capture(swapOutlet, suffixLayouts.map(_._1))

  /** Folds `layouts` (outermost first) around `page`, producing the composed root
    * — the same composition as [[MeltKitPlatform.wrapLayouts]] over a sublist. */
  private def foldLayouts(
    layouts: List[(List[PathSegment], (() => dom.Element) => dom.Element)],
    page:    () => dom.Element
  ): dom.Element =
    layouts.foldRight(page)((entry, inner) => () => entry._2(inner))()

  /** Walks the DOM from `startOutlet`, matching each expected layout `prefix` to the
    * next element child, recording a [[Frame]] while the layout exposes an outlet.
    * Stops at the first layout without one — below that is a rigid blob. */
  private def capture(startOutlet: dom.Element, prefixes: List[List[PathSegment]]): List[Frame] =
    val fs     = ListBuffer.empty[Frame]
    var parent = startOutlet
    var ok     = true
    prefixes.foreach { prefix =>
      if ok then
        val layoutEl = parent.firstElementChild
        if layoutEl == null || !hasOutlet(layoutEl) then ok = false
        else
          val outlet = findOutlet(layoutEl)
          fs += Frame(prefix, layoutEl, outlet)
          parent = outlet
    }
    fs.toList

  /** Destroys the owner subtree of every element child of `outlet`, leaving
    * `outlet`'s own owner intact (so a retained layout that *is* its own outlet
    * survives). */
  private def destroyContentOf(outlet: dom.Element): Unit =
    var child = outlet.firstElementChild
    while child != null do
      val next = child.nextElementSibling
      Lifecycle.destroyTree(child)
      child = next

  private def hasOutlet(el: dom.Element): Boolean =
    el.matches("[data-melt-outlet]") || el.querySelector("[data-melt-outlet]") != null

  private def findOutlet(el: dom.Element): dom.Element =
    if el.matches("[data-melt-outlet]") then el
    else Option(el.querySelector("[data-melt-outlet]")).getOrElse(el)
