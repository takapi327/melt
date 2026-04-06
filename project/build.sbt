// Makes sbt-meltc available as a plugin in the main build WITHOUT publishLocal.
// sbt compiles project/ first (meta-build); by depending on sbt-meltc from source
// here, MeltcPlugin becomes available to build.sbt via enablePlugins(MeltcPlugin).
lazy val root = (project in file("."))
  .dependsOn(ProjectRef(file("../modules/sbt-meltc"), "sbt-meltc"))
