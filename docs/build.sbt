import org.scalajs.linker.interface.ModuleSplitStyle

import meltkit.sbt.MeltkitPlugin.autoImport._

val meltVersion = "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion  := "3.8.3"
ThisBuild / publish / skip := true

// ── Documentation site (SSR + Hydration + SSG) ───────────────────────────────
//
//   sbt "docsJVM/run server"   ← SSR dev server
//   sbt "docsJVM/run generate" ← static site generation
lazy val docs = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("."))
  .settings(
    libraryDependencies += "io.github.takapi327" %%% "meltkit" % meltVersion
  )
  .enablePlugins(MeltkitPlugin)
  .jsConfigure(
    _.settings(
      libraryDependencies += "io.github.takapi327" %%% "meltkit-adapter-browser" % meltVersion
    )
  )
  .jsSettings(
    meltcHydration                  := true,
    scalaJSUseMainModuleInitializer := false,
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.ESModule)
        .withModuleSplitStyle(ModuleSplitStyle.SmallModulesFor(List("docs")))
    }
  )
  .jvmSettings(
    run / fork                 := true,
    meltkitAssetManifestClient := Some(LocalProject("docsJS")),
    meltkitViteDistDir         := baseDirectory.value / ".." / "dist",
    meltkitViteManifestPath    := baseDirectory.value / ".." / "dist" / ".vite" / "manifest.json"
  )
