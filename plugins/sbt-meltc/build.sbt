sbtPlugin := true

// sbt-scalajs is pulled in so that sbt-meltc can reference
// `org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.{ fastLinkJS,
// scalaJSLinkerOutputDirectory }` and `org.scalajs.linker.interface.Report`
// when auto-generating `AssetManifest.scala` from a client project's
// linker output (§C12).
//
// This is declared in sbt-meltc's OWN build.sbt — not the monorepo root —
// because the meta-build (`project/build.sbt`) compiles sbt-meltc as a
// source dependency of the root project, which evaluates THIS file's
// settings BEFORE the main build.sbt runs.
libraryDependencies += Defaults.sbtPluginExtra(
  "org.scala-js" % "sbt-scalajs" % "1.21.0",
  (pluginCrossBuild / sbtBinaryVersion).value,
  (update / scalaBinaryVersion).value
)
