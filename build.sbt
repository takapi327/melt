import BuildSettings._
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
  "project" -> List(
    "compilerJVM",
    "compilerJS",
    "codegenJVM",
    "codegenJS",
    "meltkitJVM",
    "meltkitJS",
    "meltkit-adapter-browser",
    "meltkit-adapter-node",
    "meltkit-adapter-http4sJVM",
    "meltkit-adapter-http4sJS"
  )
ThisBuild / githubWorkflowBuildMatrixExclusions ++= Seq(
  // JS runs on Java 17 only
  MatrixExclude(Map("project" -> "compilerJS", "java" -> s"corretto@$java21")),
  MatrixExclude(Map("project" -> "compilerJS", "java" -> s"corretto@$java25")),
  MatrixExclude(Map("project" -> "codegenJS", "java" -> s"corretto@$java21")),
  MatrixExclude(Map("project" -> "codegenJS", "java" -> s"corretto@$java25")),
  MatrixExclude(Map("project" -> "meltkitJS", "java" -> s"corretto@$java21")),
  MatrixExclude(Map("project" -> "meltkitJS", "java" -> s"corretto@$java25")),
  MatrixExclude(Map("project" -> "meltkit-adapter-browser", "java" -> s"corretto@$java21")),
  MatrixExclude(Map("project" -> "meltkit-adapter-browser", "java" -> s"corretto@$java25")),
  MatrixExclude(Map("project" -> "meltkit-adapter-node", "java" -> s"corretto@$java21")),
  MatrixExclude(Map("project" -> "meltkit-adapter-node", "java" -> s"corretto@$java25")),
  MatrixExclude(Map("project" -> "meltkit-adapter-http4sJS", "java" -> s"corretto@$java21")),
  MatrixExclude(Map("project" -> "meltkit-adapter-http4sJS", "java" -> s"corretto@$java25")),
  // Scala 3.8.3 runs on Java 17 only
  MatrixExclude(Map("java" -> s"corretto@$java21", "scala" -> scala38)),
  MatrixExclude(Map("java" -> s"corretto@$java25", "scala" -> scala38))
)
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
    cond = Some(
      "contains('compilerJS codegenJS meltkitJS meltkit-adapter-browser meltkit-adapter-node meltkit-adapter-http4sJS', matrix.project)"
    )
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

// ── CSS preprocessor API (no external dependencies, cross-compiled) ──
lazy val `compiler-css` = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("modules/compiler-css"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name := "melt-compiler-css"
  )
  .enablePlugins(AutomateHeaderPlugin)

// ── SCSS support via Dart Sass (optional, JVM only) ──
lazy val `compiler-sass` = project
  .in(file("modules/compiler-sass"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name                                       := "melt-compiler-sass",
    libraryDependencies += "de.larsgrefer.sass" % "sass-embedded-host" % "4.4.0"
  )
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(`compiler-css`.jvm)

// ── Core compiler (JVM + JS + Native) ──
lazy val compiler = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .module("compiler", "Core compiler: .melt → .scala")
  .settings(
    libraryDependencies ++= Seq(
      "org.scalameta" %%% "munit" % "1.3.0" % Test
    )
  )
  .jsSettings(
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) }
  )
  .dependsOn(`compiler-css`)

// ── Runtime (crossProject: JVM + JS) ──
// JS side: Scala.js reactive runtime (existing SPA implementation).
// JVM side: no-op stubs + server-render helpers under melt.runtime.render.
// Shared:   trait State[A] / Signal[A] / Memo[A] API contract + TrustedHtml +
//           StateExtensions.
lazy val runtime = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("modules/runtime"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name                                    := "melt-runtime",
    libraryDependencies += "org.scalameta" %%% "munit" % "1.3.0" % Test
  )
  .jsSettings(
    libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "2.8.1",
    // Use jsdom so that DOM APIs (matchMedia, dispatchEvent, etc.) are
    // available in unit tests.
    jsEnv := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv()
  )
  .enablePlugins(AutomateHeaderPlugin, spray.boilerplate.BoilerplatePlugin)

// ── Code generator (JVM + JS): depends on compiler (AST/parser) + runtime ──
// This is the only module that knows about both the compiler AST and the
// melt-runtime API that the generated code will import. Moving SsrCodeGen /
// SpaCodeGen / MeltCompiler here makes the compiler ↔ runtime coupling explicit
// in build.sbt instead of being an invisible contract buried in string literals.
lazy val codegen = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("modules/codegen"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name                                    := "melt-codegen",
    libraryDependencies += "org.scalameta" %%% "munit" % "1.3.0" % Test
  )
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(compiler, runtime)

