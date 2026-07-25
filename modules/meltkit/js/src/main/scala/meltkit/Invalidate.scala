/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** Refreshes live queries by tag, without holding a reference to them — the
  * client counterpart of the JVM no-op.
  *
  * Queries opt in with [[QueryFn.tagged]]; a mutation elsewhere on the page can then
  * refresh them by tag. This complements the reference-based paths
  * ([[FormInvalidation.invalidates]] and single-flight `Mutation.updates`) for the
  * case where the mutator does not hold the query instance.
  *
  * {{{
  * val posts = ServerFn.query[Unit, List[Post]]("posts.list").tagged("posts")
  * // …after creating a post, from a component that never sees `posts`:
  * Invalidate("posts")   // refreshes every live query tagged "posts"
  * Invalidate.all()      // refreshes every live query on the page
  * }}}
  */
object Invalidate:

  /** Refreshes every live query carrying `tag`. */
  def apply(tag: String): Unit = QueryRegistry.invalidateTag(tag)

  /** Refreshes every live query on the current page. */
  def all(): Unit = QueryRegistry.invalidateAll()
