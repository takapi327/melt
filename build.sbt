import org.scalajs.linker.interface.ModuleSplitStyle

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
    "meltcJVM",
    "meltcJS",
    "meltcNative",
    "codegenJVM",
    "codegenJS",
    "meltkitJVM",
    "meltkitJS",
    "meltkit-browser",
    "meltkit-node",
    "meltkit-ssg",
    "meltkit-adapter-http4sJVM",
    "meltkit-adapter-http4sJS"
  )
ThisBuild / githubWorkflowBuildMatrixExclusions ++= Seq(
  // JS / Native run on Java 17 only
  MatrixExclude(Map("project" -> "meltcJS", "java" -> s"corretto@$java21")),
  MatrixExclude(Map("project" -> "meltcJS", "java" -> s"corretto@$java25")),
  MatrixExclude(Map("project" -> "meltcNative", "java" -> s"corretto@$java21")),
  MatrixExclude(Map("project" -> "meltcNative", "java" -> s"corretto@$java25")),
  MatrixExclude(Map("project" -> "codegenJS", "java" -> s"corretto@$java21")),
  MatrixExclude(Map("project" -> "codegenJS", "java" -> s"corretto@$java25")),
  MatrixExclude(Map("project" -> "meltkitJS", "java" -> s"corretto@$java21")),
  MatrixExclude(Map("project" -> "meltkitJS", "java" -> s"corretto@$java25")),
  MatrixExclude(Map("project" -> "meltkit-browser", "java" -> s"corretto@$java21")),
  MatrixExclude(Map("project" -> "meltkit-browser", "java" -> s"corretto@$java25")),
  MatrixExclude(Map("project" -> "meltkit-node", "java" -> s"corretto@$java21")),
  MatrixExclude(Map("project" -> "meltkit-node", "java" -> s"corretto@$java25")),
  MatrixExclude(Map("project" -> "meltkit-adapter-http4sJS", "java" -> s"corretto@$java21")),
  MatrixExclude(Map("project" -> "meltkit-adapter-http4sJS", "java" -> s"corretto@$java25")),
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
    cond = Some(
      "contains('meltcJS codegenJS meltkitJS meltkit-browser meltkit-node meltkit-adapter-http4sJS', matrix.project)"
    )
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

// ── CSS preprocessor API (no external dependencies, cross-compiled) ──
lazy val `meltc-css` = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("modules/meltc-css"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name := "meltc-css"
  )
  .enablePlugins(AutomateHeaderPlugin)

// ── SCSS support via Dart Sass (optional, JVM only) ──
lazy val `meltc-sass` = project
  .in(file("modules/meltc-sass"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name                                       := "meltc-sass",
    libraryDependencies += "de.larsgrefer.sass" % "sass-embedded-host" % "4.0.2"
  )
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(`meltc-css`.jvm)

// ── Core compiler (JVM + JS + Native) ──
lazy val meltc = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Full)
  .module("meltc", "Core compiler: .melt → .scala")
  .settings(
    name := "meltc", // override "melt-meltc" → "meltc"
    libraryDependencies ++= Seq(
      "org.scalameta" %%% "munit" % "1.3.0" % Test
    )
  )
  .jsSettings(
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) }
  )
  .nativeSettings(
    // Reserved for future Native CLI configuration
  )
  .dependsOn(`meltc-css`)

// ── Runtime (crossProject: JVM + JS) ──
// JS side: Scala.js reactive runtime (existing SPA implementation).
// JVM side: no-op stubs + server-render helpers under melt.runtime.render.
// Shared:   trait Var[A] / Signal[A] / Memo[A] API contract + TrustedHtml +
//           VarExtensions.
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

// ── Code generator (JVM + JS): depends on meltc (AST/parser) + runtime ──
// This is the only module that knows about both the meltc AST and the
// melt-runtime API that the generated code will import. Moving SsrCodeGen /
// SpaCodeGen / MeltCompiler here makes the meltc ↔ runtime coupling explicit
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
  .dependsOn(meltc, runtime)

