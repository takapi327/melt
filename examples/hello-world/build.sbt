scalaVersion := "3.3.7"

name := "hello-world"

enablePlugins(ScalaJSPlugin)

scalaJSUseMainModuleInitializer := true

libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-dom" % "2.8.1",
  // Phase 3 で追加予定:
  // "dev.meltc" %%% "melt-runtime" % "0.1.0-SNAPSHOT",
)
