import scala.language.implicitConversions

import sbt._
import sbt.Keys._

import sbtcrossproject.CrossProject

import de.heikoseeberger.sbtheader.AutomateHeaderPlugin

import BuildSettings._
import ScalaVersions._

object Implicits {

  implicit class CrossProjectOps(private val project: CrossProject) extends AnyVal {

    def module(_name: String, projectDescription: String): CrossProject =
      project
        .in(file(s"modules/${_name}"))
        .settings(
          name        := s"melt-${_name}",
          description := projectDescription
        )
        .defaultSettings

    def example(_name: String, projectDescription: String): CrossProject =
      project
        .in(file(s"examples/${_name}"))
        .settings(
          name        := _name,
          description := projectDescription
        )
        .defaultSettings
        .enablePlugins(NoPublishPlugin)

    def defaultSettings: CrossProject =
      project
        .settings(scalaVersion := scala3)
        .settings(commonSettings)
        .enablePlugins(AutomateHeaderPlugin)
  }

  implicit def builderOps(builder: CrossProject.Builder): CrossProjectOps =
    new CrossProjectOps(builder.build())
}
