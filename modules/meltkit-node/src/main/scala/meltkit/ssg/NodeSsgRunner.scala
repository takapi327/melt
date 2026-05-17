/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.ssg

import meltkit.SyncRunner

/** Provides a [[SyncRunner]] given instance for use with [[NodeSsgGenerator]].
  *
  * Only the identity effect `[A] =>> A` is supported on Node.js/Scala.js because
  * Scala.js runs on a single-threaded event loop and cannot block on a `Future`
  * (no `Await.result` equivalent).
  *
  * Import `NodeSsgRunner.given` to bring the instance into scope:
  *
  * {{{
  * import meltkit.ssg.NodeSsgRunner.given
  *
  * NodeSsgGenerator.run(MyApp.app, config)
  * }}}
  */
object NodeSsgRunner:

  /** [[SyncRunner]] for the identity effect `[A] =>> A` (synchronous, no wrapping). */
  given SyncRunner[[A] =>> A] with
    def runSync[A](fa: A): A = fa
