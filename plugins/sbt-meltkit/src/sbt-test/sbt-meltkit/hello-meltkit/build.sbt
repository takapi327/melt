scalaVersion := "3.3.7"
name         := "hello-meltkit"

enablePlugins(ScalaJSPlugin, MeltkitPlugin)

scalaJSUseMainModuleInitializer := true
meltMode     := Some(Browser)
meltcPackage := "components"

libraryDependencies += "io.github.takapi327" %%% "melt-runtime" %
  sys.props.getOrElse("plugin.version", "0.1.0-SNAPSHOT")
