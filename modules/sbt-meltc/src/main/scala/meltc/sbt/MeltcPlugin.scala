/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.sbt

import sbt._
import sbt.Keys._

import org.scalajs.linker.interface.Report
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.{
  fastLinkJS,
  fullLinkJS,
  scalaJSLinkerOutputDirectory
}

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

    // ── Asset manifest auto-generation (§C12) ────────────────────────────

    /** Client sub-project whose Scala.js `fastLinkJS` public modules
      * drive the auto-generated `AssetManifest` object.
      *
      * Set this on the server project that serves the client's chunks.
      * The plugin will add a `Compile / sourceGenerators` task that:
      *
      *   1. Takes a `.value` dependency on
      *      `(clientProject / Compile / fastLinkJS)` so sbt rebuilds the
      *      client whenever the server needs to be compiled.
      *   2. Reads the resulting `Report.publicModules` and writes a
      *      Scala source exposing both a `ViteManifest` and the
      *      absolute `clientDistDir: File` path.
      *
      * Default: `None` — no manifest is generated, the project is
      * treated as a regular Melt server with no hydration client.
      *
      * Typical setup:
      * {{{
      * lazy val `my-client` = project.enablePlugins(ScalaJSPlugin, MeltcPlugin)
      * lazy val `my-server` = project
      *   .enablePlugins(MeltcPlugin)
      *   .settings(meltcAssetManifestClient := Some(`my-client`))
      *   .dependsOn(`my-components`.jvm)
      * }}}
      */
    val meltcAssetManifestClient =
      settingKey[Option[Project]](
        "Client sub-project whose fastLinkJS output drives AssetManifest generation"
      )

    /** Package of the generated asset manifest object.
      * Default: `"generated"`.
      */
    val meltcAssetManifestPackage =
      settingKey[String]("Package for the auto-generated AssetManifest object")

    /** Object name of the generated asset manifest.
      * Default: `"AssetManifest"`.
      */
    val meltcAssetManifestObject =
      settingKey[String]("Object name for the auto-generated AssetManifest")

    /** The generator task itself — exposed so advanced users can
      * customise invocation or inspect the output path.
      */
    val meltcAssetManifestGenerate =
      taskKey[Seq[File]]("Generate AssetManifest.scala from the client's fastLinkJS Report")

    // ── Vite integration (Tier 2) ──────────────────────────────────────

    /** When `true`, the asset manifest is generated from a real Vite
      * `manifest.json` (produced by `vite build`) instead of being
      * synthesised from `fastLinkJS` public modules. This switches the
      * generated `AssetManifest` to use hashed filenames and changes
      * `clientDistDir` to point at the Vite `dist/` directory.
      *
      * Default: `false` (reads `sys.env("MELT_PROD")` as a fallback).
      */
    val meltcProd =
      settingKey[Boolean]("Enable production mode (Vite manifest)")

    /** Filesystem path to the Vite `manifest.json` output. Only used
      * when [[meltcProd]] is `true`.
      *
      * Default: `examples/http4s-ssr/dist/.vite/manifest.json` (relative
      * to `baseDirectory`). Override for non-standard Vite `outDir`.
      */
    val meltcViteManifestPath =
      settingKey[File]("Path to the Vite manifest.json file")

    /** Filesystem path to the Vite `dist/` output directory. Only used
      * when [[meltcProd]] is `true`. Becomes `clientDistDir` in the
      * generated `AssetManifest`.
      *
      * Default: sibling of `meltcViteManifestPath` (`dist/`).
      */
    val meltcViteDistDir =
      settingKey[File]("Path to the Vite dist directory")

    /** Generates a `vite-inputs.json` file from the client's
      * `fullLinkJS` output. This JSON file is read by `vite.config.js`
      * as `rollupOptions.input` so that adding or removing a `.melt`
      * component automatically updates the Vite build without editing
      * any config files.
      */
    val meltcViteInputGenerate =
      taskKey[File]("Generate vite-inputs.json from the client's fullLinkJS Report")
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
    meltcHydration         := false,
    meltcSourceDirectory   := (Compile / sourceDirectory).value / "scala",
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
    Compile / sourceGenerators += meltcGenerate.taskValue,

    // ── Asset manifest generation (§C12) ─────────────────────────────────
    meltcAssetManifestClient  := None,
    meltcAssetManifestPackage := "generated",
    meltcAssetManifestObject  := "AssetManifest",

    // ── Vite integration defaults (Tier 2) ────────────────────────────
    meltcProd := sys.env.get("MELT_PROD").exists(v => v == "true" || v == "1"),
    meltcViteManifestPath := baseDirectory.value / ".." / "dist" / ".vite" / "manifest.json",
    meltcViteDistDir      := baseDirectory.value / ".." / "dist",

    meltcViteInputGenerate := Def.taskDyn {
      meltcAssetManifestClient.value match {
        case Some(clientProject) =>
          Def.task {
            generateViteInputs(
              streams = streams.value,
              report  = (clientProject / Compile / fullLinkJS).value.data,
              distDir = (clientProject / Compile / fullLinkJS / scalaJSLinkerOutputDirectory).value,
              outFile = (clientProject / baseDirectory).value / "target" / "vite-inputs.json"
            )
          }
        case None =>
          Def.task(file(""))
      }
    }.value,

    meltcAssetManifestGenerate := Def.taskDyn {
      meltcAssetManifestClient.value match {
        case Some(clientProject) if meltcProd.value =>
          // Prod mode: read the real Vite manifest.json.
          Def.task {
            generateAssetManifestFromVite(
              streams       = streams.value,
              outDir        = (Compile / sourceManaged).value / "generated",
              pkgName       = meltcAssetManifestPackage.value,
              objectName    = meltcAssetManifestObject.value,
              manifestPath  = meltcViteManifestPath.value,
              distDir       = meltcViteDistDir.value
            )
          }
        case Some(clientProject) =>
          // Dev mode: synthesise from fastLinkJS public modules.
          Def.task {
            generateAssetManifest(
              streams    = streams.value,
              outDir     = (Compile / sourceManaged).value / "generated",
              pkgName    = meltcAssetManifestPackage.value,
              objectName = meltcAssetManifestObject.value,
              report     = (clientProject / Compile / fastLinkJS).value.data,
              distDir    = (clientProject / Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value
            )
          }
        case None =>
          Def.task(Seq.empty[File])
      }
    }.value,
    Compile / sourceGenerators += meltcAssetManifestGenerate.taskValue
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
      case other         =>
        log.warn(s"[sbt-meltc] unknown meltcMode '$other' — falling back to 'spa'")
        "spa"
    }

    meltFilesWithRoot.flatMap {
      case (meltFile, srcDir) =>
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

  // ── Asset manifest generation helper (§C12) ─────────────────────────────

  /** Writes a `generated.AssetManifest` Scala source that exposes the
    * client project's Scala.js `fastLinkJS` output as a
    * [[melt.runtime.ssr.ViteManifest]] plus the absolute filesystem
    * path of the fastopt output directory. Regenerated on every
    * compile so adding or removing a `.melt` component requires zero
    * edits to this file.
    */
  private def generateAssetManifest(
    streams:    TaskStreams,
    outDir:     File,
    pkgName:    String,
    objectName: String,
    report:     Report,
    distDir:    File
  ): Seq[File] = {
    val log = streams.log
    IO.createDirectory(outDir)
    val outFile = outDir / s"$objectName.scala"

    // `Report.publicModules` is a Set, so sort by moduleID for stable
    // output — otherwise the generated file churns between compiles.
    val sortedModules = report.publicModules.toList.sortBy(_.moduleID)

    val entriesSrc = sortedModules
      .map { m =>
        s"""    "scalajs:${ m.moduleID }.js" -> ViteManifest.Entry(file = "${ m.jsFileName }")"""
      }
      .mkString(",\n")

    // Embed the absolute path so the server can locate the fastopt
    // directory without any runtime configuration. Backslashes (Windows)
    // are escaped for the Scala string literal.
    val distPathLit = distDir.getAbsolutePath.replace("\\", "\\\\")

    val code =
      s"""package $pkgName
         |
         |import java.io.File
         |import melt.runtime.ssr.ViteManifest
         |
         |/** Auto-generated by sbt-meltc — do not edit.
         |  *
         |  * Maps every `@JSExportTopLevel("hydrate", moduleID = ...)` from
         |  * the client project's Scala.js `fastLinkJS` output to its
         |  * emitted chunk file name. `clientDistDir` is the absolute path
         |  * of the fastopt directory, for use with http4s `fileService`
         |  * (or equivalent static content handlers in other servers).
         |  *
         |  * Regenerated automatically on every `sbt compile` — add or
         |  * remove a `.melt` file and this object will re-flow with no
         |  * manual edits.
         |  */
         |object $objectName {
         |  val manifest: ViteManifest = ViteManifest.fromEntries(Map(
         |$entriesSrc
         |  ))
         |
         |  val clientDistDir: File = new File("$distPathLit")
         |}
         |""".stripMargin

    IO.write(outFile, code)
    log.info(
      s"[sbt-meltc] regenerated ${ outFile.getName } with ${ sortedModules.size } public modules"
    )
    Seq(outFile)
  }

  // ── Vite integration helpers (Tier 2) ────────────────────────────────

  /** Writes a `vite-inputs.json` file that maps each Scala.js public
    * module's moduleID to its absolute filesystem path. `vite.config.js`
    * reads this as `rollupOptions.input` so adding or removing a `.melt`
    * component automatically updates the Vite build.
    *
    * Keys are plain moduleIDs (e.g. `"home"`, `"todos"`). Colons are
    * NOT used because Rollup treats colon-containing keys as non-entry
    * chunks and strips their exports — causing `hydrate is not a
    * function` errors in the browser.
    */
  private def generateViteInputs(
    streams: TaskStreams,
    report:  Report,
    distDir: File,
    outFile: File
  ): File = {
    val log = streams.log
    val sortedModules = report.publicModules.toList.sortBy(_.moduleID)

    // Build a JSON object: { "home": "/abs/path/to/home.js", ... }
    val entries = sortedModules.map { m =>
      val absPath = (distDir / m.jsFileName).getAbsolutePath
        .replace("\\", "/")  // normalise for JSON
      s"""  "${ m.moduleID }": "$absPath""""
    }
    val json = entries.mkString("{\n", ",\n", "\n}\n")

    IO.write(outFile, json)
    log.info(
      s"[sbt-meltc] wrote ${ outFile.getName } with ${ sortedModules.size } entries"
    )
    outFile
  }

  /** Writes a `generated.AssetManifest` Scala source that loads the
    * Vite-produced `manifest.json` at startup. Used in production mode
    * when `meltcProd := true` (or `MELT_PROD=true`).
    *
    * Unlike the dev-mode generator which embeds entries inline, this
    * version calls `ViteManifest.load(path)` so the Scala source stays
    * tiny and the hashed filenames come from the actual Vite output.
    */
  private def generateAssetManifestFromVite(
    streams:      TaskStreams,
    outDir:       File,
    pkgName:      String,
    objectName:   String,
    manifestPath: File,
    distDir:      File
  ): Seq[File] = {
    val log = streams.log

    if (!manifestPath.exists()) {
      log.error(
        s"[sbt-meltc] Vite manifest not found at ${ manifestPath.getAbsolutePath }. " +
        "Run `npx vite build` in the example directory first."
      )
      return Seq.empty
    }

    IO.createDirectory(outDir)
    val outFile = outDir / s"$objectName.scala"

    val manifestPathLit = manifestPath.getAbsolutePath.replace("\\", "\\\\")
    val distPathLit     = distDir.getAbsolutePath.replace("\\", "\\\\")

    val code =
      s"""package $pkgName
         |
         |import java.io.File
         |import melt.runtime.ssr.ViteManifest
         |
         |/** Auto-generated by sbt-meltc (prod mode) — do not edit.
         |  *
         |  * Loads the Vite-produced `manifest.json` at application startup.
         |  * All chunk filenames are content-hashed by Vite, enabling
         |  * `Cache-Control: immutable` on production deployments.
         |  *
         |  * Regenerated when `MELT_PROD=true sbt compile` is run.
         |  */
         |object $objectName {
         |  val manifest: ViteManifest =
         |    ViteManifest.load("$manifestPathLit")
         |
         |  val clientDistDir: File = new File("$distPathLit")
         |}
         |""".stripMargin

    IO.write(outFile, code)
    log.info(
      s"[sbt-meltc] regenerated ${ outFile.getName } (prod mode, Vite manifest)"
    )
    Seq(outFile)
  }
}
