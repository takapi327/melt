/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.collection.mutable

import melt.runtime.Owner

/** Client-side registry of live [[Query]] instances, so [[Invalidate]] can refresh
  * them by tag without the caller holding a reference.
  *
  * A query registers on creation ([[meltkit.ServerFnClient.build]]) and is removed
  * when its owning component unmounts, via [[melt.runtime.Owner.register]] cleanup.
  * A query built with no active owner (unusual — outside a component render) is not
  * tracked: it has no unmount to clean it up, so tracking it would leak, and it
  * cannot be tag-invalidated (it is effectively app-lifetime, like a top-level
  * `State`).
  */
private[meltkit] object QueryRegistry:

  private val live = mutable.Set.empty[Query[?]]

  /** Registers `q` for the lifetime of the current component (no-op without one). */
  def register(q: Query[?]): Unit =
    if Owner.current.isDefined then
      live += q
      Owner.register(() => live -= q)

  /** Refreshes every live query carrying `tag`. */
  def invalidateTag(tag: String): Unit =
    live.foreach(q => if q.tags.contains(tag) then q.refresh())

  /** Refreshes every live query. */
  def invalidateAll(): Unit = live.foreach(_.refresh())
