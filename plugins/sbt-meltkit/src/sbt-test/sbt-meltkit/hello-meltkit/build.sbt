scalaVersion := "3.8.3"
name         := "hello-meltkit"

enablePlugins(ScalaJSPlugin, MeltkitPlugin)

meltMode := Some(Browser)

libraryDependencies += "io.github.takapi327" %%% "melt-runtime" %
  sys.props.getOrElse("plugin.version", "0.1.0-SNAPSHOT")
