/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.ssg

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.Duration

import meltkit.{ SyncRunner, Template, ViteManifest }

/** Utilities for running static site generation from a `@main` entry point.
  *
  * Users define a `@main def generate()` in their project and call
  * [[SsgRunner.configFromProps]] to build [[SsgConfig]] from the system
  * properties injected by `sbt-meltc`'s `meltcStaticGenerate` task:
  *
  * {{{
  * import meltkit.ssg.{ SsgGenerator, SsgRunner }
  * import meltkit.ssg.SsgRunner.given   // brings SyncRunner[Future] into scope
  *
  * @main def generate(): Unit =
  *   val config = SsgRunner.configFromProps(
  *     template = Template.fromFile(Paths.get("src/main/resources/index.html"))
  *   )
  *   SsgGenerator.run(MyApp.app, config)
  * }}}
  *
  * == System properties set by `meltcStaticGenerate` ==
  *
  *   - `meltcSsgOutputDir`  — output directory path (required)
  *   - `meltcSsgAssetsDir`  — Vite assets directory to copy (optional)
  *   - `meltcSsgPublicDir`  — public directory to copy verbatim to output root (optional)
  *   - `meltcSsgClean`      — set to `"false"` to skip cleaning (default: `true`)
  */
object SsgRunner:

  /** [[SyncRunner]] for [[scala.concurrent.Future]] that blocks via `Await.result`. */
  given SyncRunner[Future] with
    def runSync[A](fa: Future[A]): A = Await.result(fa, Duration.Inf)

  /** [[SyncRunner]] for the identity effect `[A] =>> A` (synchronous, no wrapping). */
  given SyncRunner[[A] =>> A] with
    def runSync[A](fa: A): A = fa

  /** Builds [[SsgConfig]] from system properties injected by `sbt-meltc`.
    *
    * @param template  HTML shell template applied to every generated page
    * @param manifest  Vite asset manifest; defaults to [[ViteManifest.empty]]
    */
  def configFromProps(
    template: Template,
    manifest: ViteManifest = ViteManifest.empty
  ): SsgConfig =
    val outputDir =
      Option(System.getProperty("meltcSsgOutputDir"))
        .map(java.nio.file.Paths.get(_))
        .getOrElse(sys.error("[meltkit-ssg] meltcSsgOutputDir system property not set"))

    val assetsDir =
      Option(System.getProperty("meltcSsgAssetsDir"))
        .filter(_.nonEmpty)
        .map(java.nio.file.Paths.get(_))

    val publicDir =
      Option(System.getProperty("meltcSsgPublicDir"))
        .filter(_.nonEmpty)
        .map(java.nio.file.Paths.get(_))

    val clean = Option(System.getProperty("meltcSsgClean")).forall(_ != "false")

    SsgConfig(
      outputDir   = outputDir,
      template    = template,
      manifest    = manifest,
      assetsDir   = assetsDir,
      publicDir   = publicDir,
      cleanOutput = clean
    )