// ── Test utilities (Scala.js) ──
lazy val `melt-testkit` = project
  .in(file("modules/melt-testkit"))
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
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(runtime)

// ── MeltKit: browser adapter (Scala.js only, Scala 3.8+) ─────────────────────
lazy val `meltkit-browser` = project
  .in(file("modules/meltkit-browser"))
  .enablePlugins(ScalaJSPlugin, AutomateHeaderPlugin)
  .settings(BuildSettings.commonSettings)
  .settings(
    name         := "meltkit-browser",
    scalaVersion := scala38,
    libraryDependencies ++= Seq(
      "org.scalameta" %%% "munit" % "1.3.0" % Test
    )
  )
  .dependsOn(meltkit.js)

// ── MeltKit: Node.js server adapter (Scala.js only, Scala 3.8+) ──────────────
lazy val `meltkit-node` = project
  .in(file("modules/meltkit-node"))
  .enablePlugins(ScalaJSPlugin, AutomateHeaderPlugin)
  .settings(BuildSettings.commonSettings)
  .settings(
    name         := "meltkit-node",
    scalaVersion := scala38,
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.CommonJSModule)
    },
    libraryDependencies += "org.scalameta" %%% "munit" % "1.3.0" % Test
  )
  .dependsOn(meltkit.js)

