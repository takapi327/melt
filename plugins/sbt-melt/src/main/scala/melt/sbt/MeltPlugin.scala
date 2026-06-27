/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.sbt

import java.util.Optional

import sbt.{ given, * }
import sbt.Keys.*

import melt.{ CompileMode, MeltCompiler }
import melt.preprocessor.StylePreprocessor

/** sbt-melt plugin
  *
  * Detects `.melt` files under [[melt.sbt.MeltPlugin.autoImport.meltSourceDirectories]] and
  * compiles each one to a `.scala` file by calling [[melt.MeltCompiler]] directly in-process.
  *
  * == Setup ==
  *
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
  */
object MeltPlugin extends AutoPlugin:

  override def trigger  = noTrigger
  override def requires = plugins.JvmPlugin

  object autoImport:

    val meltHydration =
      settingKey[Boolean]("Emit @JSExportTopLevel hydration entries in SPA codegen")

    val meltHydrationRoot =
      settingKey[Option[String]](
        "Root component name for full-page hydration (Approach A). " +
          "When set, only this component emits a @JSExportTopLevel hydration entry."
      )

    val meltSourceDirectories =
      settingKey[Seq[File]]("Directories containing .melt source files (crossProject-aware)")

    val meltOutputDirectory =
      settingKey[File]("Directory for generated .scala files")

    val meltPackage =
      settingKey[String]("Package for generated Scala files")

    @transient val meltGenerate =
      taskKey[Seq[File]]("Compile .melt files to .scala files")

    val meltCodegenMode =
      settingKey[String]("Codegen mode: spa, ssr, or auto (default)")

    val meltStylePreprocessor =
      settingKey[Option[StylePreprocessor]](
        "StylePreprocessor to use for stylesheet preprocessing in .melt files. " +
          "Default: None (plain CSS only). Known values: SassPreprocessor."
      )

    val SassPreprocessor: StylePreprocessor = melt.sass.SassPreprocessor

  import autoImport.*

  private val pluginVersion: String = sys.props.getOrElse("plugin.version", "0.1.0-SNAPSHOT")

  private val ScalaJSPluginClassName = "org.scalajs.sbtplugin.ScalaJSPlugin$"

  private def hasScalaJSPlugin(project: sbt.ResolvedProject): Boolean =
    project.autoPlugins.exists(_.getClass.getName == ScalaJSPluginClassName)

  override def projectSettings: Seq[Setting[?]] = Seq(
    meltHydration         := false,
    meltHydrationRoot     := None,
    meltStylePreprocessor := None,
    meltCodegenMode       := "auto",
    meltSourceDirectories := (Compile / unmanagedSourceDirectories).value,
    meltOutputDirectory   := (Compile / sourceManaged).value / "melt",
    meltPackage           := "",

    libraryDependencies += {
      val v    = pluginVersion
      val binV = scalaBinaryVersion.value
      if hasScalaJSPlugin(thisProject.value) then "io.github.takapi327" % s"melt-runtime_sjs1_$binV" % v
      else "io.github.takapi327"                                       %% "melt-runtime"             % v
    },

    meltGenerate := compileMeltFiles(
      streams = streams.value,
      srcDirs = meltSourceDirectories.value,
      outDir  = meltOutputDirectory.value,
      pkg     = meltPackage.value,
      mode    = meltCodegenMode.value match
        case "spa" => "spa"
        case "ssr" => "ssr"
        case _     => if hasScalaJSPlugin(thisProject.value) then "spa" else "ssr"
      ,
      hydration     = meltHydration.value,
      hydrationRoot = meltHydrationRoot.value,
      preprocessor  = meltStylePreprocessor.value,
      reporter      = (Compile / compile / bspReporter).value
    ),
    Compile / sourceGenerators += meltGenerate.taskValue,

    Compile / sourcePositionMappers += Def.uncached((pos: xsbti.Position) => MeltSourceMap.positionMapper(pos)),
    Test / sourcePositionMappers += Def.uncached((pos: xsbti.Position) => MeltSourceMap.positionMapper(pos))
  )

  private def compileMeltFiles(
    streams:       TaskStreams,
    srcDirs:       Seq[File],
    outDir:        File,
    pkg:           String,
    mode:          String,
    hydration:     Boolean,
    hydrationRoot: Option[String],
    preprocessor:  Option[StylePreprocessor],
    reporter:      xsbti.Reporter
  ): Seq[File] =
    val log = streams.log

    IO.createDirectory(outDir)

    val meltFilesWithRoot: Seq[(File, File)] =
      srcDirs.filter(_.exists).flatMap { srcDir =>
        (srcDir ** "*.melt").get().map(f => (f, srcDir))
      }

    if meltFilesWithRoot.isEmpty then
      log.debug(s"[sbt-melt] No .melt files found under ${ srcDirs.mkString(", ") }")
      return Seq.empty

    val compileMode       = if mode.toLowerCase == "ssr" then CompileMode.SSR else CompileMode.SPA
    val stylePreprocessor = preprocessor.getOrElse(StylePreprocessor.cssOnly)

    meltFilesWithRoot.flatMap {
      case (meltFile, srcDir) =>
        val objectName = s"${ meltFile.base.head.toUpper }${ meltFile.base.tail }"

        val subPkg = IO
          .relativize(srcDir, meltFile.getParentFile)
          .map(_.replace(java.io.File.separatorChar, '.'))
          .getOrElse("")
        val fullPkg = (pkg, subPkg) match
          case (p, "") => p
          case ("", s) => s
          case (p, s)  => s"$p.$s"

        val outSubDir = IO
          .relativize(srcDir, meltFile.getParentFile)
          .map(rel => new java.io.File(outDir, rel))
          .getOrElse(outDir)
        IO.createDirectory(outSubDir)
        val outFile = outSubDir / s"$objectName.scala"

        val relPath  = IO.relativize(outDir, outFile).getOrElse(outFile.getName)
        val safeKey  = relPath.replace(java.io.File.separatorChar, '_').replace('.', '_')
        val cacheDir = streams.cacheDirectory / "melt" / safeKey / pluginVersion

        val cachedCompile = FileFunction.cached(cacheDir, FilesInfo.hash, FilesInfo.exists) { (_: Set[File]) =>
          log.info(s"[sbt-melt] Compiling ${ meltFile.getName } → ${ outFile.getName }")

          val emitHydration = hydrationRoot match
            case Some(root) => objectName == root
            case None       => hydration

          val result = MeltCompiler.compile(
            source       = IO.read(meltFile),
            filename     = meltFile.getName,
            objectName   = objectName,
            pkg          = fullPkg,
            mode         = compileMode,
            hydration    = emitHydration,
            preprocessor = stylePreprocessor,
            sourcePath   = meltFile.getAbsolutePath
          )

          result.warnings.foreach { w =>
            try reporter.log(mkProblem(meltFile.getAbsolutePath, w.line, w.column, w.message, xsbti.Severity.Warn))
            catch case _: Throwable => log.warn(s"melt warning: ${ meltFile.getName }:${ w.line }: ${ w.message }")
          }

          if result.errors.nonEmpty then
            result.errors.foreach { e =>
              try reporter.log(mkProblem(meltFile.getAbsolutePath, e.line, e.column, e.message, xsbti.Severity.Error))
              catch case _: Throwable => log.error(s"melt error: ${ meltFile.getName }:${ e.line }: ${ e.message }")
            }
            throw new MessageOnlyException(
              s"[sbt-melt] ${ meltFile.getName } failed to compile — see errors above"
            )

          result.scalaCode match
            case None =>
              throw new MessageOnlyException(s"[sbt-melt] ${ meltFile.getName } produced no output")
            case Some(code) =>
              IO.write(outFile, code)
              log.info(s"[sbt-melt] Generated ${ outFile.getAbsolutePath }")
              Set(outFile)
        }

        cachedCompile(Set(meltFile)).toSeq
    }

  private def mkPosition(absPath: String, lineNum: Int): xsbti.Position =
    new xsbti.Position:
      override def line(): Optional[Integer] =
        if lineNum > 0 then Optional.of(lineNum.asInstanceOf[Integer]) else Optional.empty()
      override def lineContent():  String                 = ""
      override def offset():       Optional[Integer]      = Optional.empty()
      override def pointer():      Optional[Integer]      = Optional.empty()
      override def pointerSpace(): Optional[String]       = Optional.empty()
      override def sourcePath():   Optional[String]       = Optional.of(absPath)
      override def sourceFile():   Optional[java.io.File] =
        Optional.of(new java.io.File(absPath))

  private def mkProblem(
    absPath: String,
    lineNum: Int,
    col:     Int,
    msg:     String,
    sev:     xsbti.Severity
  ): xsbti.Problem =
    new xsbti.Problem:
      override def category(): String         = "melt"
      override def severity(): xsbti.Severity = sev
      override def message():  String         = msg
      override def position(): xsbti.Position = mkPosition(absPath, lineNum)
