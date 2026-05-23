import org.scalajs.linker.interface.ModuleSplitStyle

val meltVersion = "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion   := "3.8.3"
ThisBuild / publish / skip := true

// ── Documentation site (SSR + Hydration + SSG) ───────────────────────────────
//
//   sbt "docsJVM/run server"   ← SSR dev server
//   sbt "docsJVM/run generate" ← static site generation
lazy val docs = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("."))
  .enablePlugins(MeltkitPlugin)
  .jsSettings(
    meltcHydration                  := true,
    scalaJSUseMainModuleInitializer := false,
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.ESModule)
        .withModuleSplitStyle(ModuleSplitStyle.SmallModulesFor(List("docs")))
    }
  )
  .jvmSettings(
    run / fork := true
  )