// ── MeltKit: SSG static site generator (JVM only, Scala 3.8+) ───────────────
lazy val `meltkit-ssg` = project
  .in(file("modules/meltkit-ssg"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name                                   := "meltkit-ssg",
    scalaVersion                           := scala38,
    libraryDependencies += "org.scalameta" %% "munit" % "1.3.0" % Test
  )
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(meltkit.jvm)

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
  .jsConfigure(_.dependsOn(`meltkit-node`))
  .jsSettings(
    // Node.js: AsyncLocalStorage uses @JSImport which requires module support
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
    // Tests require Node.js (cats-effect IO runtime + AsyncLocalStorage for Router)
    jsEnv := new org.scalajs.jsenv.nodejs.NodeJSEnv()
  )

// ── sbt plugin ──
// The plugin forks a JVM process to run meltc.MeltcMain, avoiding Scala 2.12/3 binary
// incompatibility. In external projects the classpath is auto-resolved via the internal
// `meltc-compiler` Ivy configuration after `publishLocal`. In this monorepo the
// hello-world example wires codegen.jvm directly (see below).
lazy val `sbt-meltc` = BuildSettings
  .MeltSbtPluginProject("sbt-meltc", "modules/sbt-meltc")
  .settings(
    crossScalaVersions := Seq(ScalaVersions.scala2), // sbt plugins require Scala 2.12
    // sbt-scalajs is pulled in via modules/sbt-meltc/build.sbt (a
    // sub-project build file), because the meta-build compiles
    // sbt-meltc as a source dependency of the root meta-project and
    // that compile phase reads sbt-meltc's OWN build.sbt — not this
    // one.
    libraryDependencies += "org.scalameta" %% "munit" % "1.3.0" % Test
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
  .dependsOn(codegen.jvm)

// ── Example: Hello World ──────────────────────────────────────────────────────
// MeltcPlugin is loaded from source via project/build.sbt (no publishLocal needed).
// meltcCompilerClasspath is wired directly from codegen.jvm, so Fork.java is invoked
// with the correct classpath without any manual setup.
lazy val `hello-world` = project
  .in(file("examples/hello-world"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name                            := "hello-world",
    publish / skip                  := true,
    scalaJSUseMainModuleInitializer := true,
    meltcManageCompilerDeps         := false,
    meltcCompilerClasspath          := (codegen.jvm / Compile / fullClasspath).value.files
  )
  .enablePlugins(ScalaJSPlugin, MeltcPlugin, AutomateHeaderPlugin)
  .dependsOn(runtime.js)

// ── Example: Counter (Phase 4 — reactive bindings) ───────────────────────────
lazy val counter = project
  .in(file("examples/counter"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name                            := "counter",
    publish / skip                  := true,
    scalaJSUseMainModuleInitializer := true,
    meltcManageCompilerDeps         := false,
    meltcCompilerClasspath          := (codegen.jvm / Compile / fullClasspath).value.files,
    // Use jsdom so that DOM APIs are available in unit tests
    jsEnv := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv()
  )
  .enablePlugins(ScalaJSPlugin, MeltcPlugin, AutomateHeaderPlugin)
  .dependsOn(runtime.js, `melt-testkit` % Test)

// ── Example: SCSS Counter (SCSS support via meltc-sass) ──────────────────────
lazy val `scss-counter` = project
  .in(file("examples/scss-counter"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name                            := "scss-counter",
    publish / skip                  := true,
    scalaJSUseMainModuleInitializer := true,
    meltcManageCompilerDeps         := false,
    meltcManagePreprocessorDeps     := false,
    meltcStylePreprocessor          := Some(SassPreprocessor),
    meltcCompilerClasspath          := (codegen.jvm / Compile / fullClasspath).value.files ++
      (`meltc-sass` / Compile / fullClasspath).value.files
  )
  .enablePlugins(ScalaJSPlugin, MeltcPlugin, AutomateHeaderPlugin)
  .dependsOn(runtime.js)

// ── Example: Todo App (Phase 5 — multi-component) ────────────────────────────
lazy val `todo-app` = project
  .in(file("examples/todo-app"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name                            := "todo-app",
    publish / skip                  := true,
    scalaJSUseMainModuleInitializer := true,
    meltcManageCompilerDeps         := false,
    meltcCompilerClasspath          := (codegen.jvm / Compile / fullClasspath).value.files
  )
  .enablePlugins(ScalaJSPlugin, MeltcPlugin, AutomateHeaderPlugin)
  .dependsOn(runtime.js)

// ── Example: Transitions (Phase 9 — transitions & animations) ─────────────────
lazy val transitions = project
  .in(file("examples/transitions"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name                            := "transitions",
    publish / skip                  := true,
    scalaJSUseMainModuleInitializer := true,
    meltcManageCompilerDeps         := false,
    meltcCompilerClasspath          := (codegen.jvm / Compile / fullClasspath).value.files
  )
  .enablePlugins(ScalaJSPlugin, MeltcPlugin, AutomateHeaderPlugin)
  .dependsOn(runtime.js)

// ── Example: Special Elements (Phase 14 — melt:head / melt:window / melt:body) ──
lazy val `special-elements` = project
  .in(file("examples/special-elements"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name                            := "special-elements",
    publish / skip                  := true,
    scalaJSUseMainModuleInitializer := true,
    meltcManageCompilerDeps         := false,
    meltcCompilerClasspath          := (codegen.jvm / Compile / fullClasspath).value.files
  )
  .enablePlugins(ScalaJSPlugin, MeltcPlugin, AutomateHeaderPlugin)
  .dependsOn(runtime.js)

// ── Example: Media Binding (M-5 — bind:currentTime / paused / volume etc.) ───
lazy val `media-binding` = project
  .in(file("examples/media-binding"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name                            := "media-binding",
    publish / skip                  := true,
    scalaJSUseMainModuleInitializer := true,
    meltcManageCompilerDeps         := false,
    meltcCompilerClasspath          := (codegen.jvm / Compile / fullClasspath).value.files
  )
  .enablePlugins(ScalaJSPlugin, MeltcPlugin, AutomateHeaderPlugin)
  .dependsOn(runtime.js)

// ── Example: Dimension Binding (M-3 — bind:clientWidth / offsetWidth etc.) ───
lazy val `dimension-binding` = project
  .in(file("examples/dimension-binding"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name                            := "dimension-binding",
    publish / skip                  := true,
    scalaJSUseMainModuleInitializer := true,
    meltcManageCompilerDeps         := false,
    meltcCompilerClasspath          := (codegen.jvm / Compile / fullClasspath).value.files
  )
  .enablePlugins(ScalaJSPlugin, MeltcPlugin, AutomateHeaderPlugin)
  .dependsOn(runtime.js)

// ── Example: Dynamic Element (Phase 0 — melt:element) ────────────────────────
lazy val `dynamic-element` = project
  .in(file("examples/dynamic-element"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name                            := "dynamic-element",
    publish / skip                  := true,
    scalaJSUseMainModuleInitializer := true,
    meltcManageCompilerDeps         := false,
    meltcCompilerClasspath          := (codegen.jvm / Compile / fullClasspath).value.files
  )
  .enablePlugins(ScalaJSPlugin, MeltcPlugin, AutomateHeaderPlugin)
  .dependsOn(runtime.js)

// ── Example: layoutEffect (Phase 13 — pre/post subscriber lanes) ─────────────
lazy val `layout-effect` = project
  .in(file("examples/layout-effect"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name                            := "layout-effect",
    publish / skip                  := true,
    scalaJSUseMainModuleInitializer := true,
    meltcManageCompilerDeps         := false,
    meltcCompilerClasspath          := (codegen.jvm / Compile / fullClasspath).value.files
  )
  .enablePlugins(ScalaJSPlugin, MeltcPlugin, AutomateHeaderPlugin)
  .dependsOn(runtime.js)

// ── Example: select / textarea bind:value (H-2) ───────────────────────────────
lazy val `select-textarea-bind` = project
  .in(file("examples/select-textarea-bind"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name                            := "select-textarea-bind",
    publish / skip                  := true,
    scalaJSUseMainModuleInitializer := true,
    meltcManageCompilerDeps         := false,
    meltcCompilerClasspath          := (codegen.jvm / Compile / fullClasspath).value.files
  )
  .enablePlugins(ScalaJSPlugin, MeltcPlugin, AutomateHeaderPlugin)
  .dependsOn(runtime.js)

// ── Example: Boundary (H-5 — melt:boundary / melt:pending / melt:failed / Await) ──
lazy val boundary = project
  .in(file("examples/boundary"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name                            := "boundary",
    publish / skip                  := true,
    scalaJSUseMainModuleInitializer := true,
    meltcManageCompilerDeps         := false,
    meltcCompilerClasspath          := (codegen.jvm / Compile / fullClasspath).value.files
  )
  .enablePlugins(ScalaJSPlugin, MeltcPlugin, AutomateHeaderPlugin)
  .dependsOn(runtime.js)

// ── Example: ReactiveScope (Phase 0 — resource management) ───────────────────
lazy val `reactive-scope` = project
  .in(file("examples/reactive-scope"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name                            := "reactive-scope",
    publish / skip                  := true,
    scalaJSUseMainModuleInitializer := true,
    meltcManageCompilerDeps         := false,
    meltcCompilerClasspath          := (codegen.jvm / Compile / fullClasspath).value.files,
    jsEnv                           := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv()
  )
  .enablePlugins(ScalaJSPlugin, MeltcPlugin, AutomateHeaderPlugin)
  .dependsOn(runtime.js, `melt-testkit` % Test)

// ── Example: TrustedHtml (raw HTML injection) ─────────────────────────────────
lazy val `trusted-html` = project
  .in(file("examples/trusted-html"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name                            := "trusted-html",
    publish / skip                  := true,
    scalaJSUseMainModuleInitializer := true,
    meltcManageCompilerDeps         := false,
    meltcCompilerClasspath          := (codegen.jvm / Compile / fullClasspath).value.files
  )
  .enablePlugins(ScalaJSPlugin, MeltcPlugin, AutomateHeaderPlugin)
  .dependsOn(runtime.js)

// ── Example: http4s SPA (pure client-side rendering) ────────────────────────
//
//   sbt "~http4s-spa-server/reStart"

lazy val `http4s-spa-client` = project
  .in(file("examples/http4s-spa/client"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name                            := "http4s-spa-client",
    publish / skip                  := true,
    scalaVersion                    := scala38,
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
    meltcManageCompilerDeps := false,
    meltcCompilerClasspath  := (codegen.jvm / Compile / fullClasspath).value.files,
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core"    % "0.14.9",
      "io.circe" %%% "circe-generic" % "0.14.9",
      "io.circe" %%% "circe-parser"  % "0.14.9"
    )
  )
  .enablePlugins(ScalaJSPlugin, MeltcPlugin, AutomateHeaderPlugin)
  .dependsOn(`meltkit-browser`)

lazy val `http4s-spa-server` = project
  .in(file("examples/http4s-spa/server"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name           := "http4s-spa-server",
    publish / skip := true,
    scalaVersion   := scala38,
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-server" % "0.23.33",
      "io.circe"   %% "circe-generic"       % "0.14.9"
    ),
    meltcAssetManifestClient := Some(`http4s-spa-client`)
  )
  .enablePlugins(MeltcPlugin, AutomateHeaderPlugin, RevolverPlugin)
  .dependsOn(runtime.jvm, `meltkit-adapter-http4s`.jvm)

// ── Shared SSR client (crossProject: JVM + JS) ─────────────────────────────
//
// Common .melt components shared by http4s-ssr, node-ssr, and jdk-ssr.
// JVM side: SSR HTML string rendering. JS side: hydration entries.

lazy val `ssr-client` = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("examples/ssr-client"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name                    := "ssr-client",
    publish / skip          := true,
    scalaVersion            := scala38,
    meltcManageCompilerDeps := false,
    meltcCompilerClasspath  := (codegen.jvm / Compile / fullClasspath).value.files,
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core"    % "0.14.9",
      "io.circe" %%% "circe-generic" % "0.14.9",
      "io.circe" %%% "circe-parser"  % "0.14.9"
    )
  )
  .enablePlugins(MeltcPlugin, AutomateHeaderPlugin)
  .dependsOn(meltkit)
  .jsConfigure(_.dependsOn(`meltkit-browser`))
  .jsSettings(
    meltcHydration                  := true,
    scalaJSUseMainModuleInitializer := false,
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.ESModule)
        .withModuleSplitStyle(
          ModuleSplitStyle.SmallModulesFor(List("components"))
        )
    }
  )

// ── Example: http4s SSR + Hydration ─────────────────────────────────────────
//
//   sbt "~http4s-ssr-server/reStart"

lazy val ssrClientDir = file("examples/ssr-client")

lazy val `http4s-ssr-server` = project
  .in(file("examples/http4s-ssr/server"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name           := "http4s-ssr-server",
    publish / skip := true,
    scalaVersion   := scala38,
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-server" % "0.23.33",
      "org.http4s" %% "http4s-dsl"          % "0.23.33",
      "io.circe"   %% "circe-generic"       % "0.14.9"
    ),
    meltcManageCompilerDeps  := false,
    meltcCompilerClasspath   := (codegen.jvm / Compile / fullClasspath).value.files,
    meltcAssetManifestClient := Some(`ssr-client`.js),
    meltcViteDistDir         := ssrClientDir / "dist",
    meltcViteManifestPath    := ssrClientDir / "dist" / ".vite" / "manifest.json"
  )
  .enablePlugins(MeltcPlugin, AutomateHeaderPlugin, RevolverPlugin)
  .dependsOn(`ssr-client`.jvm, `meltkit-adapter-http4s`.jvm)

// ── Example: Node.js SSR + Hydration (pure Scala, no cats-effect / http4s) ──
//
//   sbt "node-ssr-server/run"

lazy val `node-ssr-server` = project
  .in(file("examples/node-ssr/server"))
  .enablePlugins(ScalaJSPlugin, MeltcPlugin, AutomateHeaderPlugin)
  .settings(BuildSettings.commonSettings)
  .settings(
    name                            := "node-ssr-server",
    publish / skip                  := true,
    scalaVersion                    := scala38,
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
    jsEnv                           := new org.scalajs.jsenv.nodejs.NodeJSEnv(),
    meltMode                        := Some(Node),
    meltcManageCompilerDeps         := false,
    meltcManageRuntimeDeps          := false,
    meltcCompilerClasspath          := (codegen.jvm / Compile / fullClasspath).value.files,
    meltcAssetManifestClient        := Some(`ssr-client`.js),
    meltcViteDistDir                := ssrClientDir / "dist",
    meltcViteManifestPath           := ssrClientDir / "dist" / ".vite" / "manifest.json",
    // Include client shared sources so .melt files are compiled in SSR mode
    // (meltMode := Node) without duplicating them.
    Compile / unmanagedSourceDirectories +=
      (`ssr-client`.js / baseDirectory).value / ".." / "shared" / "src" / "main" / "scala",
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core"    % "0.14.9",
      "io.circe" %%% "circe-generic" % "0.14.9",
      "io.circe" %%% "circe-parser"  % "0.14.9"
    )
  )
  .dependsOn(`meltkit-node`)

// ── Example: JDK SSR + Hydration (pure Scala, no cats-effect / http4s) ──
//
//   sbt "jdk-ssr-server/run"

lazy val `jdk-ssr-server` = project
  .in(file("examples/jdk-ssr/server"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name           := "jdk-ssr-server",
    publish / skip := true,
    scalaVersion   := scala38,
    meltcManageCompilerDeps := false,
    meltcCompilerClasspath  := (codegen.jvm / Compile / fullClasspath).value.files,
    meltcAssetManifestClient := Some(`ssr-client`.js),
    meltcViteDistDir         := ssrClientDir / "dist",
    meltcViteManifestPath    := ssrClientDir / "dist" / ".vite" / "manifest.json"
  )
  .enablePlugins(MeltcPlugin, AutomateHeaderPlugin)
  .dependsOn(`ssr-client`.jvm, meltkit.jvm)

// ── Root (no publish) ──
lazy val root = project
  .in(file("."))
  .aggregate(
    `meltc-css`.jvm,
    `meltc-css`.js,
    `meltc-css`.native,
    `meltc-sass`,
    meltc.jvm,
    meltc.js,
    meltc.native,
    runtime.jvm,
    runtime.js,
    codegen.jvm,
    codegen.js,
    `melt-testkit`,
    meltkit.jvm,
    meltkit.js,
    `meltkit-browser`,
    `meltkit-node`,
    `meltkit-ssg`,
    `meltkit-adapter-http4s`.jvm,
    `meltkit-adapter-http4s`.js,
    `sbt-meltc`,
    `language-server`,
    `hello-world`,
    counter,
    `scss-counter`,
    `todo-app`,
    transitions,
    `special-elements`,
    `dynamic-element`,
    `layout-effect`,
    `select-textarea-bind`,
    `media-binding`,
    `dimension-binding`,
    `reactive-scope`,
    `trusted-html`,
    boundary,
    `http4s-spa-client`,
    `http4s-spa-server`,
    `ssr-client`.jvm,
    `ssr-client`.js,
    `http4s-ssr-server`,
    `node-ssr-server`,
    `jdk-ssr-server`
  )
  .settings(BuildSettings.commonSettings)
  .settings(
    publish / skip     := true,
    crossScalaVersions := Seq.empty, // root project does not cross-compile
    // meltcViteDistDir / meltcViteManifestPath are used only when meltcProd := true
    Global / excludeLintKeys ++= Set(meltcViteDistDir, meltcViteManifestPath)
  )
