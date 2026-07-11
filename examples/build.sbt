import org.scalajs.linker.interface.ModuleSplitStyle

import meltkit.sbt.MeltkitPlugin.autoImport._

val meltVersion = "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion  := "3.8.4"
ThisBuild / publish / skip := true

// ── Example: Hello World ──────────────────────────────────────────────────────
lazy val `hello-world` = project
  .in(file("hello-world"))
  .settings(
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies += "io.github.takapi327" %% "melt-runtime" % meltVersion
  )
  .enablePlugins(ScalaJSPlugin, MeltPlugin)

// ── Example: Counter (reactive bindings) ─────────────────────────────────────
lazy val counter = project
  .in(file("counter"))
  .settings(
    scalaJSUseMainModuleInitializer := true,
    jsEnv                           := Def.uncached(new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv()),
    libraryDependencies ++= Seq(
      "io.github.takapi327" %% "melt-runtime" % meltVersion,
      "io.github.takapi327" %% "melt-testkit" % meltVersion % Test
    )
  )
  .enablePlugins(ScalaJSPlugin, MeltPlugin)

// ── Example: SCSS Counter (SCSS support via sass-preprocessor) ────────────────────
lazy val `scss-counter` = project
  .in(file("scss-counter"))
  .settings(
    scalaJSUseMainModuleInitializer := true,
    meltStylePreprocessor          := Some(SassPreprocessor),
    libraryDependencies += "io.github.takapi327" %% "melt-runtime" % meltVersion
  )
  .enablePlugins(ScalaJSPlugin, MeltPlugin)

// ── Example: Todo App (multi-component) ──────────────────────────────────────
lazy val `todo-app` = project
  .in(file("todo-app"))
  .settings(
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies += "io.github.takapi327" %% "melt-runtime" % meltVersion
  )
  .enablePlugins(ScalaJSPlugin, MeltPlugin)

// ── Example: Transitions (transitions & animations) ───────────────────────────
lazy val transitions = project
  .in(file("transitions"))
  .settings(
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies += "io.github.takapi327" %% "melt-runtime" % meltVersion
  )
  .enablePlugins(ScalaJSPlugin, MeltPlugin)

// ── Example: Special Elements (melt:head / melt:window / melt:body) ───────────
lazy val `special-elements` = project
  .in(file("special-elements"))
  .settings(
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies += "io.github.takapi327" %% "melt-runtime" % meltVersion
  )
  .enablePlugins(ScalaJSPlugin, MeltPlugin)

// ── Example: Media Binding (bind:currentTime / paused / volume etc.) ──────────
lazy val `media-binding` = project
  .in(file("media-binding"))
  .settings(
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies += "io.github.takapi327" %% "melt-runtime" % meltVersion
  )
  .enablePlugins(ScalaJSPlugin, MeltPlugin)

// ── Example: Dimension Binding (bind:clientWidth / offsetWidth etc.) ───────────
lazy val `dimension-binding` = project
  .in(file("dimension-binding"))
  .settings(
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies += "io.github.takapi327" %% "melt-runtime" % meltVersion
  )
  .enablePlugins(ScalaJSPlugin, MeltPlugin)

// ── Example: Dynamic Element (melt:element) ───────────────────────────────────
lazy val `dynamic-element` = project
  .in(file("dynamic-element"))
  .settings(
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies += "io.github.takapi327" %% "melt-runtime" % meltVersion
  )
  .enablePlugins(ScalaJSPlugin, MeltPlugin)

// ── Example: Layout Effect (pre/post subscriber lanes) ────────────────────────
lazy val `layout-effect` = project
  .in(file("layout-effect"))
  .settings(
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies += "io.github.takapi327" %% "melt-runtime" % meltVersion
  )
  .enablePlugins(ScalaJSPlugin, MeltPlugin)

// ── Example: Select / Textarea bind:value ────────────────────────────────────
lazy val `select-textarea-bind` = project
  .in(file("select-textarea-bind"))
  .settings(
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies += "io.github.takapi327" %% "melt-runtime" % meltVersion
  )
  .enablePlugins(ScalaJSPlugin, MeltPlugin)

