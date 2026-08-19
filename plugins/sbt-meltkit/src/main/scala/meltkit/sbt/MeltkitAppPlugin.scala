/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.sbt

import sbt.{ *, given }
import sbt.Keys.*

import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.{ fastLinkJS, fullLinkJS }

import melt.sbt.MeltPlugin.autoImport.*
import meltkit.sbt.MeltkitPlugin.autoImport.*

/** Server adapter used by the auto-derived backend project. */
sealed abstract class MeltServerAdapter
object MeltServerAdapter:
  /** Built-in Undertow server, `MeltKit[Future]` (no cats-effect / http4s). JVM. */
  case object Undertow extends MeltServerAdapter

  /** http4s adapter (`meltkit-adapter-http4s`), `MeltKit[IO]`. JVM. */
  case object Http4s extends MeltServerAdapter

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
  * lazy val root = project.in(file("."))
  *   .enablePlugins(MeltkitAppPlugin)
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
  * The Melt components in `frontend/` are therefore compiled twice — once to JS
  * (hydration) and once inside the backend (SSR) — which is exactly the crossProject
  * this plugin removes the boilerplate for.
  */
object MeltkitAppPlugin extends AutoPlugin:

  override def trigger  = noTrigger
  // Require JvmPlugin so this plugin's settings load *after* it — our `Compile / run`
  // override on the root then wins over the default (which fails with "no main class").
  override def requires = sbt.plugins.JvmPlugin

  object autoImport:
    val meltkitServerAdapter =
      settingKey[MeltServerAdapter]("Server adapter for the auto-derived backend project (default: Undertow)")
    val MeltServerAdapter: meltkit.sbt.MeltServerAdapter.type = meltkit.sbt.MeltServerAdapter
    type MeltServerAdapter = meltkit.sbt.MeltServerAdapter

    val buildMode =
      settingKey[MeltBuildMode]("Scala.js link mode for `root/run`: Fast (fastLinkJS) or Full (fullLinkJS)")
    val MeltBuildMode: meltkit.sbt.MeltBuildMode.type = meltkit.sbt.MeltBuildMode
    type MeltBuildMode = meltkit.sbt.MeltBuildMode

  import autoImport.*

  override def globalSettings: Seq[Setting[?]] = Seq(
    meltkitServerAdapter := MeltServerAdapter.Undertow,
    buildMode            := MeltBuildMode.Fast
  )

  /** Applied to the root project (the only one with this plugin enabled).
    * `root/run` links the frontend hydration bundle, then runs the backend server —
    * so a single `run` builds the client and starts the SSR server.
    */
  override def projectSettings: Seq[Setting[?]] = Seq(
    Compile / run := Def.inputTaskDyn {
      val _  = sbt.complete.DefaultParsers.spaceDelimited("<arg>").parsed
      val id = thisProject.value.id
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

      lazy val frontend: Project =
        Project(id = proj.id + "-frontend", base = base / "frontend")
          .enablePlugins(ScalaJSPlugin, MeltkitPlugin)
          .settings(
            meltMode := Some(MeltMode.Browser)
          )

      lazy val backend: Project =
        Project(id = proj.id + "-backend", base = base / "backend")
          .enablePlugins(MeltkitPlugin)
          .settings(
            run / fork := true,
            meltMode := (meltkitServerAdapter.value match
              case MeltServerAdapter.Http4s   => Some(MeltMode.Http4s)
              case MeltServerAdapter.Undertow => None),
            meltCodegenMode            := "ssr",
            meltkitAssetManifestClient := Some(frontend),
            // Compile frontend's Melt components in SSR mode so the server can render them.
            Compile / unmanagedSourceDirectories += base / "frontend" / "src" / "main" / "scala"
          )

      Seq(frontend, backend)
