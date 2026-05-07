/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.ssg

import meltkit.{ ServerMeltKitPlatform, SyncRunner, Template, ViteManifest }

/** Trait that users extend to configure and run static site generation.
  *
  * `kit` accepts the same [[meltkit.MeltKit]] instance used for SSR/SPA, so
  * route definitions are shared across all rendering modes without any changes.
  *
  * == Usage example ==
  *
  * {{{
  * // Shared app definition (SSR and SSG use the same routes)
  * val app = MeltKit[IO]()
  * app.get("") { ctx => IO.pure(ctx.render(HomePage())) }
  * app.get("about") { ctx => IO.pure(ctx.render(AboutPage())) }
  *
  * // SSG configuration
  * object MySsg extends SsgApp[IO]:
  *   val kit      = app
  *   val template = Template.fromFile(Paths.get("src/main/resources/index.html"))
  *   val manifest = ViteManifest.empty
  *
  *   given syncRunner: SyncRunner[IO] with
  *     def runSync[A](fa: IO[A]): A = fa.unsafeRunSync()
  *
  *   def paths: IO[List[String]] =
  *     IO.pure(List("/", "/about"))
  * }}}
  *
  * @tparam F the effect type (e.g. `cats.effect.IO`)
  */
trait SsgApp[F[_]]:

  /** The [[meltkit.MeltKit]] router whose routes are used for static generation.
    *
    * This is typically the same instance used for SSR/SPA.
    */
  def kit: ServerMeltKitPlatform[F]

  /** Returns the list of URL paths to generate as static HTML pages.
    *
    * Dynamic routes must enumerate every concrete path here (equivalent to
    * Next.js `generateStaticParams`). Asynchronous DB / CMS / API queries
    * are supported via the `F[_]` effect.
    *
    * {{{
    * def paths: IO[List[String]] =
    *   db.allSlugs().map(slugs => List("/", "/about") ++ slugs.map(s => s"/posts/$s"))
    * }}}
    */
  def paths: F[List[String]]

  /** HTML shell template applied to every generated page. */
  def template: Template

  /** Vite asset manifest used for JS/CSS chunk injection.
    *
    * Use [[meltkit.ViteManifest.empty]] when hydration is not needed.
    */
  def manifest: ViteManifest

  /** Typeclass instance for blocking execution of `F[A]` effects.
    *
    * Must be provided as a `given` member in the concrete object.
    * Users who already depend on cats-effect can implement it as:
    *
    * {{{
    * import cats.effect.unsafe.implicits.global
    * given syncRunner: SyncRunner[IO] with
    *   def runSync[A](fa: IO[A]): A = fa.unsafeRunSync()
    * }}}
    */
  given syncRunner: SyncRunner[F]

  /** Asset base path injected into `<script>` / `<link>` tags. Default: `"/assets"`. */
  def basePath: String = "/assets"

  /** When `true`, JS/CSS chunks from [[manifest]] are injected into every page.
    *
    * Defaults to `true` whenever [[manifest]] is not [[meltkit.ViteManifest.empty]].
    */
  def useHydration: Boolean = manifest ne ViteManifest.empty

  /** Default page title. When empty, `RenderResult.title` is used instead. */
  def defaultTitle: String = ""

  /** Default HTML `lang` attribute. Default: `"en"`. */
  def defaultLang: String = "en"

  /** Generates the static site.
    *
    * Called by [[SsgRunner]] (via reflection) and can also be invoked directly
    * in tests. `syncRunner` is captured from the trait so no `using` argument
    * is needed at the call site.
    */
  final def generate(config: SsgConfig): Unit =
    SsgGenerator.run(this, config)(using syncRunner)
