/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.ssg

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.Duration

import meltkit.{ MeltKit, SyncRunner }

/** Convenience base trait for `Future`-based asynchronous static site generation.
  *
  * Extends [[SsgApp]][[[scala.concurrent.Future]]] and provides both a pre-wired
  * [[meltkit.SyncRunner]] and a default [[meltkit.MeltKit]] instance, so users only
  * need to declare [[SsgApp.paths]], [[SsgApp.template]], and [[SsgApp.manifest]],
  * then call `kit.get(...)` to register routes — without ever referencing `F` or
  * `Future` in the kit definition.
  *
  * Useful when [[SsgApp.paths]] or route handlers perform asynchronous work
  * (e.g. fetching slugs from a database or CMS) without introducing a full
  * effect system such as cats-effect.
  *
  * {{{
  * object MySsg extends FutureSsgApp:
  *
  *   kit.get("") { ctx => ctx.render(HomePage()) }
  *
  *   override def paths    = db.allSlugs().map(ss => ss.map(s => s"/posts/$s"))
  *   override val template = Template.fromResource("index.html")
  *   override val manifest = ViteManifest.empty
  * }}}
  *
  * Override [[awaitTimeout]] to set a maximum wait time per `Future`:
  *
  * {{{
  * import scala.concurrent.duration.*
  * override def awaitTimeout: Duration = 30.seconds
  * }}}
  */
trait FutureSsgApp extends SsgApp[Future]:
  override val kit: MeltKit[Future] = new MeltKit[Future]()

  /** Maximum time to block when waiting for a `Future` to complete.
    *
    * Defaults to [[scala.concurrent.duration.Duration.Inf]] (wait forever).
    * Override with a [[scala.concurrent.duration.FiniteDuration]] to add a timeout.
    */
  def awaitTimeout: Duration = Duration.Inf

  override given syncRunner: SyncRunner[Future] with
    override def runSync[A](fa: Future[A]): A = Await.result(fa, awaitTimeout)
