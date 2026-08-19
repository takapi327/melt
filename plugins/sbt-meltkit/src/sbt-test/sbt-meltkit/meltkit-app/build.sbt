ThisBuild / scalaVersion := "3.8.4"
ThisBuild / version      := "0.1.0-SNAPSHOT"

// One declaration derives root{Frontend,Backend}. No manual client/server wiring.
lazy val root = project.in(file("."))
  .enablePlugins(MeltkitAppPlugin)
