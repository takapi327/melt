scalaVersion := "3.8.4"

name := "hello-world"

enablePlugins(ScalaJSPlugin, MeltPlugin)

scalaJSUseMainModuleInitializer := true

libraryDependencies += "io.github.takapi327" %% "melt-runtime" % sys.props.getOrElse("plugin.version", "0.1.0-SNAPSHOT")