// ── Example: Boundary (melt:boundary / melt:pending / melt:failed / Await) ────
lazy val boundary = project
  .in(file("boundary"))
  .settings(
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies += "io.github.takapi327" %% "melt-runtime" % meltVersion
  )
  .enablePlugins(ScalaJSPlugin, MeltPlugin)

// ── Example: ReactiveScope (resource management) ─────────────────────────────
lazy val `reactive-scope` = project
  .in(file("reactive-scope"))
  .settings(
    scalaJSUseMainModuleInitializer := true,
    jsEnv                           := Def.uncached(new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv()),
    libraryDependencies ++= Seq(
      "io.github.takapi327" %% "melt-runtime" % meltVersion,
      "io.github.takapi327" %% "melt-testkit" % meltVersion % Test
    )
  )
  .enablePlugins(ScalaJSPlugin, MeltPlugin)

// ── Example: TrustedHtml (raw HTML injection) ─────────────────────────────────
lazy val `trusted-html` = project
  .in(file("trusted-html"))
  .settings(
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies += "io.github.takapi327" %% "melt-runtime" % meltVersion
  )
  .enablePlugins(ScalaJSPlugin, MeltPlugin)

// ── Example: http4s SPA (pure client-side rendering) ─────────────────────────
//
//   sbt "http4s-spa-server/run"

lazy val `http4s-spa-client` = project
  .in(file("http4s-spa/client"))
  .settings(
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
    libraryDependencies ++= Seq(
      "io.github.takapi327" %% "meltkit-adapter-browser" % meltVersion,
      "io.circe"            %% "circe-core"              % "0.14.9",
      "io.circe"            %% "circe-generic"           % "0.14.9",
      "io.circe"            %% "circe-parser"            % "0.14.9"
    )
  )
  .enablePlugins(ScalaJSPlugin, MeltPlugin)

lazy val `http4s-spa-server` = project
  .in(file("http4s-spa/server"))
  .settings(
    run / fork := true,
    libraryDependencies ++= Seq(
      "io.github.takapi327" %% "meltkit-adapter-http4s" % meltVersion,
      "org.http4s"          %% "http4s-ember-server"    % "0.23.33",
      "io.circe"            %% "circe-generic"          % "0.14.9"
    ),
    meltkitAssetManifestClient := Some(`http4s-spa-client`)
  )
  .enablePlugins(MeltkitPlugin)

// ── Shared SSR client (crossProject: JVM + JS) ───────────────────────────────
//
// Common .melt components shared by http4s-ssr, node-ssr, and jdk-ssr.
// JVM side: SSR HTML string rendering. JS side: hydration entries.

lazy val `ssr-client` = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("ssr-client"))
  .settings(
    libraryDependencies ++= Seq(
      "io.github.takapi327" %% "meltkit"       % meltVersion,
      "io.circe"            %% "circe-core"    % "0.14.9",
      "io.circe"            %% "circe-generic" % "0.14.9",
      "io.circe"            %% "circe-parser"  % "0.14.9"
    )
  )
  .enablePlugins(MeltPlugin)
  .jsConfigure(
    _.settings(
      libraryDependencies += "io.github.takapi327" %% "meltkit-adapter-browser" % meltVersion
    )
  )
  .jsSettings(
    meltHydration                  := true,
    scalaJSUseMainModuleInitializer := false,
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.ESModule)
        .withModuleSplitStyle(
          ModuleSplitStyle.SmallModulesFor(List("components"))
        )
    }
  )

// ── Example: http4s SSR + Hydration ──────────────────────────────────────────
//
//   sbt "http4s-ssr-server/run"

lazy val ssrClientDir = file("ssr-client")

