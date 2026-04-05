import Implicits._

ThisBuild / version            := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion       := ScalaVersions.scala3
ThisBuild / crossScalaVersions := Seq(ScalaVersions.scala3, ScalaVersions.scala38)

// ── コアコンパイラ（JVM + JS + Native）──
lazy val meltc = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Full)
  .module("meltc", "Core compiler: .melt → .scala")
  .settings(
    name := "meltc", // override "melt-meltc" → "meltc"
    libraryDependencies ++= Seq(
      "org.scalameta" %%% "munit" % "1.2.4" % Test,
    ),
  )
  .jsSettings(
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
  )
  .nativeSettings(
    // 将来の Native CLI 向け設定
  )

// ── sbt プラグイン ──
// Note: meltc.jvm（Scala 3）への dependsOn は Phase 3 で cross-compilation 設定後に追加予定。
lazy val `sbt-meltc` = BuildSettings.MeltSbtPluginProject("sbt-meltc", "modules/sbt-meltc")
  .settings(
    crossScalaVersions := Seq(ScalaVersions.scala2), // sbt プラグインは Scala 2.12 のみ
  )

// ── ランタイム（Scala.js ライブラリ）──
lazy val runtime = project
  .in(file("modules/runtime"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name := "melt-runtime",
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "2.8.1",
      "org.scalameta" %%% "munit"      % "1.2.4" % Test,
    ),
  )
  .enablePlugins(ScalaJSPlugin, AutomateHeaderPlugin)

// ── Language Server（LSP — 全エディタ共通）──
lazy val `language-server` = project
  .in(file("editors/language-server"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name := "melt-language-server",
    libraryDependencies ++= Seq(
      "org.eclipse.lsp4j" %  "org.eclipse.lsp4j" % "1.0.0",
      "org.scalameta"     %% "munit"              % "1.2.4" % Test,
    ),
  )
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(meltc.jvm)

// ── テストユーティリティ（Scala.js）──
lazy val `melt-testing` = project
  .in(file("modules/melt-testing"))
  .settings(BuildSettings.commonSettings)
  .settings(
    name := "melt-testing",
    libraryDependencies ++= Seq(
      "org.scalameta" %%% "munit" % "1.2.4",
    ),
  )
  .enablePlugins(ScalaJSPlugin, AutomateHeaderPlugin)
  .dependsOn(runtime)

// ── ルート（publish しない）──
lazy val root = project
  .in(file("."))
  .aggregate(
    meltc.jvm,
    meltc.js,
    meltc.native,
    `sbt-meltc`,
    runtime,
    `melt-testing`,
    `language-server`,
  )
  .settings(BuildSettings.commonSettings)
  .settings(
    publish / skip     := true,
    crossScalaVersions := Seq.empty, // ルートは cross-compile しない
  )
