/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import scala.concurrent.{ Future, Promise }

import scalajs.js

/** Schedules a callback to run after the current microtask completes.
  * Uses `js.Promise.resolve` as per Appendix B.3.
  */
def tick(f: => Unit): Unit =
  js.Promise.resolve[Unit](()).`then`[Unit](_ => f: Unit)

/** Returns a Future that completes after the current microtask. */
def tickAsync(): Future[Unit] =
  val p = Promise[Unit]()
  js.Promise.resolve[Unit](()).`then`[Unit] { _ => p.success(()); (): Unit }
  p.future
