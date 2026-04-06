/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import scala.collection.mutable

/** Batches reactive updates so that subscribers are notified only once
  * after all mutations in a `batch { }` block complete.
  *
  * Uses a set of pending "dirty" sources. Each source is flushed once
  * at the end of the outermost batch, reading the latest value.
  */
object Batch:
  private var depth = 0

  /** Set of flush functions keyed by identity to avoid duplicates.
    * Each entry is a `() => Unit` that reads the current value and notifies subscribers.
    */
  private val pending: mutable.LinkedHashSet[() => Unit] = mutable.LinkedHashSet.empty

  def isBatching: Boolean = depth > 0

  /** Registers a flush function. If the same function is already pending,
    * it is not added again (dedup by reference identity).
    */
  def enqueue(f: () => Unit): Unit = pending += f

  private def flush(): Unit =
    // Iterate and clear — new enqueues during flush are processed in the same pass
    while pending.nonEmpty do
      val fns = pending.toList
      pending.clear()
      fns.foreach(_())

  def apply(f: => Unit): Unit =
    depth += 1
    try f
    finally
      depth -= 1
      if depth == 0 then flush()

def batch(f: => Unit): Unit = Batch(f)
