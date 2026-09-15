/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import scala.scalajs.js

/** Batches reactive updates so that subscribers are notified only once
  * after all mutations in a `batch { }` block complete.
  *
  * Uses a set of pending "dirty" sources. Each source is flushed once
  * at the end of the outermost batch, reading the latest value.
  */
object Batch:
  private var depth    = 0
  private var flushing = false

  /** Set of flush functions keyed by identity to avoid duplicates.
    * Each entry is a `() => Unit` that reads the current value and notifies subscribers.
    */
  // js.Array with an explicit containment check rather than mutable.LinkedHashSet:
  // linking a Scala Set drags the immutable collection hierarchy into the bundle
  // (~41 KB gzip). See memo/design-bundle-size.md §2.6. Insertion order and
  // "enqueue at most once" are both preserved.
  private val pending: js.Array[() => Unit] = js.Array()

  def isBatching: Boolean = depth > 0

  /** True while the pending queue is being drained.
    * Two-dep triggers check this to avoid re-scheduling dedup during flush.
    */
  private[runtime] def isFlushing: Boolean = flushing

  /** Registers a flush function. If the same function is already pending,
    * it is not added again (dedup by reference identity).
    */
  def enqueue(f: () => Unit): Unit = if pending.indexOf(f) < 0 then pending.push(f)

  private def flush(): Unit =
    flushing = true
    try
      // Iterate and clear — new enqueues during flush are processed in the same pass
      while pending.length > 0 do
        val fns = pending.jsSlice()
        pending.length = 0
        fns.foreach(_())
    finally flushing = false

  def apply(f: => Unit): Unit =
    depth += 1
    try f
    finally
      depth -= 1
      if depth == 0 then flush()

def batch(f: => Unit): Unit = Batch(f)
