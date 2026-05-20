/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.sbt

import sbt.*
import sbt.Keys.*

import org.scalajs.linker.interface.Report
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.{ fastLinkJS, fullLinkJS, scalaJSLinkerOutputDirectory }

/** Target platform / rendering mode for a Meltkit project.
  *
  * Setting [[MeltkitPlugin.autoImport.meltMode]] to one of these values causes
  * the plugin to automatically add the corresponding runtime dependency and
  * to select the appropriate codegen mode.
  */
sealed abstract class MeltMode
object MeltMode:

  /** Browser SPA — adds `meltkit-adapter-browser` (Scala.js). Codegen: `spa`. */
  case object Browser extends MeltMode

  /** Node.js SSR server — adds `meltkit-adapter-node` (Scala.js). Codegen: `ssr`. */
  case object Node extends MeltMode

  /** http4s SSR server (JVM / Node.js) — adds `meltkit-adapter-http4s`. Codegen: `ssr`. */
  case object Http4s extends MeltMode

/** sbt-meltkit plugin
  *
  * Integrates the Meltkit runtime into sbt projects. Requires [[meltc.sbt.MeltcPlugin]]
  * (enabled automatically). Adds runtime library dependencies, asset manifest generation,
  * and MeltKitConfig source generation based on [[meltMode]].
  *
  * == Setup ==
  *
  * {{{
  * enablePlugins(MeltkitPlugin)
  * meltMode    := Some(Browser)
  * meltcPackage := "components"
  * }}}
  */