// ── Test utilities (Scala.js) ──
lazy val testkit = project
  .in(file("modules/testkit"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name := "melt-testkit",
    libraryDependencies ++= Seq(
      "org.scalameta" %%% "munit" % "1.3.0"
    ),
    // Use jsdom so that DOM APIs are available in unit tests
    jsEnv := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv()
  )
  .enablePlugins(ScalaJSPlugin, AutomateHeaderPlugin)
  .dependsOn(runtime.js)

// ── MeltKit: routing DSL (JVM + JS, Scala 3.8+) ──────────────────────────────
lazy val meltkit = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("modules/meltkit"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name                                    := "meltkit",
    scalaVersion                            := scala38,
    libraryDependencies += "org.scalameta" %%% "munit" % "1.3.0" % Test
  )
  .jvmSettings(
    libraryDependencies += "io.undertow" % "undertow-core" % "2.3.18.Final"
  )
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(runtime)

// ── MeltKit: browser adapter (Scala.js only, Scala 3.8+) ─────────────────────
lazy val `meltkit-adapter-browser` = project
  .in(file("modules/meltkit-adapter-browser"))
  .enablePlugins(ScalaJSPlugin, AutomateHeaderPlugin)
  .settings(BuildSettings.commonSettings)
  .settings(
    name         := "meltkit-adapter-browser",
    scalaVersion := scala38,
    libraryDependencies ++= Seq(
      "org.scalameta" %%% "munit" % "1.3.0" % Test
    )
  )
  .dependsOn(meltkit.js)

// ── MeltKit: Node.js server adapter (Scala.js only, Scala 3.8+) ──────────────
lazy val `meltkit-adapter-node` = project
  .in(file("modules/meltkit-adapter-node"))
  .enablePlugins(ScalaJSPlugin, AutomateHeaderPlugin)
  .settings(BuildSettings.commonSettings)
  .settings(
    name         := "meltkit-adapter-node",
    scalaVersion := scala38,
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.CommonJSModule)
    },
    libraryDependencies += "org.scalameta" %%% "munit" % "1.3.0" % Test
  )
  .dependsOn(meltkit.js)

// ── MeltKit: http4s adapter (JVM + JS, Scala 3.8+) ───────────────────────────
lazy val `meltkit-adapter-http4s` = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("modules/meltkit-adapter-http4s"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name         := "meltkit-adapter-http4s",
    scalaVersion := scala38,
    libraryDependencies ++= Seq(
      "org.http4s"    %%% "http4s-core"         % "0.23.33",
      "org.http4s"    %%% "http4s-server"       % "0.23.33",
      "org.http4s"    %%% "http4s-ember-server" % "0.23.33",
      "org.http4s"    %%% "http4s-circe"        % "0.23.33",
      "io.circe"      %%% "circe-parser"        % "0.14.9",
      "org.typelevel" %%% "munit-cats-effect"   % "2.0.0" % Test
    )
  )
  .enablePlugins(AutomateHeaderPlugin)
  .jvmConfigure(_.dependsOn(meltkit.jvm))
  .jsConfigure(_.dependsOn(`meltkit-adapter-node`))
  .jsSettings(
    // Node.js: AsyncLocalStorage uses @JSImport which requires module support
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
    // Tests require Node.js (cats-effect IO runtime + AsyncLocalStorage for Router)
    jsEnv := new org.scalajs.jsenv.nodejs.NodeJSEnv()
  )

// ── sbt plugin: core compiler ──
// The plugin forks a JVM process to run meltc.MeltcMain, avoiding Scala 2.12/3 binary
// incompatibility. In external projects the classpath is auto-resolved via the internal
// `meltc-compiler` Ivy configuration after `publishLocal`. In this monorepo the
// hello-world example wires codegen.jvm directly (see below).
lazy val `sbt-meltc` = MeltSbtPluginProject("sbt-meltc", "plugins/sbt-meltc")
  .settings(
    crossScalaVersions                     := Seq(ScalaVersions.scala2), // sbt plugins require Scala 2.12
    libraryDependencies += "org.scalameta" %% "munit" % "1.3.0" % Test
  )

// ── sbt plugin: meltkit integration ──
// Adds runtime dependency management, asset manifest generation, and MeltKitConfig
// generation on top of sbt-meltc. Requires sbt-meltc (enabled automatically via requires).
lazy val `sbt-meltkit` = MeltSbtPluginProject("sbt-meltkit", "plugins/sbt-meltkit")
  .dependsOn(`sbt-meltc`)
  .settings(
    crossScalaVersions := Seq(ScalaVersions.scala2),
    libraryDependencies += Defaults.sbtPluginExtra(
      "org.scala-js" % "sbt-scalajs" % "1.21.0",
      (pluginCrossBuild / sbtBinaryVersion).value,
      (update / scalaBinaryVersion).value
    )
  )

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
    assembly / mainClass             := Some("melt.lsp.MeltLanguageServerLauncher"),
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
  .dependsOn(codegen.jvm)

// ── Root (no publish) ──
lazy val root = project
  .in(file("."))
  .aggregate(
    `compiler-css`.jvm,
    `compiler-css`.js,
    `compiler-sass`,
    compiler.jvm,
    compiler.js,
    runtime.jvm,
    runtime.js,
    codegen.jvm,
    codegen.js,
    testkit,
    meltkit.jvm,
    meltkit.js,
    `meltkit-adapter-browser`,
    `meltkit-adapter-node`,
    `meltkit-adapter-http4s`.jvm,
    `meltkit-adapter-http4s`.js,
    `sbt-meltc`,
    `sbt-meltkit`,
    `language-server`
  )
  .settings(BuildSettings.commonSettings)
  .settings(
    publish / skip     := true,
    crossScalaVersions := Seq.empty // root project does not cross-compile
  )
