/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import org.scalajs.dom

/** Browser-specific [[MeltKit]] router with `F` fixed to [[Id]] and `C` fixed to [[org.scalajs.dom.Element]].
  *
  * {{{
  * val app = MeltKit()
  * app.get("todos") { ctx => ctx.render(TodoPage()) }
  * BrowserAdapter.mountWithShell(app, rootEl, Layout())
  * }}}
  */
class MeltKit extends MeltKitPlatform[Id, dom.Element]:

  // Prefetch thunks by path (static, exact match). Warmed by the link hover/viewport
  // hook in BrowserAdapter when a link to `path` is about to be navigated.
  private val _prefetch = scala.collection.mutable.ListBuffer.empty[(List[PathSegment], () => Unit)]

  /** Registers work to run just before a navigation to `path` — typically warming
    * that route's query data so the page renders without a loading flash:
    *
    * {{{
    * app.get("posts") { ctx => ctx.render(PostsPage()) }
    * app.prefetch("posts") { () => Api.list.prefetch() }
    * // <a href="/posts" data-melt-preload="hover">Posts</a>
    * }}}
    *
    * The hook fires when a link carrying `data-melt-preload` (or nested under one)
    * is hovered/focused (`"hover"`, the default) or scrolls into view (`"viewport"`),
    * matched to `path` by exact static segments. Each path prefetches once per session.
    */
  def prefetch(path: String)(thunk: () => Unit): Unit =
    val segs = path.split('/').filter(_.nonEmpty).map(PathSegment.Static(_)).toList
    _prefetch += (segs -> thunk)

  /** The prefetch thunks registered for exactly `path` (adapter use only). */
  private[meltkit] def prefetchThunksFor(path: String): List[() => Unit] =
    val segs = path.split('/').filter(_.nonEmpty).toList
    _prefetch.toList.collect {
      case (registered, thunk) if registered.length == segs.length && registered.zip(segs).forall {
          case (PathSegment.Static(v), s) => v == s
          case _                          => true
        } =>
        thunk
    }

object MeltKit:
  def apply(): MeltKit = new MeltKit()
