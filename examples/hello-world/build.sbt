scalaVersion := "3.3.7"

name := "hello-world"

enablePlugins(ScalaJSPlugin)

scalaJSUseMainModuleInitializer := true

libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-dom" % "2.8.1",
  // To be added in Phase 3:
  // "dev.meltc" %%% "melt-runtime" % "0.1.0-SNAPSHOT",
)
