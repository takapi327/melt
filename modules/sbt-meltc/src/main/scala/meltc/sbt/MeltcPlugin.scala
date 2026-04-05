/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.sbt

import sbt._
import sbt.Keys._

/** sbt-meltc plugin
  *
  * Detects `.melt` files under `meltcSourceDirectory` and compiles each one
  * to a `.scala` file via a forked JVM process running `meltc.MeltcMain`.
  *
  * == Setup ==
  *
  * Enable the plugin and publish `meltc` locally first:
  * {{{
  * // In your build.sbt:
  * enablePlugins(MeltcPlugin)
  * meltcPackage := "components"
  * }}}
  *
  * {{{
  * // One-time in the melt monorepo:
  * sbt meltcJVM/publishLocal runtime/publishLocal sbt-meltc/publishLocal
  * }}}
  *
  * The plugin resolves `meltc` (including all transitive deps such as
  * `scala3-library`) using its own Ivy configuration `meltc-compiler`, so
  * you do not need to configure the classpath manually.
  *
  * === Monorepo override ===
  * When working inside the melt monorepo you can skip `publishLocal` by wiring
  * the JVM full classpath directly:
  * {{{
  * meltcCompilerClasspath := (meltcJVM / Compile / fullClasspath).value.files
  * }}}
  */
object MeltcPlugin extends AutoPlugin {

  override def trigger  = noTrigger
  override def requires = plugins.JvmPlugin

  /** Internal Ivy configuration used to resolve the meltc compiler and its
    * transitive runtime dependencies (including `scala3-library`).
    */
  private val MeltcCompilerConfig = config("meltc-compiler").hide

  object autoImport {

    /** Directory that contains `.melt` source files.
      * Default: `src/main/components`
      */
    val meltcSourceDirectory =
      settingKey[File]("Directory containing .melt source files")

    /** Directory where generated `.scala` files are written.
      * Default: `target/scala-x.y/src_managed/meltc`
      */
    val meltcOutputDirectory =
      settingKey[File]("Directory for generated .scala files")

    /** Scala package placed at the top of every generated file.
      * Default: `"components"`
      */
    val meltcPackage =
      settingKey[String]("Package for generated Scala files")

    /** Version of `meltc` to resolve as the compiler.
      * Default: matches the sbt-meltc plugin version set via `-Dplugin.version`.
      */
    val meltcCompilerVersion =
      settingKey[String]("Version of meltc to use as the compiler")

    /** Full classpath (jar files) used when forking the meltc JVM compiler.
      *
      * By default this is resolved automatically from [[meltcCompilerVersion]]
      * using the `meltc-compiler` Ivy configuration.
      * Override to point at the meltc JVM output directly
      * (e.g. `(meltcJVM / Compile / fullClasspath).value.files`).
      */
    val meltcCompilerClasspath =
      taskKey[Seq[File]]("Classpath for the meltc compiler JVM process")

    /** Compiles all `.melt` files and returns the generated `.scala` files. */
    val meltcGenerate =
      taskKey[Seq[File]]("Compile .melt files to .scala files")
  }

  import autoImport._

  override def projectSettings: Seq[Setting[_]] = Seq(
    meltcSourceDirectory := (Compile / sourceDirectory).value / "scala",
    meltcOutputDirectory := (Compile / sourceManaged).value / "meltc",
    meltcPackage         := "",
    meltcCompilerVersion := sys.props.getOrElse("plugin.version", "0.1.0-SNAPSHOT"),

    // ── Ivy resolution for the compiler classpath ──────────────────────────
    ivyConfigurations += MeltcCompilerConfig,
    libraryDependencies += {
      val v = meltcCompilerVersion.value
      // CrossVersion.disabled: meltc_3 is already the full artifact ID after publishLocal
      ("io.github.takapi327" % "meltc_3" % v cross CrossVersion.disabled) % MeltcCompilerConfig
    },
    meltcCompilerClasspath := update.value.select(
      configurationFilter(MeltcCompilerConfig.name)
    ),

    meltcGenerate := compileMeltFiles(
      streams    = streams.value,
      srcDir     = meltcSourceDirectory.value,
      outDir     = meltcOutputDirectory.value,
      pkg        = meltcPackage.value,
      compilerCp = meltcCompilerClasspath.value
    ),
    Compile / sourceGenerators += meltcGenerate.taskValue
  )

  // ── Implementation ─────────────────────────────────────────────────────────

  private def compileMeltFiles(
    streams:    TaskStreams,
    srcDir:     File,
    outDir:     File,
    pkg:        String,
    compilerCp: Seq[File]
  ): Seq[File] = {
    val log = streams.log

    if (compilerCp.isEmpty) {
      log.warn(
        "[sbt-meltc] meltcCompilerClasspath is empty — no .melt files will be compiled.\n" +
          "  Run `sbt meltcJVM/publishLocal` in the melt monorepo first."
      )
      return Seq.empty
    }

    IO.createDirectory(outDir)

    val meltFiles = (srcDir ** "*.melt").get
    if (meltFiles.isEmpty) {
      log.debug(s"[sbt-meltc] No .melt files found in $srcDir")
      return Seq.empty
    }

    val cpStr = compilerCp.map(_.getAbsolutePath).mkString(java.io.File.pathSeparator)

    meltFiles.flatMap { meltFile =>
      val objectName = meltFile.base.head.toUpper + meltFile.base.tail

      // Derive sub-package from the relative path between srcDir and the file's parent directory.
      // e.g. srcDir=src/main/components, file=src/main/components/atom/Button.melt → subPkg="atom"
      val subPkg = IO.relativize(srcDir, meltFile.getParentFile)
        .map(_.replace(java.io.File.separatorChar, '.'))
        .getOrElse("")
      val fullPkg = (pkg, subPkg) match {
        case (p, "") => p
        case ("", s) => s
        case (p, s)  => s"$p.$s"
      }

      // Mirror directory structure in the output so that each package lives in its own sub-folder.
      val outSubDir = IO.relativize(srcDir, meltFile.getParentFile)
        .map(rel => new java.io.File(outDir, rel))
        .getOrElse(outDir)
      IO.createDirectory(outSubDir)
      val outFile = outSubDir / s"$objectName.scala"

      log.info(s"[sbt-meltc] Compiling ${meltFile.getName} → ${outFile.getName}")

      val javaArgs = Seq(
        "-cp", cpStr,
        "meltc.MeltcMain",
        meltFile.getAbsolutePath,
        outFile.getAbsolutePath,
        objectName,
        fullPkg
      )

      val exitCode = Fork.java(ForkOptions(), javaArgs)

      if (exitCode == 0) {
        log.info(s"[sbt-meltc] Generated ${outFile.getAbsolutePath}")
        Seq(outFile)
      } else {
        log.error(s"[sbt-meltc] Compilation failed for ${meltFile.getName} (exit $exitCode)")
        Seq.empty
      }
    }
  }
}
