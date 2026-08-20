ThisBuild / scalaVersion := "3.8.4"
ThisBuild / version      := "0.1.0-SNAPSHOT"

// A shared `shared.Const` provided per platform (stands in for a crossProject's
// .js / .jvm without needing the crossproject plugin): the frontend App.melt and the
// backend both reference it, so it must be on both derived classpaths.
lazy val sharedJs = project
  .in(file("shared-js"))
  .enablePlugins(org.scalajs.sbtplugin.ScalaJSPlugin)

lazy val sharedJvm = project
  .in(file("shared-jvm"))

// The derived root-frontend depends on the JS side, root-backend on the JVM side.
// Without these, App.melt fails to resolve `shared.Const` on the respective platform.
lazy val root = project
  .in(file("."))
  .enablePlugins(MeltkitAppPlugin)
  .frontendDependsOn(sharedJs)
  .backendDependsOn(sharedJvm)
