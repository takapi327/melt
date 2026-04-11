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

    /** Enable emission of `@JSExportTopLevel("hydrate", moduleID = ...)`
      * hydration entries in SPA-mode generated code.
      *
      * Default: `false`. Existing Scala.js-only examples keep the
      * single-module CommonJSModule link config (the current default)
      * and do not need the per-component public modules.
      *
      * Set to `true` on projects that actually do SSR + client-side
      * hydration. Those projects typically also configure
      * `scalaJSLinkerConfig` with `ModuleKind.ESModule` and a
      * small-modules split style so each component ends up in its own
      * public chunk. See `examples/http4s-ssr-hydration` for a complete
      * Phase C setup.
      */
    val meltcHydration =
      settingKey[Boolean]("Emit @JSExportTopLevel hydration entries in SPA codegen")

    /** Compilation mode for `.melt` files.
      *
      * Values:
      *   - `"spa"` — generate Scala.js DOM-manipulating code (default for
      *              projects with `ScalaJSPlugin` enabled)
      *   - `"ssr"` — generate JVM HTML string-generating code (default for
      *              projects without `ScalaJSPlugin`)
      *
      * The default is auto-detected from the project's enabled plugins, so
      * crossProject (JVM + JS) users normally do not need to set this
      * manually: the `.jvm` sub-project will get `"ssr"` and the `.js`
      * sub-project will get `"spa"`.
      *
      * Detection is performed by scanning `thisProject.value.autoPlugins` for
      * a plugin whose class name is `org.scalajs.sbtplugin.ScalaJSPlugin`.
      * This avoids making sbt-meltc itself depend on sbt-scalajs.
      */
    val meltcMode =
      settingKey[String]("Compilation mode: 'spa' or 'ssr' (auto-detected from platform)")

    /** Directory that contains `.melt` source files.
      *
      * Default: `src/main/scala`.
      *
      * In a `crossProject` layout this setting alone is insufficient because
      * the platform-specific source directory does not contain `shared/` files.
      * Use [[meltcSourceDirectories]] instead — it defaults to the union of
      * all `Compile / unmanagedSourceDirectories`, which includes both the
      * platform-specific and the shared source directory provided by
      * sbt-crossproject.
      *
      * This key is kept for backwards compatibility; it seeds
      * `meltcSourceDirectories` by default.
      */
    val meltcSourceDirectory =
      settingKey[File]("Directory containing .melt source files (legacy single-dir form)")

    /** All directories to scan for `.melt` source files.
      *
      * Default: `Compile / unmanagedSourceDirectories` — this picks up both
      * the platform-specific source root (e.g. `jvm/src/main/scala`) and the
      * shared source root (e.g. `shared/src/main/scala`) automatically when
      * the project is a `crossProject` member.
      *
      * For single-platform projects this defaults to the single directory
      * `Compile / sourceDirectory / "scala"`, preserving legacy behaviour.
      */
    val meltcSourceDirectories =
      settingKey[Seq[File]]("Directories containing .melt source files (crossProject-aware)")

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

  /** Class name of Scala.js's sbt plugin. We check for this by string rather
    * than importing the class, so sbt-meltc does not need to declare a hard
    * dependency on sbt-scalajs.
    */
  private val ScalaJSPluginClassName = "org.scalajs.sbtplugin.ScalaJSPlugin$"

  /** Returns `true` iff the resolved project has `ScalaJSPlugin` enabled. */
  private def hasScalaJSPlugin(project: sbt.ResolvedProject): Boolean =
    project.autoPlugins.exists(_.getClass.getName == ScalaJSPluginClassName)

  override def projectSettings: Seq[Setting[_]] = Seq(
    meltcMode := {
      // Auto-detect: projects with ScalaJSPlugin → "spa", otherwise → "ssr".
      // Users can override this setting explicitly if needed.
      if (hasScalaJSPlugin(thisProject.value)) "spa" else "ssr"
    },
    meltcHydration := false,
    meltcSourceDirectory := (Compile / sourceDirectory).value / "scala",
    meltcSourceDirectories := {
      // In a crossProject the unmanagedSourceDirectories list already
      // contains both the platform-specific and the shared `scala` directory,
      // so scanning them all naturally supports .melt placement under shared.
      val unmanaged = (Compile / unmanagedSourceDirectories).value
      val legacy    = meltcSourceDirectory.value
      // Deduplicate while preserving order: unmanaged first, then legacy
      // (if not already included).
      (unmanaged ++ (if (unmanaged.contains(legacy)) Nil else Seq(legacy))).distinct
    },
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
      srcDirs    = meltcSourceDirectories.value,
      outDir     = meltcOutputDirectory.value,
      pkg        = meltcPackage.value,
      mode       = meltcMode.value,
      hydration  = meltcHydration.value,
      compilerCp = meltcCompilerClasspath.value
    ),
    Compile / sourceGenerators += meltcGenerate.taskValue
  )

  // ── Implementation ─────────────────────────────────────────────────────────

  private def compileMeltFiles(
    streams:    TaskStreams,
    srcDirs:    Seq[File],
    outDir:     File,
    pkg:        String,
    mode:       String,
    hydration:  Boolean,
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

    // Collect all .melt files from every configured source directory, tagged
    // with the directory they were discovered in (so we can compute the
    // correct relative sub-package against the owning source root).
    val meltFilesWithRoot: Seq[(File, File)] =
      srcDirs.filter(_.exists).flatMap { srcDir =>
        (srcDir ** "*.melt").get.map(f => (f, srcDir))
      }

    if (meltFilesWithRoot.isEmpty) {
      log.debug(s"[sbt-meltc] No .melt files found under ${ srcDirs.mkString(", ") }")
      return Seq.empty
    }

    val cpStr = compilerCp.map(_.getAbsolutePath).mkString(java.io.File.pathSeparator)

    val normalisedMode = mode.toLowerCase match {
      case "spa" | "ssr" => mode.toLowerCase
      case other =>
        log.warn(s"[sbt-meltc] unknown meltcMode '$other' — falling back to 'spa'")
        "spa"
    }

    meltFilesWithRoot.flatMap { case (meltFile, srcDir) =>
      val objectName = meltFile.base.head.toUpper + meltFile.base.tail

      // Derive sub-package from the relative path between the owning srcDir
      // and the file's parent directory.
      // e.g. srcDir=src/main/components, file=src/main/components/atom/Button.melt → subPkg="atom"
      val subPkg = IO
        .relativize(srcDir, meltFile.getParentFile)
        .map(_.replace(java.io.File.separatorChar, '.'))
        .getOrElse("")
      val fullPkg = (pkg, subPkg) match {
        case (p, "") => p
        case ("", s) => s
        case (p, s)  => s"$p.$s"
      }

      // Mirror directory structure in the output so that each package lives
      // in its own sub-folder. Derived against the owning srcDir.
      val outSubDir = IO
        .relativize(srcDir, meltFile.getParentFile)
        .map(rel => new java.io.File(outDir, rel))
        .getOrElse(outDir)
      IO.createDirectory(outSubDir)
      val outFile = outSubDir / s"$objectName.scala"

      log.info(s"[sbt-meltc] Compiling ${ meltFile.getName } → ${ outFile.getName }")

      val javaArgs = Seq(
        "-cp",
        cpStr,
        "meltc.MeltcMain",
        meltFile.getAbsolutePath,
        outFile.getAbsolutePath,
        objectName,
        fullPkg,
        "--mode",
        normalisedMode
      ) ++ (if (hydration) Seq("--hydration") else Seq.empty)

      val exitCode = Fork.java(ForkOptions(), javaArgs)

      if (exitCode == 0) {
        log.info(s"[sbt-meltc] Generated ${ outFile.getAbsolutePath }")
        Seq(outFile)
      } else {
        log.error(s"[sbt-meltc] Compilation failed for ${ meltFile.getName } (exit $exitCode)")
        Seq.empty
      }
    }
  }
}
