import melt.sbt.Dependencies

ThisBuild / scalaVersion   := "3.8.4"
ThisBuild / publish / skip := true

// ── Documentation site (SSR + Hydration + SSG) ───────────────────────────────
//
// A single `enablePlugins(MeltkitAppPlugin)` derives the two projects this site
// needs from `frontend/` (Melt pages, hydrated in the browser) and `backend/`
// (the Undertow SSR server + the SSG generator):
//
//   sbt "docs-backend/runMain docs.server"    ← SSR dev server
//   sbt "docs-backend/runMain docs.generate"  ← static site generation
//
// Per-project settings use the extension methods:
//   .sharedSettings   — applied to both derived projects
//   .frontendSettings — frontend (Scala.js) only
//   .backendSettings  — backend (JVM) only
lazy val docs = project
  .in(file("."))
  .enablePlugins(MeltkitAppPlugin)
  // melt-codegen powers the in-browser playground; needed by both the JS bundle
  // (interactive compile) and the JVM server (SSR of the playground). The shared
  // `Dependencies.meltCodegen` (`%%`) resolves to `_sjs1_3` on the frontend and `_3`
  // on the backend; its version is the plugin's own `melt.build.Version.current`.
  .sharedSettings(
    libraryDependencies += Dependencies.meltCodegen
  )
  // Each `.melt` page becomes its own JS module so a page loads only its own code.
  .frontendSettings(
    meltkitSplitPackages := List("docs")
  )
