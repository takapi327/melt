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

    val meltPublicEnv =
      settingKey[Map[String, String]](
        "Public (browser-safe) environment values. Each entry becomes a field of a " +
          "generated `PublicEnv` object compiled into this project (client + server). " +
          "Referencing an undeclared key is a compile error; never put secrets here — " +
          "these values are shipped to the browser."
      )

    val meltPublicEnvPackage =
      settingKey[String]("Package for the generated PublicEnv object. Default: \"generated\".")

    val SassPreprocessor: StylePreprocessor = melt.sass.SassPreprocessor

  import autoImport.*

  /** Resolves the user-facing `meltCodegenMode` setting to a concrete `"spa"` /
    * `"ssr"` codegen mode, or a `Left` error when the value is not one of the
    * recognised modes. `"auto"` selects `spa` for a Scala.js project and `ssr`
    * otherwise. Kept pure so it is unit-testable and so a typo fails the build
    * loudly instead of silently defaulting.
    */
  def resolveCodegenMode(raw: String, hasScalaJSPlugin: Boolean): Either[String, String] =
    raw.trim.toLowerCase match
      case "spa"  => Right("spa")
      case "ssr"  => Right("ssr")
      case "auto" => Right(if hasScalaJSPlugin then "spa" else "ssr")
      case _      =>
        Left(s"""invalid meltCodegenMode "$raw" — expected "spa", "ssr", or "auto"""")

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
    meltPublicEnv         := Map.empty,
    meltPublicEnvPackage  := "generated",

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
      mode    = resolveCodegenMode(meltCodegenMode.value, hasScalaJSPlugin(thisProject.value)) match
        case Right(m)  => m
        case Left(err) => throw new MessageOnlyException(s"[sbt-melt] $err")
      ,
      hydration     = meltHydration.value,
      hydrationRoot = meltHydrationRoot.value,
      preprocessor  = meltStylePreprocessor.value,
      reporter      = (Compile / compile / bspReporter).value
    ),
    Compile / sourceGenerators += meltGenerate.taskValue,

    Compile / sourceGenerators += Def.task {
      generatePublicEnv(
        outDir = (Compile / sourceManaged).value / "melt-env",
        pkg    = meltPublicEnvPackage.value,
        env    = meltPublicEnv.value
      )
    }.taskValue,

    Compile / sourcePositionMappers += Def.uncached((pos: xsbti.Position) => MeltSourceMap.positionMapper(pos)),
    Test / sourcePositionMappers += Def.uncached((pos: xsbti.Position) => MeltSourceMap.positionMapper(pos))
  )

  /** Generates a typed `PublicEnv` object rather than exposing a runtime `Map`, so an
    * undeclared key is a compile error and what ships to the browser is an explicit
    * whitelist. Emitted on the `.melt` project (compiled for both platforms) so a
    * dependent server inherits it instead of generating a clashing second copy. */
  private def generatePublicEnv(outDir: File, pkg: String, env: Map[String, String]): Seq[File] =
    if env.isEmpty then Seq.empty
    else
      IO.createDirectory(outDir)
      val pkgLine = if pkg.trim.isEmpty then "" else s"package ${ pkg.trim }\n\n"
      // sortBy keeps the output stable across runs so the change-guard below holds.
      val fields = env.toList
        .sortBy(_._1)
        .map { case (k, v) => s"""  val $k: String = "${ escapeScalaString(v) }"""" }
        .mkString("\n")
      val content =
        s"""$pkgLine/** Generated by sbt-melt from `meltPublicEnv`. Browser-safe public values.
           |  * Do NOT put secrets here — these fields are shipped to the client. */
           |object PublicEnv:
           |$fields
           |""".stripMargin
      val outFile = outDir / "PublicEnv.scala"
      // Write only on change: an identical rewrite bumps the mtime and forces a
      // needless recompile of everything that reads PublicEnv.
      if !outFile.exists || IO.read(outFile) != content then IO.write(outFile, content)
      Seq(outFile)

  private def escapeScalaString(s: String): String =
    s.flatMap {
      case '"'  => "\\\""
      case '\\' => "\\\\"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case c    => c.toString
    }

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
