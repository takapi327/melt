/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.sbt

import sbt.{ *, given }
import sbt.Keys.*

import org.scalajs.linker.interface.ModuleSplitStyle
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.{ fastLinkJS, fullLinkJS, scalaJSLinkerConfig }

import melt.sbt.MeltPlugin.autoImport.*

import meltkit.sbt.MeltkitPlugin.autoImport.*

/** Server adapter used by the auto-derived backend project. */
sealed abstract class MeltServerAdapter
object MeltServerAdapter:
  /** Built-in Undertow server, `MeltKit[Future]` (no cats-effect / http4s). JVM. */
  case object Undertow extends MeltServerAdapter

  /** http4s adapter (`meltkit-adapter-http4s`), `MeltKit[IO]`. JVM. */
  case object Http4s extends MeltServerAdapter

  /** zio-http adapter (`meltkit-adapter-zio-http`), `MeltKit[[A] =>> ZIO[R, Throwable, A]]`. JVM. */
  case object ZioHttp extends MeltServerAdapter

/** Scala.js linking mode used by `root/run`. */
sealed abstract class MeltBuildMode
object MeltBuildMode:
  /** `fastLinkJS` — fast, unoptimized (development). */
  case object Fast extends MeltBuildMode

  /** `fullLinkJS` — optimized (production). */
  case object Full extends MeltBuildMode

/** Single-declaration full-stack SSR app.
  *
  * Enabling this on one root project derives the two projects an SSR app needs —
  * so you manage two source directories, `frontend/` (Melt components) and
  * `backend/` (server), instead of hand-wiring a client crossProject and a server
  * project:
  *
  * {{{
  * lazy val domain = crossProject(JVMPlatform, JSPlatform).in(file("domain"))
  *
  * lazy val root = project.in(file("."))
  *   .enablePlugins(MeltkitAppPlugin)
  *   .autoAggregate                                              // see "Aggregation" below
  *   .sharedSettings(libraryDependencies += "org" %% "lib" % v)  // both derived projects
  *   .frontendSettings(meltkitSplitPackages := List("pages"))    // frontend only
  *   .backendSettings(javaOptions += "-Xmx1g")                   // backend only
  *   .frontendDependsOn(domain.js)                               // project dep, frontend
  *   .backendDependsOn(domain.jvm)                               // project dep, backend
  *
  * // optional — default is Undertow:
  * Global / meltkitServerAdapter := MeltServerAdapter.Http4s
  * }}}
  *
  * Derived (via [[sbt.AutoPlugin.derivedProjects]]):
  *   - `<root>-frontend` — Scala.js + Melt (Browser mode): the hydration bundle.
  *   - `<root>-backend`  — JVM server (Undertow/http4s): compiles `frontend/`'s
  *     `.melt` sources in SSR mode (`unmanagedSourceDirectories`) and reads the
  *     frontend's asset manifest.
  *
  * ==Aggregation==
  *
  * Add `.autoAggregate` so `<root>/compile`, `<root>/test`, `<root>/clean` and the rest run on
  * both derived projects. Without it the root is an empty shell: it owns no sources, so
  * `<root>/compile` succeeds while compiling nothing.
  *
  * A plugin cannot supply this itself — `aggregate` belongs to the project definition, not to
  * settings, and `derivedProjects` only adds projects, it never rewrites the one that enabled
  * the plugin. sbt's built-in `Project.autoAggregate` expands to every subproject whose base
  * directory sits under the root's, which is exactly `<base>/frontend` and `<base>/backend`.
  *
  * Per-project settings use the `.sharedSettings` / `.frontendSettings` /
  * `.backendSettings` extensions (plain `.settings` applies to the root shell only);
  * project (classpath) dependencies use `.sharedDependsOn` / `.frontendDependsOn` /
  * `.backendDependsOn`. Published/Ivy artifacts need only `libraryDependencies` (via the
  * `*Settings` extensions) — no `dependsOn`.
  *
  * The Melt components in `frontend/` are therefore compiled twice — once to JS
  * (hydration) and once inside the backend (SSR) — which is exactly the crossProject
  * this plugin removes the boilerplate for.
  */
