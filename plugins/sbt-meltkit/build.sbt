sbtPlugin := true

// In the monorepo source-dependency context (project/build.sbt uses ProjectRef),
// sbt-meltc is compiled first (guaranteed by plugins/sbt-meltkit/project/build.sbt).
// Its classes must be on the compilation classpath for MeltkitPlugin.scala to import
// meltc.sbt.MeltcPlugin and declare `override def requires = meltc.sbt.MeltcPlugin`.
// When using publishLocal the directory does not exist and Attributed.blank is ignored.
Compile / unmanagedClasspath += {
  Attributed.blank(
    baseDirectory.value / ".." / "sbt-meltc" / "target" / "scala-2.12" / "sbt-1.0" / "classes"
  )
}

// sbt-meltkit uses fastLinkJS / fullLinkJS / scalaJSLinkerOutputDirectory / Report
// when auto-generating AssetManifest.scala from a client project's linker output.
libraryDependencies += Defaults.sbtPluginExtra(
  "org.scala-js" % "sbt-scalajs" % "1.21.0",
  (pluginCrossBuild / sbtBinaryVersion).value,
  (update / scalaBinaryVersion).value
)
