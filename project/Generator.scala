import sbt.*

object Generator:

  /** Generates `melt/build/Version.scala` so the sbt plugins can reference the actual
    * build version (`melt.build.Version.current`) at compile time — instead of a
    * hard-coded string — and hand it to [[melt.sbt.Dependencies]].
    */
  def version(
    version:      String,
    scalaVersion: String,
    sbtVersion:   String,
    dir:          File
  ): Seq[File] =
    val file        = dir / "Version.scala"
    val scalaSource =
      s"""|package melt.build
          |
          |object Version {
          |  val current      = "$version"
          |  val scalaVersion = "$scalaVersion"
          |  val sbtVersion   = "$sbtVersion"
          |}
          |""".stripMargin

    if !file.exists() || IO.read(file) != scalaSource then IO.write(file, scalaSource)

    Seq(file)
