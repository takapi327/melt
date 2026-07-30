scalaVersion := "3.3.7"

name := "public-env"

enablePlugins(ScalaJSPlugin, MeltPlugin)

scalaJSUseMainModuleInitializer := true

// Whitelisted, browser-safe public config → generated `PublicEnv` object.
meltPublicEnv := Map("apiBase" -> "/api", "appName" -> "Demo")

libraryDependencies += "io.github.takapi327" %% "melt-runtime" % sys.props.getOrElse("plugin.version", "0.1.0-SNAPSHOT")
