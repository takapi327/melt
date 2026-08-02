/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.collection.mutable

/** Per-history-entry scroll positions, keyed by a monotonic entry id stored in
  * `history.state`. Kept free of any DOM dependency so the bookkeeping is
  * unit-testable; [[Router]] wires it to `window.scroll*` and the History API.
  */
object ScrollPositions:

  private val positions = mutable.LongMap.empty[(Double, Double)]

  /** Records the scroll offset for history entry `key`. */
  def save(key: Long, x: Double, y: Double): Unit = positions(key) = (x, y)

  /** The saved scroll offset for `key`, or `(0, 0)` if none — a fresh entry
    * starts at the top, matching browser navigation semantics. */
  def restore(key: Long): (Double, Double) = positions.getOrElse(key, (0.0, 0.0))

  /** True if an offset has been recorded for `key`. */
  def has(key: Long): Boolean = positions.contains(key)

  private[meltkit] def clear(): Unit = positions.clear()