object MeltkitAppPlugin extends AutoPlugin:

  override def trigger = noTrigger
  // Require JvmPlugin so this plugin's settings load *after* it — our `Compile / run`
  // override on the root then wins over the default (which fails with "no main class").
  override def requires = sbt.plugins.JvmPlugin

  // Settings recorded by the `sharedSettings` / `frontendSettings` / `backendSettings`
  // extensions, keyed by the root project's id. `derivedProjects` reads them when
  // building the derived projects. Access is single-threaded during build loading.
  //
  // A dedicated `sharedSettings` is required because the plain `.settings(...)` on the
  // root applies only to the root: in `derivedProjects` the project's `settings` is the
  // fully-resolved list (hundreds of auto-injected settings), so the user's own additions
  // can't be isolated and re-applied to the children.
  private val sharedExtra   = collection.mutable.Map.empty[String, Seq[Def.SettingsDefinition]]
  private val frontendExtra = collection.mutable.Map.empty[String, Seq[Def.SettingsDefinition]]
  private val backendExtra  = collection.mutable.Map.empty[String, Seq[Def.SettingsDefinition]]

  // Project (classpath) dependencies for the derived projects. `libraryDependencies`
  // (via *Settings) covers published/Ivy artifacts; these cover source dependencies on
  // another project in the same build — e.g. a shared crossProject's `.js` / `.jvm`.
  private val sharedDeps   = collection.mutable.Map.empty[String, Seq[ClasspathDep[ProjectReference]]]
  private val frontendDeps = collection.mutable.Map.empty[String, Seq[ClasspathDep[ProjectReference]]]
  private val backendDeps  = collection.mutable.Map.empty[String, Seq[ClasspathDep[ProjectReference]]]

  object autoImport:

    extension (p: Project)
      /** Settings applied to BOTH derived projects (`<id>-frontend` and `<id>-backend`).
        * Use for shared library dependencies, scalac options, etc. A dependency added
        * with `%%` resolves per-platform (`_sjs1_3` on the frontend, `_3` on the backend).
        * Chainable and additive. (Plain `.settings(...)` applies to the root shell only.) */
      def sharedSettings(ss: Def.SettingsDefinition*): Project =
        sharedExtra(p.id) = sharedExtra.getOrElse(p.id, Nil) ++ ss
        p

      /** Settings applied only to the derived `<id>-frontend` (Scala.js) project.
        * Chainable and additive; pair with `.backendSettings` / `.sharedSettings`. */
      def frontendSettings(ss: Def.SettingsDefinition*): Project =
        frontendExtra(p.id) = frontendExtra.getOrElse(p.id, Nil) ++ ss
        p

      /** Settings applied only to the derived `<id>-backend` (JVM server) project.
        * Chainable and additive; pair with `.frontendSettings` / `.sharedSettings`. */
      def backendSettings(ss: Def.SettingsDefinition*): Project =
        backendExtra(p.id) = backendExtra.getOrElse(p.id, Nil) ++ ss
        p

      /** Adds project (classpath) dependencies to BOTH derived projects — the analogue
        * of `.dependsOn` for the auto-derived projects. Use for a platform-neutral module
        * shared by both; for a crossProject depend per side (`domain.js` / `domain.jvm`)
        * via `.frontendDependsOn` / `.backendDependsOn`. Chainable and additive. */
      def sharedDependsOn(deps: ClasspathDep[ProjectReference]*): Project =
        sharedDeps(p.id) = sharedDeps.getOrElse(p.id, Nil) ++ deps
        p

      /** Adds project (classpath) dependencies to the derived `<id>-frontend` (Scala.js)
        * project — e.g. a shared crossProject's `.js` side. Chainable and additive. */
      def frontendDependsOn(deps: ClasspathDep[ProjectReference]*): Project =
        frontendDeps(p.id) = frontendDeps.getOrElse(p.id, Nil) ++ deps
        p

      /** Adds project (classpath) dependencies to the derived `<id>-backend` (JVM)
        * project — e.g. a shared crossProject's `.jvm` side. Chainable and additive. */
      def backendDependsOn(deps: ClasspathDep[ProjectReference]*): Project =
        backendDeps(p.id) = backendDeps.getOrElse(p.id, Nil) ++ deps
        p

    val meltkitServerAdapter =
      settingKey[MeltServerAdapter]("Server adapter for the auto-derived backend project (default: Undertow)")
    val MeltServerAdapter: meltkit.sbt.MeltServerAdapter.type = meltkit.sbt.MeltServerAdapter
    type MeltServerAdapter = meltkit.sbt.MeltServerAdapter

    val buildMode =
      settingKey[MeltBuildMode]("Scala.js link mode for `root/run`: Fast (fastLinkJS) or Full (fullLinkJS)")
    val MeltBuildMode: meltkit.sbt.MeltBuildMode.type = meltkit.sbt.MeltBuildMode
    type MeltBuildMode = meltkit.sbt.MeltBuildMode

    /** Scala.js packages whose classes are each split into their own module chunk
      * (`ModuleSplitStyle.SmallModulesFor`), so a page loads only its component's JS.
      * Default: `List("components")`. */
    val meltkitSplitPackages =
      settingKey[Seq[String]]("Scala.js packages to split per-module (SmallModulesFor) on the frontend")

  import autoImport.*

  override def globalSettings: Seq[Setting[?]] = Seq(
    meltkitServerAdapter := MeltServerAdapter.Undertow,
    buildMode            := MeltBuildMode.Fast,
    meltkitSplitPackages := List("components")
  )

  /** Applied to the root project (the only one with this plugin enabled).
    * `root/run` links the frontend hydration bundle, then runs the backend server —
    * so a single `run` builds the client and starts the SSR server.
    */
  override def projectSettings: Seq[Setting[?]] = Seq(
    Compile / run := Def.inputTaskDyn {
      val _            = sbt.complete.DefaultParsers.spaceDelimited("<arg>").parsed
      val id           = thisProject.value.id
      val linkFrontend = buildMode.value match
        case MeltBuildMode.Full => LocalProject(id + "-frontend") / (Compile / fullLinkJS)
        case MeltBuildMode.Fast => LocalProject(id + "-frontend") / (Compile / fastLinkJS)
      Def.sequential(
        linkFrontend,
        (LocalProject(id + "-backend") / (Compile / run)).toTask("")
      )
    }.evaluated
  )

  // sbt 2.0 makes `autoPlugins` / `Plugins.And` private[sbt], so we detect
  // enablement from the public `plugins` expression's rendering.
  private def enabledOn(proj: ProjectDefinition[?]): Boolean =
    proj.plugins.toString.contains("MeltkitAppPlugin")

  override def derivedProjects(proj: ProjectDefinition[?]): Seq[Project] =
    if !enabledOn(proj) then Nil
    else
      val base = proj.base

      // `.sharedSettings` → both; `.frontendSettings` / `.backendSettings` → one side.
      // All are appended after the plugin's own settings, so the user's win.
      val shared  = sharedExtra.remove(proj.id).getOrElse(Nil)
      val feExtra = frontendExtra.remove(proj.id).getOrElse(Nil)
      val beExtra = backendExtra.remove(proj.id).getOrElse(Nil)

      // Project dependencies: `.sharedDependsOn` → both; `.frontendDependsOn` /
      // `.backendDependsOn` → one side.
      val sharedDep = sharedDeps.remove(proj.id).getOrElse(Nil)
      val feDep     = frontendDeps.remove(proj.id).getOrElse(Nil)
      val beDep     = backendDeps.remove(proj.id).getOrElse(Nil)

      lazy val frontend: Project =
        Project(id = proj.id + "-frontend", base = base / "frontend")
          .enablePlugins(ScalaJSPlugin, MeltkitPlugin)
          .settings(
            meltMode := Some(MeltMode.Browser),
            // Per-component code splitting: each class in the split packages becomes
            // its own module, so a page loads only the JS it needs.
            scalaJSLinkerConfig := scalaJSLinkerConfig.value
              .withModuleSplitStyle(ModuleSplitStyle.SmallModulesFor(meltkitSplitPackages.value.toList))
          )
          .settings(shared*)
          .settings(feExtra*)
          .dependsOn((sharedDep ++ feDep)*)

      lazy val backend: Project =
        Project(id = proj.id + "-backend", base = base / "backend")
          .enablePlugins(MeltkitPlugin)
          .settings(
            run / fork := true,
            meltMode   := (meltkitServerAdapter.value match
              case MeltServerAdapter.Http4s   => Some(MeltMode.Http4s)
              case MeltServerAdapter.ZioHttp  => Some(MeltMode.ZioHttp)
              case MeltServerAdapter.Undertow => None),
            meltCodegenMode            := "ssr",
            meltkitAssetManifestClient := Some(LocalProject(frontend.id)),
            // Follow buildMode so `buildMode := Full` serves the optimized (`-opt`)
            // bundle: the asset manifest / clientDistDir point at fullLinkJS output.
            meltkitClientFullLink := (buildMode.value match
              case MeltBuildMode.Full => true
              case MeltBuildMode.Fast => false),
            // Production (MELT_PROD=true): serve the frontend's Vite build (hashed
            // assets + manifest) instead of the raw Scala.js output.
            meltkitViteDistDir      := base / "frontend" / "dist",
            meltkitViteManifestPath := base / "frontend" / "dist" / ".vite" / "manifest.json",
            // Compile frontend's Melt components in SSR mode so the server can render them.
            Compile / unmanagedSourceDirectories += base / "frontend" / "src" / "main" / "scala"
          )
          .settings(shared*)
          .settings(beExtra*)
          .dependsOn((sharedDep ++ beDep)*)

      Seq(frontend, backend)
