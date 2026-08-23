scalaVersion := "3.8.4"

name := "link-checking"

// No ScalaJS plugin → meltCodegenMode "auto" resolves to SSR, whose generated
// code runs the `route"..."` macro on the JVM at compile time.
enablePlugins(MeltPlugin)

// No meltPackage: the `components/` source subdir already yields package `components`,
// which is where the given RouteRegistry lives.
meltCodegenMode  := "ssr"
meltLinkChecking := true

val pluginVersion = sys.props.getOrElse("plugin.version", "0.1.0-SNAPSHOT")

libraryDependencies += "io.github.takapi327" %% "melt-runtime" % pluginVersion
libraryDependencies += "io.github.takapi327" %% "meltkit"      % pluginVersion