lazy val `http4s-ssr-server` = project
  .in(file("http4s-ssr/server"))
  .settings(
    run / fork := true,
    libraryDependencies ++= Seq(
      "io.github.takapi327" %% "meltkit-adapter-http4s" % meltVersion,
      "org.http4s"          %% "http4s-ember-server"    % "0.23.33",
      "org.http4s"          %% "http4s-dsl"             % "0.23.33",
      "io.circe"            %% "circe-generic"          % "0.14.9"
    ),
    meltkitAssetManifestClient := Some(`ssr-client`.js),
    meltkitViteDistDir         := ssrClientDir / "dist",
    meltkitViteManifestPath    := ssrClientDir / "dist" / ".vite" / "manifest.json"
  )
  .enablePlugins(MeltkitPlugin)
  .dependsOn(`ssr-client`.jvm)

// ── Example: Node.js SSR + Hydration ─────────────────────────────────────────
//
//   sbt "node-ssr-server/run"

lazy val `node-ssr-server` = project
  .in(file("node-ssr/server"))
  .settings(
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
    jsEnv                      := Def.uncached(new org.scalajs.jsenv.nodejs.NodeJSEnv()),
    meltMode                   := Some(Node),
    meltkitAssetManifestClient := Some(`ssr-client`.js),
    meltkitViteDistDir         := ssrClientDir / "dist",
    meltkitViteManifestPath    := ssrClientDir / "dist" / ".vite" / "manifest.json",
    // Include shared .melt sources so they are compiled in SSR mode
    Compile / unmanagedSourceDirectories +=
      baseDirectory.value / ".." / ".." / "ssr-client" / "shared" / "src" / "main" / "scala",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core"    % "0.14.9",
      "io.circe" %% "circe-generic" % "0.14.9",
      "io.circe" %% "circe-parser"  % "0.14.9"
    )
  )
  .enablePlugins(ScalaJSPlugin, MeltkitPlugin)

// ── Example: JDK SSR + Hydration ─────────────────────────────────────────────
//
//   sbt "jdk-ssr-server/run"

lazy val `jdk-ssr-server` = project
  .in(file("jdk-ssr/server"))
  .settings(
    run / fork := true,
    meltkitAssetManifestClient := Some(`ssr-client`.js),
    meltkitViteDistDir         := ssrClientDir / "dist",
    meltkitViteManifestPath    := ssrClientDir / "dist" / ".vite" / "manifest.json"
  )
  .enablePlugins(MeltkitPlugin)
  .dependsOn(`ssr-client`.jvm)

// ── Example: Form actions + progressive enhancement ──────────────────────────
//
//   sbt "form-actions-server/run"

lazy val `form-actions-client` = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("form-actions-client"))
  .settings(
    libraryDependencies += "io.github.takapi327" %% "meltkit" % meltVersion
  )
  .enablePlugins(MeltPlugin)
  .jsConfigure(
    _.settings(
      libraryDependencies += "io.github.takapi327" %% "meltkit-adapter-browser" % meltVersion
    )
  )
  .jsSettings(
    meltHydration                   := true,
    scalaJSUseMainModuleInitializer := false,
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.ESModule)
        .withModuleSplitStyle(ModuleSplitStyle.SmallModulesFor(List("components")))
    }
  )

lazy val formActionsClientDir = file("form-actions-client")
lazy val `form-actions-server` = project
  .in(file("form-actions/server"))
  .settings(
    run / fork := true,
    libraryDependencies ++= Seq(
      "io.github.takapi327" %% "meltkit-adapter-http4s" % meltVersion,
      "org.http4s"          %% "http4s-ember-server"    % "0.23.33",
      "org.http4s"          %% "http4s-dsl"             % "0.23.33",
      "io.circe"            %% "circe-generic"          % "0.14.9"
    ),
    meltkitAssetManifestClient := Some(`form-actions-client`.js),
    meltkitViteDistDir         := formActionsClientDir / "dist",
    meltkitViteManifestPath    := formActionsClientDir / "dist" / ".vite" / "manifest.json"
  )
  .enablePlugins(MeltkitPlugin)
  .dependsOn(`form-actions-client`.jvm)

// ── Root ──────────────────────────────────────────────────────────────────────
lazy val root = project
  .in(file("."))
  .aggregate(
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
    `jdk-ssr-server`,
    `form-actions-client`.jvm,
    `form-actions-client`.js,
    `form-actions-server`
  )
  .settings(
    crossScalaVersions := Seq.empty
  )
