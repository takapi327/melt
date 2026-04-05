import sbt._
import sbt.Keys._
import sbt.plugins.SbtPlugin
import sbt.ScriptedPlugin.autoImport._

import de.heikoseeberger.sbtheader.{AutomateHeaderPlugin, CommentBlockCreator, CommentStyle}
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport._
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport.HeaderPattern.commentBetween

import ScalaVersions._

object BuildSettings {

  val customCommentStyle: CommentStyle =
    CommentStyle(
      new CommentBlockCreator("/**", " *", " */"),
      commentBetween("""/\**+""", "*", """\*/""")
    )

  /** Settings for scripted tests. */
  def scriptedSettings: Seq[Setting[_]] = Seq(
    scriptedLaunchOpts := {
      scriptedLaunchOpts.value ++
        Seq("-Xmx1024M", s"-Dplugin.version=${version.value}")
    },
    scriptedBufferLog := false
  )

  /** Settings shared across all projects. */
  def commonSettings: Seq[Setting[_]] = Def.settings(
    organization     := "dev.meltc",
    organizationName := "meltc",
    startYear        := Some(2026),
    homepage         := Some(url("https://github.com/takapi327/melt")),
    licenses         := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")),
    run / fork       := true,
    developers += Developer(
      id = "takapi327",
      name = "Takahiko Tominaga",
      email = "",
      url = url("https://github.com/takapi327")
    ),
    headerMappings := headerMappings.value + (HeaderFileType.scala -> customCommentStyle),
    headerLicense  := Some(
      HeaderLicense.Custom(
        """|Copyright (c) 2026 by Takahiko Tominaga
           |This software is licensed under the Apache License, Version 2.0 (the "License").
           |For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
           |""".stripMargin
      )
    )
  )

  /** Helper for sbt plugin projects. */
  object MeltSbtPluginProject {
    def apply(name: String, dir: String): Project =
      Project(name, file(dir))
        .settings(scalaVersion := scala2)
        .settings(commonSettings)
        .settings(scriptedSettings)
        .enablePlugins(SbtPlugin, AutomateHeaderPlugin)
  }
}
