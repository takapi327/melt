// Makes sbt-meltc and sbt-meltkit available as plugins in the main build WITHOUT publishLocal.
// sbt compiles project/ first (meta-build); by depending on both from source here,
// MeltcPlugin and MeltkitPlugin become available to build.sbt via enablePlugins(...).
//
// Compilation ordering between sbt-meltc and sbt-meltkit is handled by
// plugins/sbt-meltkit/project/build.sbt, which makes sbt-meltc available on the
// sbt-meltkit sub-build's classpath (same pattern as this file for the root build).

lazy val sbtMeltcRef   = ProjectRef(file("../plugins/sbt-meltc"), "sbt-meltc")
lazy val sbtMeltkitRef = ProjectRef(file("../plugins/sbt-meltkit"), "sbt-meltkit")

lazy val root = (project in file("."))
  .dependsOn(sbtMeltcRef, sbtMeltkitRef)
  .settings(
    // Provides JSDOMNodeJSEnv for Scala.js DOM tests (used in melt-testkit and examples)
    libraryDependencies += "org.scala-js" %% "scalajs-env-jsdom-nodejs" % "1.1.0"
  )