object MeltkitPlugin extends AutoPlugin:

  override def trigger  = noTrigger
  override def requires = meltc.sbt.MeltcPlugin

  /** Class name of Scala.js's sbt plugin — checked by string to avoid a hard
    * compile-time dependency on sbt-scalajs in sbt-meltc.
    */
  private val ScalaJSPluginClassName = "org.scalajs.sbtplugin.ScalaJSPlugin$"

  /** Returns `true` iff the resolved project has `ScalaJSPlugin` enabled. */
  private def hasScalaJSPlugin(project: sbt.ResolvedProject): Boolean =
    project.autoPlugins.exists(_.getClass.getName == ScalaJSPluginClassName)

  object autoImport:

    /** Target platform mode.
      *
      * Setting this key causes the plugin to:
      *   - automatically add the appropriate runtime `libraryDependency`
      *     (when [[meltkitManageRuntimeDeps]] is `true`)
      *   - select the correct codegen mode (`spa` for [[Browser]], `ssr` otherwise)
      *
      * {{{
      * meltMode := Some(Browser)  // → meltkit-adapter-browser (Scala.js SPA)
      * meltMode := Some(Node)     // → meltkit-adapter-node (Node.js SSR)
      * meltMode := Some(Http4s)   // → meltkit-adapter-http4s (http4s SSR)
      * }}}
      *
      * Default: `None` — no runtime dependency is added automatically.
      */
    val meltMode = settingKey[Option[MeltMode]]("Target platform / rendering mode")

    /** When `true` (the default), the plugin automatically adds the runtime
      * library corresponding to [[meltMode]] to `libraryDependencies`.
      *
      * Set to `false` to manage the runtime dependency manually.
      */
    val meltkitManageRuntimeDeps =
      settingKey[Boolean]("Auto-add meltkit runtime dependency based on meltMode")

    // Convenience aliases so users can write `meltMode := Some(Browser)` without a prefix
    val Browser: MeltMode = MeltMode.Browser
    val Node:    MeltMode = MeltMode.Node
    val Http4s:  MeltMode = MeltMode.Http4s

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
      *      absolute `clientDistDir: fs2.io.file.Path` path.
      *
      * Default: `None` — no manifest is generated, the project is
      * treated as a regular Melt server with no hydration client.
      *
      * Typical setup:
      * {{{
      * lazy val `my-client` = project.enablePlugins(ScalaJSPlugin, MeltcPlugin)
      * lazy val `my-server` = project
      *   .enablePlugins(MeltkitPlugin)
      *   .settings(meltkitAssetManifestClient := Some(`my-client`))
      *   .dependsOn(`my-components`.jvm)
      * }}}
      */
    val meltkitAssetManifestClient =
      settingKey[Option[ProjectReference]](
        "Client sub-project whose fastLinkJS output drives AssetManifest generation"
      )

    /** Package of the generated asset manifest object.
      * Default: `"generated"`.
      */
    val meltkitAssetManifestPackage =
      settingKey[String]("Package for the auto-generated AssetManifest object")

    /** Object name of the generated asset manifest.
      * Default: `"AssetManifest"`.
      */
    val meltkitAssetManifestObject =
      settingKey[String]("Object name for the auto-generated AssetManifest")

    /** The generator task itself — exposed so advanced users can
      * customise invocation or inspect the output path.
      */
    val meltkitAssetManifestGenerate =
      taskKey[Seq[File]]("Generate AssetManifest.scala from the client's fastLinkJS Report")

    /** When `true`, the asset manifest is generated from a real Vite
      * `manifest.json` (produced by `vite build`) instead of being
      * synthesised from `fastLinkJS` public modules. This switches the
      * generated `AssetManifest` to use hashed filenames and changes
      * `clientDistDir` to point at the Vite `dist/` directory.
      *
      * Default: `false` (reads `sys.env("MELT_PROD")` as a fallback).
      */
    val meltkitProd =
      settingKey[Boolean]("Enable production mode (Vite manifest)")

    /** Filesystem path to the Vite `manifest.json` output. Only used
      * when [[meltkitProd]] is `true`.
      *
      * Default: `examples/http4s-ssr/dist/.vite/manifest.json` (relative
      * to `baseDirectory`). Override for non-standard Vite `outDir`.
      */
    val meltkitViteManifestPath =
      settingKey[File]("Path to the Vite manifest.json file")

    /** Filesystem path to the Vite `dist/` output directory. Only used
      * when [[meltkitProd]] is `true`. Becomes `clientDistDir` in the
      * generated `AssetManifest`.
      *
      * Default: sibling of `meltkitViteManifestPath` (`dist/`).
      */
    val meltkitViteDistDir =
      settingKey[File]("Path to the Vite dist directory")

    /** Source `index.html` template to copy into `clientDistDir` at build time.
      *
      * [[meltkit.adapter.http4s.Http4sAdapter.spaRoutes]] reads
      * `clientDistDir / "index.html"` via `fs2.io.file.Files` so that it
      * works on both JVM and Node.js. Set this key to have sbt-meltkit copy
      * your template automatically on every compile.
      *
      * Default: auto-detected from `(Compile / resourceDirectory) / "index.html"`.
      *
      * {{{
      * meltkitIndexHtml := Some(baseDirectory.value / "index.html")
      * }}}
      */
    val meltkitIndexHtml =
      settingKey[Option[File]]("Source index.html to copy into clientDistDir")

    /** Generates a `vite-inputs.json` file from the client's
      * `fullLinkJS` output. This JSON file is read by `vite.config.js`
      * as `rollupOptions.input` so that adding or removing a `.melt`
      * component automatically updates the Vite build without editing
      * any config files.
      */
    val meltkitViteInputGenerate =
      taskKey[File]("Generate vite-inputs.json from the client's fullLinkJS Report")

    /** Generates a `MeltKitConfig.scala` source for SSR projects.
      *
      * The generated object bundles the [[meltkit.ViteManifest]] reference,
      * asset base path, and default language used by `ctx.melt()` to render
      * SSR pages. The HTML template is read at startup via `fs2.io.file.Files`
      * by the adapter, keeping the generated code platform-agnostic.
      *
      * Only generated when all of the following hold:
      *   - [[meltkitAssetManifestClient]] is set (a Scala.js client exists)
      *   - the current project is a JVM project (no `ScalaJSPlugin`) or a Node.js server
      *
      * The generated file lives alongside `AssetManifest.scala` in the
      * same managed source directory and package.
      */
    val meltkitConfigGenerate =
      taskKey[Seq[File]]("Generate MeltKitConfig.scala for SSR rendering via ctx.melt()")

    /** Object name of the generated MeltKitConfig.
      * Default: `"MeltKitConfig"`.
      */
    val meltkitConfigObject =
      settingKey[String]("Object name for the auto-generated MeltKitConfig")

    /** Default HTML `lang` attribute value for SSR pages.
      * Default: `"en"`.
      */
    val meltkitConfigLang =
      settingKey[String]("Default HTML lang attribute for SSR pages")

    /** Asset base path used in [[meltkit.Template.render]] for SSR.
      * Default: `"/assets"`.
      */
    val meltkitConfigBasePath =
      settingKey[String]("Asset base path used in Template.render for SSR")

  import meltc.sbt.MeltcPlugin.autoImport.{ meltcCodegenMode, meltcCompilerVersion }

  import autoImport.*

  override def projectSettings: Seq[Setting[_]] = Seq(
    meltMode                 := None,
    meltkitManageRuntimeDeps := true,

    // Override meltcCodegenMode based on meltMode
    meltcCodegenMode := {
      meltMode.value match
        case Some(MeltMode.Browser) => "spa"
        case Some(MeltMode.Node)    => "ssr"
        case Some(MeltMode.Http4s)  => "ssr"
        case None                   => "auto"
    },

    // ── meltMode: auto-add runtime dependency ─────────────────────────────
    libraryDependencies ++= {
      if !meltkitManageRuntimeDeps.value then Seq.empty
      else
        val v    = meltcCompilerVersion.value
        val binV = scalaBinaryVersion.value // Scala 3 → "3"
        meltMode.value match
          case Some(MeltMode.Browser) =>
            // Scala.js artefact — name must be specified explicitly
            Seq("io.github.takapi327" % s"meltkit-adapter-browser_sjs1_$binV" % v)
          case Some(MeltMode.Node) =>
            Seq("io.github.takapi327" % s"meltkit-adapter-node_sjs1_$binV" % v)
          case Some(MeltMode.Http4s) =>
            Seq("io.github.takapi327" %% "meltkit-adapter-http4s" % v)
          case None =>
            Seq.empty
    },

    meltkitAssetManifestClient  := None,
    meltkitAssetManifestPackage := "generated",
    meltkitAssetManifestObject  := "AssetManifest",

    meltkitProd             := sys.env.get("MELT_PROD").exists(v => v == "true" || v == "1"),
    meltkitViteManifestPath := baseDirectory.value / ".." / "dist" / ".vite" / "manifest.json",
    meltkitViteDistDir      := baseDirectory.value / ".." / "dist",
    meltkitIndexHtml        := {
      val f = (Compile / resourceDirectory).value / "index.html"
      if f.exists() then Some(f) else None
    },

    meltkitConfigObject   := "MeltKitConfig",
    meltkitConfigLang     := "en",
    meltkitConfigBasePath := "/assets",

    meltkitConfigGenerate := Def.taskDyn {
      val client = meltkitAssetManifestClient.value
      val isNode = meltMode.value.contains(MeltMode.Node)
      // Generate for JVM servers (no ScalaJSPlugin) OR Node.js servers (MeltMode.Node)
      if client.isDefined && (!hasScalaJSPlugin(thisProject.value) || isNode) then
        Def.task {
          generateMeltKitConfig(
            streams        = streams.value,
            outDir         = (Compile / sourceManaged).value / "generated",
            pkgName        = meltkitAssetManifestPackage.value,
            objectName     = meltkitConfigObject.value,
            manifestObject = meltkitAssetManifestObject.value,
            lang           = meltkitConfigLang.value,
            basePath       = meltkitConfigBasePath.value
          )
        }
      else Def.task(Seq.empty[File])
    }.value,
    Compile / sourceGenerators += meltkitConfigGenerate.taskValue,

    meltkitViteInputGenerate := Def.taskDyn {
      meltkitAssetManifestClient.value match
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
    }.value,

    meltkitAssetManifestGenerate := Def.taskDyn {
      meltkitAssetManifestClient.value match
        case Some(clientProject) if meltkitProd.value =>
          Def.task {
            val distDir = meltkitViteDistDir.value
            meltkitIndexHtml.value.foreach(src => IO.copyFile(src, distDir / "index.html"))
            generateAssetManifestFromVite(
              streams      = streams.value,
              outDir       = (Compile / sourceManaged).value / "generated",
              pkgName      = meltkitAssetManifestPackage.value,
              objectName   = meltkitAssetManifestObject.value,
              manifestPath = meltkitViteManifestPath.value,
              distDir      = distDir
            )
          }
        case Some(clientProject) =>
          Def.task {
            val distDir = (clientProject / Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value
            meltkitIndexHtml.value.foreach(src => IO.copyFile(src, distDir / "index.html"))
            val isNode = meltMode.value.contains(MeltMode.Node)
            generateAssetManifest(
              streams      = streams.value,
              outDir       = (Compile / sourceManaged).value / "generated",
              pkgName      = meltkitAssetManifestPackage.value,
              objectName   = meltkitAssetManifestObject.value,
              report       = (clientProject / Compile / fastLinkJS).value.data,
              distDir      = distDir,
              isNodeServer = isNode
            )
          }
        case None =>
          Def.task(Seq.empty[File])
    }.value,
    Compile / sourceGenerators += meltkitAssetManifestGenerate.taskValue
  )

  /** Writes a `MeltKitConfig` Scala source that bundles the [[meltkit.Template]],
    * manifest reference, asset base path, and default language for SSR rendering.
    *
    * The generated object is placed in the same package as `AssetManifest` and
    * references `AssetManifest.manifest` by name so that both objects are always
    * in sync.
    */
  private def generateMeltKitConfig(
    streams:        TaskStreams,
    outDir:         File,
    pkgName:        String,
    objectName:     String,
    manifestObject: String,
    lang:           String,
    basePath:       String
  ): Seq[File] =
    val log = streams.log
    IO.createDirectory(outDir)
    val outFile = outDir / s"$objectName.scala"

    val code =
      s"""package $pkgName
         |
         |/** Auto-generated by sbt-meltkit — do not edit.
         |  *
         |  * Bundles the [[meltkit.ViteManifest]] reference, asset base path,
         |  * and default language used by `ctx.melt()` for SSR rendering.
         |  *
         |  * The HTML template (`index.html`) is intentionally excluded: it is
         |  * read at server startup via `fs2.io.file.Files` so that the adapter
         |  * works on both JVM and Node.js. To customise other values, override
         |  * the `meltkitConfig*` settings in `build.sbt`.
         |  */
         |object $objectName {
         |  val manifest: meltkit.ViteManifest = $manifestObject.manifest
         |  val basePath: String               = "$basePath"
         |  val lang:     String               = "$lang"
         |}
         |""".stripMargin

    IO.write(outFile, code)
    log.info(s"[sbt-meltkit] generated ${ outFile.getName }")
    Seq(outFile)

  /** Writes a `generated.AssetManifest` Scala source that exposes the
    * client project's Scala.js `fastLinkJS` output as a
    * [[meltkit.ViteManifest]] plus the absolute filesystem
    * path of the fastopt output directory as a [[fs2.io.file.Path]].
    * Regenerated on every compile so adding or removing a `.melt`
    * component requires zero edits to this file.
    */
  private def generateAssetManifest(
    streams:      TaskStreams,
    outDir:       File,
    pkgName:      String,
    objectName:   String,
    report:       Report,
    distDir:      File,
    isNodeServer: Boolean = false // unused, kept for binary compat
  ): Seq[File] =
    val log = streams.log
    IO.createDirectory(outDir)
    val outFile = outDir / s"$objectName.scala"

    val sortedModules = report.publicModules.toList.sortBy(_.moduleID)

    val entriesSrc = sortedModules
      .map { m =>
        s"""    "scalajs:${ m.moduleID }.js" -> ViteManifest.Entry(file = "${ m.jsFileName }")"""
      }
      .mkString(",\n")

    val distPathLit = distDir.getAbsolutePath.replace("\\", "\\\\")

    // Always use String for clientDistDir — no fs2 dependency required.
    // http4s users can convert via Path(AssetManifest.clientDistDir).
    val code =
      s"""package $pkgName
         |
         |import meltkit.ViteManifest
         |
         |/** Auto-generated by sbt-meltkit — do not edit.
         |  *
         |  * Maps every `@JSExportTopLevel("hydrate", moduleID = ...)` from
         |  * the client project's Scala.js `fastLinkJS` output to its
         |  * emitted chunk file name. `clientDistDir` is the absolute path
         |  * of the fastopt directory.
         |  *
         |  * For http4s `fileService`, convert to `Path` via
         |  * `fs2.io.file.Path(AssetManifest.clientDistDir)`.
         |  */
         |object $objectName {
         |  val manifest: ViteManifest = ViteManifest.fromEntries(Map(
         |$entriesSrc
         |  ))
         |
         |  val clientDistDir: String = "$distPathLit"
         |}
         |""".stripMargin

    IO.write(outFile, code)
    log.info(
      s"[sbt-meltkit] regenerated ${ outFile.getName } with ${ sortedModules.size } public modules"
    )
    Seq(outFile)

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
  ): File =
    val log           = streams.log
    val sortedModules = report.publicModules.toList.sortBy(_.moduleID)

    val entries = sortedModules.map { m =>
      val absPath = (distDir / m.jsFileName).getAbsolutePath
        .replace("\\", "/")
      s"""  "${ m.moduleID }": "$absPath""""
    }
    val json = entries.mkString("{\n", ",\n", "\n}\n")

    IO.write(outFile, json)
    log.info(
      s"[sbt-meltkit] wrote ${ outFile.getName } with ${ sortedModules.size } entries"
    )
    outFile

  /** Writes a `generated.AssetManifest` Scala source that loads the
    * Vite-produced `manifest.json` at startup. Used in production mode
    * when `meltkitProd := true` (or `MELT_PROD=true`).
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
  ): Seq[File] =
    val log = streams.log

    if !manifestPath.exists() then
      log.error(
        s"[sbt-meltkit] Vite manifest not found at ${ manifestPath.getAbsolutePath }. " +
          "Run `npx vite build` in the example directory first."
      )
      return Seq.empty

    IO.createDirectory(outDir)
    val outFile = outDir / s"$objectName.scala"

    val manifestPathLit = manifestPath.getAbsolutePath.replace("\\", "\\\\")
    val distPathLit     = distDir.getAbsolutePath.replace("\\", "\\\\")

    // Read manifest JSON at sbt compile time and embed it as a string literal.
    // This avoids java.nio.file at runtime, making it compatible with Scala.js.
    val manifestContent = IO.read(manifestPath).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    val code =
      s"""package $pkgName
         |
         |import meltkit.ViteManifest
         |
         |/** Auto-generated by sbt-meltkit (prod mode) — do not edit.
         |  *
         |  * Embeds the Vite-produced `manifest.json` at compile time.
         |  * All chunk filenames are content-hashed by Vite, enabling
         |  * `Cache-Control: immutable` on production deployments.
         |  *
         |  * For http4s `fileService`, convert to `Path` via
         |  * `fs2.io.file.Path(AssetManifest.clientDistDir)`.
         |  */
         |object $objectName {
         |  val manifest: ViteManifest = ViteManifest.fromString(
         |    "$manifestContent"
         |  )
         |
         |  val clientDistDir: String = "$distPathLit"
         |}
         |""".stripMargin

    IO.write(outFile, code)
    log.info(
      s"[sbt-meltkit] regenerated ${ outFile.getName } (prod mode, Vite manifest)"
    )
    Seq(outFile)
