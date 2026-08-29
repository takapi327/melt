import org.scalajs.linker.interface.ModuleSplitStyle

import meltkit.sbt.MeltkitPlugin.autoImport._

val meltVersion = "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion   := "3.8.4"
ThisBuild / publish / skip := true
ThisBuild / scalacOptions += "-Werror"

// ── Example: Hello World ──────────────────────────────────────────────────────
lazy val `hello-world` = project
  .in(file("hello-world"))
  .settings(
    scalaJSUseMainModuleInitializer              := true,
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
    scalaJSUseMainModuleInitializer              := true,
    meltStylePreprocessor                        := Some(SassPreprocessor),
    libraryDependencies += "io.github.takapi327" %% "melt-runtime" % meltVersion
  )
  .enablePlugins(ScalaJSPlugin, MeltPlugin)

// ── Example: Todo App (multi-component) ──────────────────────────────────────
lazy val `todo-app` = project
  .in(file("todo-app"))
  .settings(
    scalaJSUseMainModuleInitializer              := true,
    libraryDependencies += "io.github.takapi327" %% "melt-runtime" % meltVersion
  )
  .enablePlugins(ScalaJSPlugin, MeltPlugin)

// ── Example: Transitions (transitions & animations) ───────────────────────────
lazy val transitions = project
  .in(file("transitions"))
  .settings(
    scalaJSUseMainModuleInitializer              := true,
    libraryDependencies += "io.github.takapi327" %% "melt-runtime" % meltVersion
  )
  .enablePlugins(ScalaJSPlugin, MeltPlugin)

// ── Example: Special Elements (melt:head / melt:window / melt:body) ───────────
lazy val `special-elements` = project
  .in(file("special-elements"))
  .settings(
    scalaJSUseMainModuleInitializer              := true,
    libraryDependencies += "io.github.takapi327" %% "melt-runtime" % meltVersion
  )
  .enablePlugins(ScalaJSPlugin, MeltPlugin)

// ── Example: Media Binding (bind:currentTime / paused / volume etc.) ──────────
lazy val `media-binding` = project
  .in(file("media-binding"))
  .settings(
    scalaJSUseMainModuleInitializer              := true,
    libraryDependencies += "io.github.takapi327" %% "melt-runtime" % meltVersion
  )
  .enablePlugins(ScalaJSPlugin, MeltPlugin)

// ── Example: ECharts chart via @JSImport (npm interop through Vite) ───────────
lazy val `echarts-chart` = project
  .in(file("echarts-chart"))
  .settings(
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
    libraryDependencies += "io.github.takapi327" %% "melt-runtime" % meltVersion
  )
  .enablePlugins(ScalaJSPlugin, MeltPlugin)

