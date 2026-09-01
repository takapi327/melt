scalaVersion := "3.8.4"

name := "link-checking-routes"

enablePlugins(MeltPlugin)

meltCodegenMode := "ssr"

// Link checking needs no `given RouteRegistry`: naming the routes object makes the compiler
// emit `checkedRouteFor[routes.Routes.type](...)`, and naming it IS the on/off switch.
meltLinkCheckingRoutes := Some("routes.Routes")

val pluginVersion = sys.props.getOrElse("plugin.version", "0.1.0-SNAPSHOT")

libraryDependencies += "io.github.takapi327" %% "melt-runtime" % pluginVersion
libraryDependencies += "io.github.takapi327" %% "meltkit"      % pluginVersion
