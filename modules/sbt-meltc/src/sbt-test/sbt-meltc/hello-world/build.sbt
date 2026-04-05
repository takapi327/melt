scalaVersion := "3.3.7"

name := "hello-world"

enablePlugins(ScalaJSPlugin, MeltcPlugin)

scalaJSUseMainModuleInitializer := true

libraryDependencies += "io.github.takapi327" %%% "melt-runtime" % sys.props.getOrElse("plugin.version", "0.1.0-SNAPSHOT")
