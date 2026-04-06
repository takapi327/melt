/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import scala.collection.mutable

/** Batches reactive updates so that subscribers are notified only once
  * after all mutations in a `batch { }` block complete.
  */
object Batch:
  private var depth                                   = 0
  private val pending: mutable.ListBuffer[() => Unit] = mutable.ListBuffer.empty

  def isBatching: Boolean = depth > 0

  def enqueue(f: () => Unit): Unit = pending += f

  private def flush(): Unit =
    val fns = pending.toList
    pending.clear()
    fns.foreach(_())

  /** Defers all reactive subscriber notifications until the block completes. */
  def apply(f: => Unit): Unit =
    depth += 1
    try f
    finally
      depth -= 1
      if depth == 0 then flush()

/** Alias for `Batch.apply`. */
def batch(f: => Unit): Unit = Batch(f)
