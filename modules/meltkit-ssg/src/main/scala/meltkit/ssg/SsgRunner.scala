/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.ssg

/** JVM entry point forked by `sbt-meltc`'s `meltcStaticGenerate` task.
  *
  * == Arguments ==
  *
  * `args(0)` — fully-qualified class name of the user's [[SsgApp]] object
  *             (e.g. `"docs.MySsg"`)
  *
  * == System properties ==
  *
  *   - `meltcSsgOutputDir`  — output directory path (required)
  *   - `meltcSsgAssetsDir`  — Vite assets directory to copy (optional)
  *   - `meltcSsgClean`      — set to `"false"` to skip cleaning (default: `true`)
  */
object SsgRunner:

  def main(args: Array[String]): Unit =
    val mainClass = args.headOption.getOrElse(
      sys.error("[meltkit-ssg] SsgApp class name not provided")
    )

    val outputDir =
      Option(System.getProperty("meltcSsgOutputDir"))
        .map(java.nio.file.Paths.get(_))
        .getOrElse(sys.error("[meltkit-ssg] meltcSsgOutputDir system property not set"))

    val assetsDir =
      Option(System.getProperty("meltcSsgAssetsDir"))
        .filter(_.nonEmpty)
        .map(java.nio.file.Paths.get(_))

    val publicDir =
      Option(System.getProperty("meltcSsgPublicDir"))
        .filter(_.nonEmpty)
        .map(java.nio.file.Paths.get(_))

    val clean = Option(System.getProperty("meltcSsgClean")).forall(_ != "false")

    // Load the user's SsgApp object via reflection.
    // generate(config) captures syncRunner from the trait — no using arg needed.
    val app =
      try
        Class
          .forName(mainClass + "$")
          .getField("MODULE$")
          .get(null)
          .asInstanceOf[SsgApp[?]]
      catch
        case _: ClassNotFoundException =>
          sys.error(s"[meltkit-ssg] SsgApp class not found: '$mainClass'. Check meltcSsgMainClass in your build.sbt.")
        case _: ClassCastException =>
          sys.error(
            s"[meltkit-ssg] '$mainClass' does not extend SsgApp. Make sure the object extends meltkit.ssg.SsgApp."
          )

    app.generate(SsgConfig(outputDir, assetsDir, publicDir, cleanOutput = clean))
    println(s"[meltkit-ssg] Done. Output: $outputDir")