// ── Example: ECharts via @JSImport with SSR + Hydration (crossProject) ────────
// Proves a browser-only npm widget works in an SSR + hydration app: the shared
// component cross-compiles (JVM SSR + JS hydrate) by isolating the @JSImport
// echarts into the platform-split `ChartHost` (jvm no-op / js real).
lazy val `echarts-ssr` = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("echarts-ssr"))
  .settings(
    libraryDependencies += "io.github.takapi327" %% "melt-runtime" % meltVersion
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

// ── Example: single-declaration full-stack SSR app (MeltkitAppPlugin) ─────────
// One `enablePlugins(MeltkitAppPlugin)` derives `meltkit-appFrontend` (JS
// hydration) and `meltkit-appBackend` (JVM Undertow SSR). You manage only
// frontend/ and backend/ — no manual client crossProject + server wiring.
lazy val `meltkit-app` = project
  .in(file("meltkit-app"))
  .enablePlugins(MeltkitAppPlugin)
  .autoAggregate

// ── Example: compile-time type-safe links (MeltkitAppPlugin) ──────────────────
// Naming the routes object turns link checking on: every internal template link —
// static (`href="/users"`) or interpolated (`href="/users/{u.id}"`) — is validated
// against the shared `routes.Routes`, so a broken internal link is a compile error,
// not a runtime 404. No database required.
lazy val `typed-links` = project
  .in(file("typed-links"))
  .enablePlugins(MeltkitAppPlugin)
  // Naming the routes object IS the on switch: links compile to
  // `checkedRouteFor[routes.Routes.type]`, no `given RouteRegistry` boilerplate.
  // Applied to both derived frontend + backend.
  .sharedSettings(meltLinkCheckingRoutes := Some("routes.Routes"))

// ── Example: ECharts SSR + Hydration server (MeltKit[Future] on Undertow) ─────
lazy val echartsSsrDir = file("echarts-ssr")

lazy val `echarts-ssr-server` = project
  .in(file("echarts-ssr-server"))
  .settings(
    run / fork                                   := true,
    libraryDependencies += "io.github.takapi327" %% "meltkit" % meltVersion,
    meltkitAssetManifestClient                   := Some(`echarts-ssr`.js),
    meltkitViteDistDir                           := echartsSsrDir / "dist",
    meltkitViteManifestPath                      := echartsSsrDir / "dist" / ".vite" / "manifest.json"
  )
  .enablePlugins(MeltkitPlugin)
  .dependsOn(`echarts-ssr`.jvm)

// ── Example: Dimension Binding (bind:clientWidth / offsetWidth etc.) ───────────
lazy val `dimension-binding` = project
  .in(file("dimension-binding"))
  .settings(
    scalaJSUseMainModuleInitializer              := true,
    libraryDependencies += "io.github.takapi327" %% "melt-runtime" % meltVersion
  )
  .enablePlugins(ScalaJSPlugin, MeltPlugin)

// ── Example: Dynamic Element (melt:element) ───────────────────────────────────
lazy val `dynamic-element` = project
  .in(file("dynamic-element"))
  .settings(
    scalaJSUseMainModuleInitializer              := true,
    libraryDependencies += "io.github.takapi327" %% "melt-runtime" % meltVersion
  )
  .enablePlugins(ScalaJSPlugin, MeltPlugin)

// ── Example: Layout Effect (pre/post subscriber lanes) ────────────────────────
lazy val `layout-effect` = project
  .in(file("layout-effect"))
  .settings(
    scalaJSUseMainModuleInitializer              := true,
    libraryDependencies += "io.github.takapi327" %% "melt-runtime" % meltVersion
  )
  .enablePlugins(ScalaJSPlugin, MeltPlugin)

// ── Example: Select / Textarea bind:value ────────────────────────────────────
lazy val `select-textarea-bind` = project
  .in(file("select-textarea-bind"))
  .settings(
    scalaJSUseMainModuleInitializer              := true,
    libraryDependencies += "io.github.takapi327" %% "melt-runtime" % meltVersion
  )
  .enablePlugins(ScalaJSPlugin, MeltPlugin)

// ── Example: Boundary (melt:boundary / melt:pending / melt:failed / Await) ────
lazy val boundary = project
  .in(file("boundary"))
  .settings(
    scalaJSUseMainModuleInitializer              := true,
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
    scalaJSUseMainModuleInitializer              := true,
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
    meltHydration                   := true,
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
    run / fork                 := true,
    meltkitAssetManifestClient := Some(`ssr-client`.js),
    meltkitViteDistDir         := ssrClientDir / "dist",
    meltkitViteManifestPath    := ssrClientDir / "dist" / ".vite" / "manifest.json"
  )
  .enablePlugins(MeltkitPlugin)
  .dependsOn(`ssr-client`.jvm)

// ── Example: Form actions + progressive enhancement ──────────────────────────
//
//   sbt "form-actions/run"
//
// `MeltkitAppPlugin` on `MeltServerAdapter.Http4s`: one declaration derives
// `form-actions-frontend` (hydration bundle) and `form-actions-backend` (http4s
// SSR server). The adapter itself is added by the plugin; the http4s server and
// circe are the example's own choices, so they stay explicit.

lazy val `form-actions` = project
  .in(file("form-actions"))
  .enablePlugins(MeltkitAppPlugin)
  .autoAggregate
  .backendSettings(
    meltkitServerAdapter := MeltServerAdapter.Http4s,
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-server" % "0.23.33",
      "org.http4s" %% "http4s-dsl"          % "0.23.33",
      "io.circe"   %% "circe-generic"       % "0.14.9"
    )
  )

// ── Example: Type-safe Server Functions (query/command/single-flight/optimistic) ─
//
//   sbt "server-functions/run"
//
// `MeltkitAppPlugin` on `MeltServerAdapter.Http4s`, same shape as `form-actions`:
// `frontend/` holds the shared `Api` contract + components (compiled to JS for
// hydration and again inside the backend for SSR), `backend/` implements them.

lazy val `server-functions` = project
  .in(file("server-functions"))
  .enablePlugins(MeltkitAppPlugin)
  .autoAggregate
  .backendSettings(
    meltkitServerAdapter := MeltServerAdapter.Http4s,
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-server" % "0.23.33",
      "org.http4s" %% "http4s-dsl"          % "0.23.33",
      "io.circe"   %% "circe-generic"       % "0.14.9"
    )
  )

