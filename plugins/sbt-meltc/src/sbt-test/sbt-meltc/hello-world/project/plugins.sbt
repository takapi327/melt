addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.21.0")

sys.props.get("plugin.version") match {
  case Some(v) => addSbtPlugin("io.github.takapi327" % "sbt-meltc" % v)
  case _       => sys.error("plugin.version not set. Run `sbt +publishLocal` in the monorepo root first.")
}
