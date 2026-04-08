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
// The plugin forks a JVM process to run meltc.MeltcMain, avoiding Scala 2.12/3 binary
// incompatibility. In external projects the classpath is auto-resolved via the internal
// `meltc-compiler` Ivy configuration after `publishLocal`. In this monorepo the
// hello-world example wires meltc.jvm directly (see below).
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
    ),
    // Use jsdom so that DOM APIs (matchMedia, dispatchEvent, etc.) are available
    // in unit tests. Required by TransitionEventSpec which tests TransitionEngine directly.
    jsEnv := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv()
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
    ),
    // Fat JAR: java -jar melt-language-server.jar
    assembly / assemblyJarName       := "melt-language-server.jar",
    assembly / mainClass             := Some("meltc.lsp.MeltLanguageServerLauncher"),
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "MANIFEST.MF")   => MergeStrategy.discard
      case PathList("META-INF", "services", _*)  => MergeStrategy.concat
      case PathList("META-INF", _*)              => MergeStrategy.discard
      case PathList("module-info.class")         => MergeStrategy.discard
      case x if x.endsWith("/module-info.class") => MergeStrategy.discard
      case _                                     => MergeStrategy.first
    }
  )
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(meltc.jvm)

// ── Test utilities (Scala.js) ──
lazy val `melt-testkit` = project
  .in(file("modules/melt-testkit"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name := "melt-testkit",
    libraryDependencies ++= Seq(
      "org.scalameta" %%% "munit" % "1.2.4"
    ),
    // Use jsdom so that DOM APIs are available in unit tests
    jsEnv := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv()
  )
  .enablePlugins(ScalaJSPlugin, AutomateHeaderPlugin)
  .dependsOn(runtime)

// ── Example: Hello World ──────────────────────────────────────────────────────
// MeltcPlugin is loaded from source via project/build.sbt (no publishLocal needed).
// meltcCompilerClasspath is wired directly from meltc.jvm, so Fork.java is invoked
// with the correct classpath without any manual setup.
lazy val `hello-world` = project
  .in(file("examples/hello-world"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name                            := "hello-world",
    publish / skip                  := true,
    scalaJSUseMainModuleInitializer := true,
    meltcCompilerClasspath          := (meltc.jvm / Compile / fullClasspath).value.files
  )
  .enablePlugins(ScalaJSPlugin, MeltcPlugin, AutomateHeaderPlugin)
  .dependsOn(runtime)

// ── Example: Counter (Phase 4 — reactive bindings) ��─────────────────────────
lazy val counter = project
  .in(file("examples/counter"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name                            := "counter",
    publish / skip                  := true,
    scalaJSUseMainModuleInitializer := true,
    meltcCompilerClasspath          := (meltc.jvm / Compile / fullClasspath).value.files,
    // Use jsdom so that DOM APIs are available in unit tests
    jsEnv := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv()
  )
  .enablePlugins(ScalaJSPlugin, MeltcPlugin, AutomateHeaderPlugin)
  .dependsOn(runtime, `melt-testkit` % Test)

// ── Example: Todo App (Phase 5 — multi-component) ────────────────────────────
lazy val `todo-app` = project
  .in(file("examples/todo-app"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name                            := "todo-app",
    publish / skip                  := true,
    scalaJSUseMainModuleInitializer := true,
    meltcCompilerClasspath          := (meltc.jvm / Compile / fullClasspath).value.files
  )
  .enablePlugins(ScalaJSPlugin, MeltcPlugin, AutomateHeaderPlugin)
  .dependsOn(runtime)

// ── Example: Transitions (Phase 9 — transitions & animations) ─────────────────
lazy val transitions = project
  .in(file("examples/transitions"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name                            := "transitions",
    publish / skip                  := true,
    scalaJSUseMainModuleInitializer := true,
    meltcCompilerClasspath          := (meltc.jvm / Compile / fullClasspath).value.files
  )
  .enablePlugins(ScalaJSPlugin, MeltcPlugin, AutomateHeaderPlugin)
  .dependsOn(runtime)

// ── Example: layoutEffect (Phase 13 — pre/post subscriber lanes) ─────────────
lazy val `layout-effect` = project
  .in(file("examples/layout-effect"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name                            := "layout-effect",
    publish / skip                  := true,
    scalaJSUseMainModuleInitializer := true,
    meltcCompilerClasspath          := (meltc.jvm / Compile / fullClasspath).value.files
  )
  .enablePlugins(ScalaJSPlugin, MeltcPlugin, AutomateHeaderPlugin)
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
    `melt-testkit`,
    `language-server`,
    `hello-world`,
    counter,
    `todo-app`,
    transitions,
    `layout-effect`
  )
  .settings(BuildSettings.commonSettings)
  .settings(
    publish / skip     := true,
    crossScalaVersions := Seq.empty // root project does not cross-compile
  )
