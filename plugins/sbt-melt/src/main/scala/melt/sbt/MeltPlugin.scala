/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.sbt

import java.util.Optional

import sbt._
import sbt.Keys._

/** sbt-melt plugin
  *
  * Detects `.melt` files under [[melt.sbt.MeltPlugin.autoImport.meltSourceDirectories]] and
  * compiles each one to a `.scala` file via a forked JVM process running `melt.MeltMain`.
  *
  * == Setup ==
  *
  * Enable the plugin and publish `sbt-melt` locally first:
  * {{{
  * // In your build.sbt:
  * enablePlugins(MeltPlugin)
  * meltPackage := "components"
  * }}}
  *
  * {{{
  * // One-time in the melt monorepo:
  * sbt compilerJVM/publishLocal runtimeJVM/publishLocal codegenJVM/publishLocal sbt-melt/publishLocal
  * }}}
  *
  * The plugin resolves `melt-codegen` (including all transitive deps such as
  * `melt-compiler`, `melt-runtime`, and `scala3-library`) using its own Ivy configuration
  * `melt-compiler`, so you do not need to configure the classpath manually.
  *
  * To skip `publishLocal` in a monorepo, override the compiler classpath directly:
  * {{{
  * meltCompilerClasspath := (codegen.jvm / Compile / fullClasspath).value.files
  * }}}
  */
object MeltPlugin extends AutoPlugin {

  override def trigger  = noTrigger
  override def requires = plugins.JvmPlugin

  /** Internal Ivy configuration used to resolve the melt compiler and its
    * transitive runtime dependencies (including `scala3-library`).
    */
  private val MeltCompilerConfig = config("melt-compiler").hide

  object autoImport {

    /** Enable emission of `@JSExportTopLevel("hydrate", moduleID = ...)`
      * hydration entries in SPA-mode generated code.
      *
      * Default: `false`. Existing Scala.js-only examples keep the
      * single-module CommonJSModule link config (the current default)
      * and do not need the per-component public modules.
      *
      * Set to `true` on projects that actually do SSR + client-side
      * hydration (Approach B: per-component Islands hydration). Those
      * projects typically also configure `scalaJSLinkerConfig` with
      * `ModuleKind.ESModule` and a small-modules split style so each
      * component ends up in its own public chunk.
      *
      * For full-page hydration (Approach A), use
      * [[meltHydrationRoot]] instead. The two settings are mutually
      * exclusive: `meltHydrationRoot` takes precedence when set.
      */
    val meltHydration =
      settingKey[Boolean]("Emit @JSExportTopLevel hydration entries in SPA codegen")

    /** Root component name for full-page hydration (Approach A).
      *
      * When set to `Some("App")` (or another component name), only the
      * named component receives a `@JSExportTopLevel("hydrate")` entry.
      * All other `.melt` files are compiled without a hydration export,
      * keeping them as internal Scala.js chunks.
      *
      * One bootstrap `<script>` mounts the entire component tree from
      * the server-rendered HTML markers.
      *
      * Default: `None` — falls back to [[meltHydration]] behaviour.
      *
      * {{{
      * // build.sbt (JS platform of a crossProject):
      * meltHydrationRoot := Some("App"),
      * scalaJSLinkerConfig ~= {
      *   _.withModuleKind(ModuleKind.ESModule)
      *     .withModuleSplitStyle(ModuleSplitStyle.SmallModulesFor(List("components")))
      * }
      * }}}
      */
    val meltHydrationRoot =
      settingKey[Option[String]](
        "Root component name for full-page hydration (Approach A). " +
          "When set, only this component emits a @JSExportTopLevel hydration entry."
      )

    /** All directories to scan for `.melt` source files.
      *
      * Default: `Compile / unmanagedSourceDirectories` — this picks up both
      * the platform-specific source root (e.g. `jvm/src/main/scala`) and the
      * shared source root (e.g. `shared/src/main/scala`) automatically when
      * the project is a `crossProject` member.
      */
    val meltSourceDirectories =
      settingKey[Seq[File]]("Directories containing .melt source files (crossProject-aware)")

    /** Directory where generated `.scala` files are written.
      * Default: `target/scala-x.y/src_managed/melt`
      */
    val meltOutputDirectory =
      settingKey[File]("Directory for generated .scala files")

    /** Scala package placed at the top of every generated file.
      * Default: `"components"`
      */
    val meltPackage =
      settingKey[String]("Package for generated Scala files")

