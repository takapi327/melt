/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.sbt

import sbt.*

/** Central registry of Melt's internal module dependencies, versioned by the generated
  * [[melt.build.Version]].
  *
  * `%%` is resolved per consumer platform: on a Scala.js project it upgrades to the
  * `_sjs1_3` artifact (via `platformDepsCrossVersion`), on the JVM it stays `_3`. So the
  * same `ModuleID` serves the browser/node (JS) and http4s/server (JVM) consumers.
  */
trait Dependencies:

  private def component(id: String): ModuleID =
    "io.github.takapi327" %% id % melt.build.Version.current

  val meltRuntime           = component("melt-runtime")
  val meltCodegen           = component("melt-codegen")
  val meltkit               = component("meltkit")
  val meltkitAdapterBrowser = component("meltkit-adapter-browser")
  val meltkitAdapterNode    = component("meltkit-adapter-node")
  val meltkitAdapterHttp4s  = component("meltkit-adapter-http4s")

  /** JVM only — zio-http ships no server driver for Scala.js, so this must never be added to a
    * Scala.js project (see `MeltMode.ZioHttp`). */
  val meltkitAdapterZioHttp = component("meltkit-adapter-zio-http")

object Dependencies extends Dependencies