// ── Example: Nested layouts (SSR + router-driven hydration) ───────────────────
//
//   sbt "nested-layoutsJVM/run"   → writes static HTML under nested-layouts/dist
//
// Layout components (AppShell / DashboardLayout) are registered by prefix with
// app.layout(...) and compose each page during SSR. The JVM main generates a
// static site whose bootstrap is a single router-driven hydrate entry
// (routerHydration = Some("app")); the JS main (Main.scala) exports that entry,
// which calls BrowserAdapter.hydrate to claim the SSR DOM and take over routing.
lazy val `nested-layouts` = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("nested-layouts"))
  .settings(
    libraryDependencies += "io.github.takapi327" %% "meltkit" % meltVersion
  )
  .enablePlugins(MeltPlugin)
  .jvmSettings(
    run / fork := true
  )
  .jsConfigure(
    _.settings(
      libraryDependencies += "io.github.takapi327" %% "meltkit-adapter-browser" % meltVersion
    )
  )
  .jsSettings(
    meltHydration                   := true,
    scalaJSUseMainModuleInitializer := false,
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.ESModule))
  )

// ── Example: Route prefetch (SPA + server functions) ─────────────────────────
//
//   sbt "prefetch-app-server/run"   → http://localhost:8080
//
// A pure SPA (BrowserAdapter.mountWithShell) whose nav links carry
// `data-melt-preload`; hovering the Items link runs the registered
// `app.prefetch("items")` thunk (Api.items.prefetch()), warming a short-lived
// query cache the ItemsPage then adopts with no loading flash. The http4s server
// serves the shared `Api.items` server function (deliberately slow).
lazy val `prefetch-app` = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("prefetch-app"))
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
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.ESModule)
        .withModuleSplitStyle(ModuleSplitStyle.SmallModulesFor(List("components")))
    }
  )

lazy val prefetchAppDir        = file("prefetch-app")
lazy val `prefetch-app-server` = project
  .in(file("prefetch-app/server"))
  .settings(
    run / fork := true,
    libraryDependencies ++= Seq(
      "io.github.takapi327" %% "meltkit-adapter-http4s" % meltVersion,
      "org.http4s"          %% "http4s-ember-server"    % "0.23.33",
      "org.http4s"          %% "http4s-dsl"             % "0.23.33",
      "io.circe"            %% "circe-generic"          % "0.14.9"
    ),
    meltkitAssetManifestClient := Some(`prefetch-app`.js),
    meltkitViteDistDir         := prefetchAppDir / "dist",
    meltkitViteManifestPath    := prefetchAppDir / "dist" / ".vite" / "manifest.json"
  )
  .enablePlugins(MeltkitPlugin)
  .dependsOn(`prefetch-app`.jvm)

// ── Example: Server-only env boundary + typed env ────────────────────────────
//
//   sbt "server-env-server/run"   → http://localhost:8080
//
// The server reads a private value with meltkit.env.PrivateEnv (JVM-only, so the
// client cannot reference it — a real compile boundary) and returns only a derived
// non-secret greeting. `meltPublicEnv` generates a typed `PublicEnv` object the
// client reads for browser-safe config.
lazy val `server-env` = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("server-env"))
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
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.ESModule)
        .withModuleSplitStyle(ModuleSplitStyle.SmallModulesFor(List("components")))
    }
  )
  // Browser-safe public config → generated `PublicEnv`. Never put secrets here.
  .settings(meltPublicEnv := Map("appName" -> "Melt env demo", "apiBase" -> "/api"))

lazy val serverEnvDir        = file("server-env")
lazy val `server-env-server` = project
  .in(file("server-env/server"))
  .settings(
    run / fork := true,
    libraryDependencies ++= Seq(
      "io.github.takapi327" %% "meltkit-adapter-http4s" % meltVersion,
      "org.http4s"          %% "http4s-ember-server"    % "0.23.33",
      "org.http4s"          %% "http4s-dsl"             % "0.23.33",
      "io.circe"            %% "circe-generic"          % "0.14.9"
    ),
    meltkitAssetManifestClient := Some(`server-env`.js),
    meltkitViteDistDir         := serverEnvDir / "dist",
    meltkitViteManifestPath    := serverEnvDir / "dist" / ".vite" / "manifest.json"
  )
  .enablePlugins(MeltkitPlugin)
  .dependsOn(`server-env`.jvm)

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
    `echarts-chart`,
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
    `meltkit-app`,
    `form-actions`,
    `server-functions`,
    `nested-layouts`.jvm,
    `nested-layouts`.js,
    `prefetch-app`.jvm,
    `prefetch-app`.js,
    `prefetch-app-server`,
    `server-env`.jvm,
    `server-env`.js,
    `server-env-server`
  )
  .settings(
    crossScalaVersions := Seq.empty
  )