    /** Full classpath (jar files) used when forking the melt JVM compiler.
      *
      * By default this is resolved automatically using the `melt-compiler` Ivy configuration.
      * Override to point at the melt JVM output directly
      * (e.g. `(compilerJVM / Compile / fullClasspath).value.files`).
      */
    val meltCompilerClasspath =
      taskKey[Seq[File]]("Classpath for the melt compiler JVM process")

    /** Compiles all `.melt` files and returns the generated `.scala` files. */
    val meltGenerate =
      taskKey[Seq[File]]("Compile .melt files to .scala files")

    /** Codegen mode passed to `melt.MeltMain`.
      *
      * Valid values: `"spa"`, `"ssr"`, `"auto"`.
      *
      * Default: `"auto"` — selects `"spa"` when `ScalaJSPlugin` is enabled,
      * otherwise `"ssr"`.
      *
      * `MeltkitPlugin` overrides this based on `meltMode`.
      */
    val meltCodegenMode =
      settingKey[String]("Codegen mode: spa, ssr, or auto (default)")

    /** Class name of the [[melt.css.StylePreprocessor]] implementation to use
      * for stylesheet preprocessing in `.melt` files.
      *
      * Default: `None` — plain CSS only. Setting `<style lang="scss">` will
      * result in a compile error with a hint to set this key.
      *
      * Use the predefined constants from this plugin's `autoImport` to refer
      * to known implementations:
      *
      * {{{
      * // SCSS via Dart Sass:
      * meltStylePreprocessor := Some(SassPreprocessor)
      * }}}
      *
      * The implementation class must be a Scala `object` that extends
      * [[melt.css.StylePreprocessor]] and its JAR must be present on
      * [[meltCompilerClasspath]]. For known preprocessors (e.g. [[SassPreprocessor]])
      * the plugin adds the required JAR automatically.
      *
      * For a custom preprocessor, add the JAR to [[meltCompilerClasspath]]
      * manually and pass the fully-qualified object name:
      * {{{
      * meltStylePreprocessor := Some("com.example.MyPreprocessor")
      * }}}
      */
    val meltStylePreprocessor =
      settingKey[Option[String]](
        "Fully-qualified object name of the StylePreprocessor to use. " +
          "Known values: SassPreprocessor."
      )

    /** Preprocessor constant for SCSS support via Dart Sass.
      *
      * Requires the `melt-compiler-sass` artifact on the compiler classpath.
      * When [[meltStylePreprocessor]] is set to `Some(SassPreprocessor)`, the plugin
      * adds `melt-compiler-css` and `melt-compiler-sass` to the compiler classpath automatically.
      *
      * {{{
      * meltStylePreprocessor := Some(SassPreprocessor)
      * }}}
      */
    val SassPreprocessor: String = "melt.sass.SassPreprocessor"

  }

  import autoImport._

  private val pluginVersion: String = sys.props.getOrElse("plugin.version", "0.1.0-SNAPSHOT")

  /** Class name of Scala.js's sbt plugin. We check for this by string rather
    * than importing the class, so sbt-melt does not need to declare a hard
    * dependency on sbt-scalajs.
    */
  private val ScalaJSPluginClassName = "org.scalajs.sbtplugin.ScalaJSPlugin$"

  /** Returns `true` iff the resolved project has `ScalaJSPlugin` enabled. */
  private def hasScalaJSPlugin(project: sbt.ResolvedProject): Boolean =
    project.autoPlugins.exists(_.getClass.getName == ScalaJSPluginClassName)

  override def projectSettings: Seq[Setting[_]] = Seq(
    meltHydration         := false,
    meltHydrationRoot     := None,
    meltStylePreprocessor := None,
    meltCodegenMode       := "auto",
    meltSourceDirectories := (Compile / unmanagedSourceDirectories).value,
    meltOutputDirectory   := (Compile / sourceManaged).value / "melt",
    meltPackage           := "",

    ivyConfigurations += MeltCompilerConfig,
    libraryDependencies += ("io.github.takapi327" % "melt-codegen_3" % pluginVersion cross CrossVersion.disabled) % MeltCompilerConfig,
    libraryDependencies ++= {
      meltStylePreprocessor.value match {
        case Some(cls) if cls == SassPreprocessor =>
          Seq(
            ("io.github.takapi327" % "melt-compiler-css_3" % pluginVersion cross CrossVersion.disabled) % MeltCompilerConfig,
            ("io.github.takapi327" % "melt-compiler-sass_3" % pluginVersion cross CrossVersion.disabled) % MeltCompilerConfig
          )
        case _ => Seq.empty
      }
    },
    meltCompilerClasspath := update.value.select(
      configurationFilter(MeltCompilerConfig.name)
    ),

    meltGenerate := compileMeltFiles(
      streams = streams.value,
      srcDirs = meltSourceDirectories.value,
      outDir  = meltOutputDirectory.value,
      pkg     = meltPackage.value,
      mode    = meltCodegenMode.value match {
        case "spa" => "spa"
        case "ssr" => "ssr"
        case _     => if (hasScalaJSPlugin(thisProject.value)) "spa" else "ssr"
      },
      hydration     = meltHydration.value,
      hydrationRoot = meltHydrationRoot.value,
      preprocessor  = meltStylePreprocessor.value,
      compilerCp    = meltCompilerClasspath.value,
      reporter      = (Compile / compile / bspReporter).value
    ),
    Compile / sourceGenerators += meltGenerate.taskValue,

    // ── Source position mapping ───────────────────────────────────────────────
    // Remaps scalac error positions from generated `.scala` files back to the
    // original `.melt` source files using the `-- MELT GENERATED --` comment
    // block that melt appends to every generated file.
    Compile / sourcePositionMappers += { pos => MeltSourceMap.positionMapper(pos) },
    Test / sourcePositionMappers += { pos => MeltSourceMap.positionMapper(pos) }
  )

