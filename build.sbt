import Implicits._
import JavaVersions._
import ScalaVersions._

ThisBuild / version            := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion       := scala3
ThisBuild / crossScalaVersions := Seq(scala3, scala38)

// ── GitHub Actions ──
ThisBuild / githubWorkflowJavaVersions := Seq(
  JavaSpec.corretto(java17),
  JavaSpec.corretto(java21),
  JavaSpec.corretto(java25)
)
ThisBuild / githubWorkflowBuildMatrixAdditions +=
  "project" -> List("meltcJVM", "meltcJS", "meltcNative")
ThisBuild / githubWorkflowBuildMatrixExclusions ++= Seq(
  // JS / Native run on Java 17 only
  MatrixExclude(Map("project" -> "meltcJS", "java" -> s"corretto@$java21")),
  MatrixExclude(Map("project" -> "meltcJS", "java" -> s"corretto@$java25")),
  MatrixExclude(Map("project" -> "meltcNative", "java" -> s"corretto@$java21")),
  MatrixExclude(Map("project" -> "meltcNative", "java" -> s"corretto@$java25")),
  // Scala 3.8.3 runs on Java 17 only
  MatrixExclude(Map("java" -> s"corretto@$java21", "scala" -> scala38)),
  MatrixExclude(Map("java" -> s"corretto@$java25", "scala" -> scala38))
)
ThisBuild / githubWorkflowBuildPreamble += Workflows.installNativeDeps
ThisBuild / githubWorkflowBuild := Seq(
  WorkflowStep.Sbt(
    // The plugin prepends '++ ${{ matrix.scala }}' automatically; only project switching is needed here
    List(
      "project ${{ matrix.project }}",
      "headerCheckAll",
      "scalafmtCheckAll",
      "project /",
      "scalafmtSbtCheck"
    ),
    name = Some("Check headers and formatting"),
    cond = Some(s"matrix.java == 'corretto@$java17'")
  ),
  WorkflowStep.Sbt(
    List("project ${{ matrix.project }}", "Test/scalaJSLinkerResult"),
    name = Some("scalaJSLink"),
    cond = Some("matrix.project == 'meltcJS'")
  ),
  WorkflowStep.Sbt(
    List("project ${{ matrix.project }}", "Test/nativeLink"),
    name = Some("nativeLink"),
    cond = Some("matrix.project == 'meltcNative'")
  ),
  WorkflowStep.Sbt(
    List("project ${{ matrix.project }}", "test"),
    name = Some("Test")
  )
)
// Override upload/download steps to match ldbc's pattern:
//   - mkdir -p ensures missing platform targets don't cause tar to fail
//   - all three steps (mkdir, tar, upload) are gated to tag-push only
//   - artifact name includes matrix.project to avoid conflicts across platform jobs
ThisBuild / githubWorkflowGeneratedUploadSteps   := Workflows.uploadSteps
ThisBuild / githubWorkflowGeneratedDownloadSteps := Workflows.downloadSteps
ThisBuild / githubWorkflowTargetBranches         := Seq("**")
ThisBuild / githubWorkflowTargetTags             := Seq("v*")
ThisBuild / githubWorkflowPublishTargetBranches  := Seq(RefPredicate.StartsWith(Ref.Tag("v")))
ThisBuild / githubWorkflowAddedJobs += Workflows.sbtScripted.value

// ── Core compiler (JVM + JS + Native) ──
lazy val meltc = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Full)
  .module("meltc", "Core compiler: .melt → .scala")
  .settings(
    name := "meltc", // override "melt-meltc" → "meltc"
    libraryDependencies ++= Seq(
      "org.scalameta" %%% "munit" % "1.2.4" % Test
    )
  )
  .jsSettings(
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) }
  )
  .nativeSettings(
    // Reserved for future Native CLI configuration
  )

// ── sbt plugin ──
// Note: dependsOn(meltc.jvm) will be added in Phase 3 after cross-compilation is configured.
lazy val `sbt-meltc` = BuildSettings
  .MeltSbtPluginProject("sbt-meltc", "modules/sbt-meltc")
  .settings(
    crossScalaVersions := Seq(ScalaVersions.scala2) // sbt plugins require Scala 2.12
  )

// ── Runtime (Scala.js library) ──
lazy val runtime = project
  .in(file("modules/runtime"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name := "melt-runtime",
    libraryDependencies ++= Seq(
      "org.scala-js"  %%% "scalajs-dom" % "2.8.1",
      "org.scalameta" %%% "munit"       % "1.2.4" % Test
    )
  )
  .enablePlugins(ScalaJSPlugin, AutomateHeaderPlugin)

// ── Language Server (LSP — shared across all editors) ──
lazy val `language-server` = project
  .in(file("editors/language-server"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name := "melt-language-server",
    libraryDependencies ++= Seq(
      "org.eclipse.lsp4j" % "org.eclipse.lsp4j" % "1.0.0",
      "org.scalameta"    %% "munit"             % "1.2.4" % Test
    )
  )
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(meltc.jvm)

// ── Test utilities (Scala.js) ──
lazy val `melt-testing` = project
  .in(file("modules/melt-testing"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name := "melt-testing",
    libraryDependencies ++= Seq(
      "org.scalameta" %%% "munit" % "1.2.4"
    )
  )
  .enablePlugins(ScalaJSPlugin, AutomateHeaderPlugin)
  .dependsOn(runtime)

// ── Root (no publish) ──
lazy val root = project
  .in(file("."))
  .aggregate(
    meltc.jvm,
    meltc.js,
    meltc.native,
    `sbt-meltc`,
    runtime,
    `melt-testing`,
    `language-server`
  )
  .settings(BuildSettings.commonSettings)
  .settings(
    publish / skip     := true,
    crossScalaVersions := Seq.empty // root project does not cross-compile
  )
