/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.sbt

import java.io.File

import sbt.{ *, given }
import sbt.Keys.*

/** Auto-enabled on every JVM project to catch the "I wrote `.melt` files but
  * forgot `enablePlugins(MeltPlugin)`" mistake.
  *
  * Without this, an un-enabled project compiles a `.melt`-referencing import into
  * a bare `Not found: components` error with no hint at the real cause. This
  * plugin cannot compile the files (MeltPlugin is what does that), but it can
  * warn loudly so the misconfiguration is obvious.
  *
  * It stays silent when [[MeltPlugin]] is enabled (that plugin handles the
  * files) or when the project has no `.melt` sources.
  */
object MeltDetectPlugin extends AutoPlugin:

  override def trigger  = allRequirements
  override def requires = plugins.JvmPlugin

  private val MeltPluginClassName = "melt.sbt.MeltPlugin$"

  private def meltPluginEnabled(project: sbt.ResolvedProject): Boolean =
    project.autoPlugins.exists(_.getClass.getName == MeltPluginClassName)

  /** Recursively collects `.melt` files under the given source directories,
    * skipping directories that do not exist. */
  def findMeltFiles(srcDirs: Seq[File]): Seq[File] =
    srcDirs.filter(_.exists).flatMap(dir => (dir ** "*.melt").get())

  override def projectSettings: Seq[Setting[?]] = Seq(
    Compile / sourceGenerators += Def.task {
      if !meltPluginEnabled(thisProject.value) then
        val melts = findMeltFiles((Compile / unmanagedSourceDirectories).value)
        if melts.nonEmpty then
          val shown = melts.take(3).map(_.getName).mkString(", ")
          val more  = if melts.size > 3 then s", … (+${ melts.size - 3 } more)" else ""
          streams.value.log.warn(
            s"[melt] found ${ melts.size } .melt file(s) in '${ name.value }' but MeltPlugin is not enabled — " +
              s"they will NOT be compiled. Add `enablePlugins(MeltPlugin)` (or MeltkitPlugin) to this project. " +
              s"Files: $shown$more"
          )
      Seq.empty[File]
    }.taskValue
  )
