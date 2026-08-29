ThisBuild / scalaVersion := "3.8.4"
ThisBuild / version      := "0.1.0-SNAPSHOT"

// One declaration derives root-frontend / root-backend. The per-side extensions
// route settings to each derived project (plain .settings would apply to the root
// shell only, since in derivedProjects the project's settings are fully resolved).
lazy val root = project.in(file("."))
  .enablePlugins(MeltkitAppPlugin)
  .autoAggregate
  .sharedSettings(scalacOptions += "-deprecation")
  .frontendSettings(meltkitSplitPackages := List("components"))
  .backendSettings(run / fork := true)
