/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** A memoized reactive value.
  *
  * A `Memo[A]` is a [[Signal]] that only propagates changes when the computed
  * value is actually different (checked via `!=`). This matches the behaviour
  * of Svelte 5's `$derived` and suppresses redundant updates in the reactive
  * graph.
  *
  * Phase A currently has no dedicated implementation class — `memo(src)(f)`
  * simply returns a plain [[Signal]] with equality-based propagation. The
  * `Memo` trait exists as a future extension point and as an API contract
  * guarantee: users who want to distinguish "memoized" from "plain" signals
  * can rely on this type. See `docs/meltc-ssr-design.md` §5.1.
  */
trait Memo[A] extends Signal[A]