  private def compileMeltFiles(
    streams:       TaskStreams,
    srcDirs:       Seq[File],
    outDir:        File,
    pkg:           String,
    mode:          String,
    hydration:     Boolean,
    hydrationRoot: Option[String],
    preprocessor:  Option[String],
    compilerCp:    Seq[File],
    reporter:      xsbti.Reporter
  ): Seq[File] = {
    val log = streams.log

    if (compilerCp.isEmpty) {
      log.warn(
        "[sbt-melt] meltCompilerClasspath is empty — no .melt files will be compiled.\n" +
          "  Run `sbt compilerJVM/publishLocal` in the melt monorepo first."
      )
      return Seq.empty
    }

    IO.createDirectory(outDir)

    val meltFilesWithRoot: Seq[(File, File)] =
      srcDirs.filter(_.exists).flatMap { srcDir =>
        (srcDir ** "*.melt").get.map(f => (f, srcDir))
      }

    if (meltFilesWithRoot.isEmpty) {
      log.debug(s"[sbt-melt] No .melt files found under ${ srcDirs.mkString(", ") }")
      return Seq.empty
    }

    val cpStr = compilerCp.map(_.getAbsolutePath).mkString(java.io.File.pathSeparator)

    val normalisedMode = mode.toLowerCase

    meltFilesWithRoot.flatMap {
      case (meltFile, srcDir) =>
        val objectName = meltFile.base.head.toUpper + meltFile.base.tail

        val subPkg = IO
          .relativize(srcDir, meltFile.getParentFile)
          .map(_.replace(java.io.File.separatorChar, '.'))
          .getOrElse("")
        val fullPkg = (pkg, subPkg) match {
          case (p, "") => p
          case ("", s) => s
          case (p, s)  => s"$p.$s"
        }

        val outSubDir = IO
          .relativize(srcDir, meltFile.getParentFile)
          .map(rel => new java.io.File(outDir, rel))
          .getOrElse(outDir)
        IO.createDirectory(outSubDir)
        val outFile  = outSubDir / s"$objectName.scala"
        val diagFile = new File(outFile.getAbsolutePath + ".diag")

        // ── Incremental compilation cache ─────────────────────────────────
        // Use a per-file cache directory keyed on the output file's relative
        // path so that two components with the same base name in different
        // sub-packages each get an independent cache entry.
        //
        // A compiler fingerprint is also included in the directory name so
        // that updating melt (either via publishLocal or by recompiling in
        // the monorepo) automatically invalidates all cached generated files.
        // For class directories the newest direct-child modification time is
        // used as a proxy for "has the compiler changed?".
        val relPath = IO.relativize(outDir, outFile).getOrElse(outFile.getName)
        val safeKey = relPath.replace(java.io.File.separatorChar, '_').replace('.', '_')
        val cpFingerprint: String = {
          def stamp(f: File): Long =
            if (f.isDirectory) {
              val children = Option(f.listFiles()).toSeq.flatten
              if (children.isEmpty) f.lastModified
              else children.map(stamp).max
            } else
              f.lastModified
          val raw = compilerCp
            .sortBy(_.getAbsolutePath)
            .map(f => s"${ f.getName }:${ stamp(f) }")
            .mkString("|")
          java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(raw.getBytes("UTF-8"))
            .take(8)
            .map(b => "%02x".format(b & 0xff))
            .mkString
        }
        val cacheDir = streams.cacheDirectory / "melt" / safeKey / cpFingerprint

        val cachedCompile = FileFunction.cached(cacheDir, FilesInfo.hash, FilesInfo.exists) { (_: Set[File]) =>
          log.info(s"[sbt-melt] Compiling ${ meltFile.getName } → ${ outFile.getName }")

          // Determine whether this component gets a hydration entry:
          //   - Approach A (meltHydrationRoot set): only the named root component
          //   - Approach B (meltHydration := true): all components
          val emitHydration = hydrationRoot match {
            case Some(root) => objectName == root
            case None       => hydration
          }

          val javaArgs = Seq(
            "-cp",
            cpStr,
            "melt.MeltMain",
            meltFile.getAbsolutePath,
            outFile.getAbsolutePath,
            objectName,
            fullPkg,
            "--mode",
            normalisedMode
          ) ++ (if (emitHydration) Seq("--hydration") else Seq.empty) ++
            preprocessor.toSeq.flatMap(cls => Seq("--preprocessor", cls))

          val exitCode = Fork.java(ForkOptions(), javaArgs)

          // ── Read structured diagnostics written by MeltcMain ──────────
          val (errors, warnings) = readDiagnostics(diagFile)
          IO.delete(diagFile)

          // Report diagnostics via BSP reporter (IDE integration + sbt terminal)
          warnings.foreach {
            case (path, lineNum, col, msg) =>
              try reporter.log(mkProblem(path, lineNum, col, msg, xsbti.Severity.Warn))
              catch { case _: Throwable => log.warn(s"melt warning: ${ new File(path).getName }:$lineNum: $msg") }
          }

          if (exitCode != 0) {
            errors.foreach {
              case (path, lineNum, col, msg) =>
                try reporter.log(mkProblem(path, lineNum, col, msg, xsbti.Severity.Error))
                catch { case _: Throwable => log.error(s"melt error: ${ new File(path).getName }:$lineNum: $msg") }
            }
            throw new MessageOnlyException(
              s"[sbt-melt] ${ meltFile.getName } failed to compile — see errors above"
            )
          }

          log.info(s"[sbt-melt] Generated ${ outFile.getAbsolutePath }")
          Set(outFile)
        }

        cachedCompile(Set(meltFile)).toSeq
    }
  }

