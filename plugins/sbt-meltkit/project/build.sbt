// Makes sbt-meltc available on the classpath when compiling MeltkitPlugin.scala.
// MeltkitPlugin.scala imports meltc.sbt.MeltcPlugin and declares requires = MeltcPlugin,
// so the sbt-meltc classes must be on the compilation classpath of the sbt-meltkit sub-build.
// This mirrors the pattern used by the root project/build.sbt to make MeltcPlugin available
// in build.sbt without publishLocal.
lazy val root = (project in file("."))
  .dependsOn(ProjectRef(file("../../sbt-meltc"), "sbt-meltc"))
