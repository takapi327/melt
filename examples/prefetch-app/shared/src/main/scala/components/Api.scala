/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package components

import meltkit.ServerFn

/** Shared server-function contract — one definition compiled for both the JVM
  * server (which implements it with `app.serve`) and the JS client (which calls
  * and prefetches it). */
object Api:

  /** Read: the item list. `Api.items()` fetches it reactively; `Api.items.prefetch()`
    * warms it ahead of a navigation so the page renders with no loading flash. */
  val items = ServerFn.query[Unit, List[Item]]("items.list")