  /** Reads the structured `.diag` file written by `MeltcMain`.
    *
    * Each line is tab-separated: `severity\tabsPath\tline\tcol\tmessage`
    * where severity is `E` (error) or `W` (warning).
    *
    * Returns `(errors, warnings)` as `(absPath, line, col, message)` tuples.
    */
  private def readDiagnostics(
    diagFile: File
  ): (List[(String, Int, Int, String)], List[(String, Int, Int, String)]) = {
    if (!diagFile.exists()) return (Nil, Nil)
    def parseInt(s: String): Int = try s.toInt
    catch { case _: NumberFormatException => 0 }
    def parseLine(line: String): Option[(String, Int, Int, String)] = {
      val parts = line.split("\t", 5)
      if (parts.length >= 5) Some((parts(1), parseInt(parts(2)), parseInt(parts(3)), parts(4)))
      else None
    }
    val lines    = IO.readLines(diagFile)
    val errors   = lines.filter(_.startsWith("E\t")).flatMap(parseLine)
    val warnings = lines.filter(_.startsWith("W\t")).flatMap(parseLine)
    (errors, warnings)
  }

  /** Creates an `xsbti.Position` pointing at a location in a `.melt` file. */
  private def mkPosition(absPath: String, lineNum: Int): xsbti.Position =
    new xsbti.Position {
      override def line(): Optional[Integer] =
        if (lineNum > 0) Optional.of(lineNum.asInstanceOf[Integer]) else Optional.empty()
      override def lineContent():  String                 = ""
      override def offset():       Optional[Integer]      = Optional.empty()
      override def pointer():      Optional[Integer]      = Optional.empty()
      override def pointerSpace(): Optional[String]       = Optional.empty()
      override def sourcePath():   Optional[String]       = Optional.of(absPath)
      override def sourceFile():   Optional[java.io.File] =
        Optional.of(new java.io.File(absPath))
    }

  /** Creates an `xsbti.Problem` suitable for reporting to the BSP/IDE reporter. */
  private def mkProblem(
    absPath: String,
    lineNum: Int,
    col:     Int,
    msg:     String,
    sev:     xsbti.Severity
  ): xsbti.Problem =
    new xsbti.Problem {
      override def category(): String         = "melt"
      override def severity(): xsbti.Severity = sev
      override def message():  String         = msg
      override def position(): xsbti.Position = mkPosition(absPath, lineNum)
    }
}
