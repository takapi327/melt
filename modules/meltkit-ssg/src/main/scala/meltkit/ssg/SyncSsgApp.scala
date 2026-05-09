/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.ssg

import meltkit.{ MeltKit, SyncRunner }

/** Type alias for the identity effect — `Id[A]` is simply `A`.
  *
  * Kept package-private to `meltkit.ssg`; users extending [[SyncSsgApp]]
  * never need to reference this type directly.
  */
type Id = [A] =>> A

/** Convenience base trait for synchronous static site generation.
  *
  * Extends [[SsgApp]][[[Id]]] and provides both a pre-wired [[meltkit.SyncRunner]]
  * and a default [[meltkit.MeltKit]] instance, so users only need to declare
  * [[SsgApp.paths]], [[SsgApp.template]], and [[SsgApp.manifest]], then call
  * `kit.get(...)` to register routes — without ever referencing `F` or `Id`.
  *
  * {{{
  * object MySsg extends SyncSsgApp:
  *
  *   kit.get("") { ctx => ctx.render(HomePage()) }
  *   kit.get("about") { ctx => ctx.render(AboutPage()) }
  *
  *   override val paths    = List("/", "/about")
  *   override val template = Template.fromResource("index.html")
  *   override val manifest = ViteManifest.empty
  * }}}
  *
  * If you need to share the `kit` instance with an SSR setup, override it:
  *
  * {{{
  * object MySsg extends SyncSsgApp:
  *   override val kit = sharedMeltKit
  * }}}
  */
trait SyncSsgApp extends SsgApp[Id]:
  override val kit: MeltKit[Id] = new MeltKit[Id]()

  override given syncRunner: SyncRunner[Id] with
    override def runSync[A](fa: A): A = fa
