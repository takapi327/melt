scalaVersion := "3.8.4"
name         := "zio-http-server"

// JVM server: no ScalaJSPlugin. `MeltMode.ZioHttp` selects the zio-http adapter and
// resolves meltCodegenMode to "ssr".
enablePlugins(MeltkitPlugin)

meltMode := Some(ZioHttp)

libraryDependencies += "dev.zio" %% "zio-http" % "3.11.4"
