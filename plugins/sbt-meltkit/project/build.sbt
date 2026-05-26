// Makes sbt-melt available on the classpath when compiling MeltkitPlugin.scala.
// MeltkitPlugin.scala imports melt.sbt.MeltPlugin and declares requires = MeltPlugin,
// so the sbt-melt classes must be on the compilation classpath of the sbt-meltkit sub-build.
// This mirrors the pattern used by the root project/build.sbt to make MeltPlugin available
// in build.sbt without publishLocal.
lazy val root = (project in file("."))
  .dependsOn(ProjectRef(file("../../sbt-melt"), "sbt-melt"))
