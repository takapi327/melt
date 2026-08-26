/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package components

import meltkit.ServerFn

/** Shared, type-safe server-function contracts.
  *
  * This one object is compiled for BOTH the JVM server — which implements each
  * function with `app.serve(...)` — and the JS client — which calls them
  * (`Api.list.seeded(...)`, `Api.like.dispatch(...)`). Because the contract is a
  * single shared definition, the input/output types cannot drift between the two
  * sides.
  */
object Api:

  /** Read: all posts. Rendered reactively; seeded from the page loader on SSR. */
  val list = ServerFn.query[Unit, List[Post]]("posts.list")

  /** Read: the post count. Used to demonstrate a nested `<melt:await>`. */
  val count = ServerFn.query[Unit, Int]("posts.count")

  /** Mutate: like a post, returning the updated post. Paired with a single-flight
    * refresh of [[list]] so one round-trip both likes and refreshes the list. */
  val like = ServerFn.command[Int, Post]("posts.like")

  /** Mutate: delete a post. */
  val remove = ServerFn.command[Int, Unit]("posts.remove")

  // ── Streaming SSR demo ─────────────────────────────────────────────────────
  // Two independent reads with different (artificial) server-side delays, used by
  // `StreamingPage` to show `ctx.renderStream`: the shell flushes immediately and
  // each boundary streams in as it settles — the faster `streamStats` chunk arrives
  // before the slower `streamPosts` one (out-of-order).

  /** Read: all posts, deliberately slow. Feeds a top-level `<melt:await>`. */
  val streamPosts = ServerFn.query[Unit, List[Post]]("stream.posts")

  /** Read: the post count, less slow — settles (and streams) before `streamPosts`. */
  val streamStats = ServerFn.query[Unit, Int]("stream.stats")
