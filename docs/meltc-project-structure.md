# Meltc Project Structure

> Meltc プロジェクトの内部構成・ビルド・テスト・CI
>
> Status: Draft
> Last Updated: 2026-04-01

---

## 1. リポジトリ構成

### 1.1 Monorepo

全パッケージを1つのリポジトリで管理する。ビルドツールは sbt。

```
melt/
├── build.sbt                                ← 全サブプロジェクトの定義
├── project/
│   ├── build.properties                     ← sbt バージョン
│   └── plugins.sbt                          ← sbt プラグイン
│
├── modules/
│   ├── meltc/                               ← コアコンパイラ（JVM / JS / Native）
│   │   ├── shared/src/                      ← 全プラットフォーム共通（コアロジック）
│   │   │   ├── main/scala/meltc/
│   │   │   │   ├── MeltCompiler.scala       ← エントリーポイント
│   │   │   │   ├── Parser.scala             ← .melt パーサー
│   │   │   │   ├── Ast.scala                ← 中間表現（AST）
│   │   │   │   ├── CodeGen.scala            ← AST → Scala コード生成
│   │   │   │   ├── CssScoper.scala          ← CSS スコーピング
│   │   │   │   └── Errors.scala             ← コンパイルエラー型
│   │   │   └── test/scala/meltc/
│   │   │       ├── ParserSpec.scala
│   │   │       ├── CodeGenSpec.scala
│   │   │       ├── CssScopeSpec.scala
│   │   │       └── IntegrationSpec.scala    ← .melt → .scala の結合テスト
│   │   ├── jvm/src/                         ← JVM 固有（今は空）
│   │   │   └── main/scala/meltc/
│   │   ├── js/src/                          ← JS 固有（将来の npm CLI 用。今は空）
│   │   │   └── main/scala/meltc/
│   │   └── native/src/                      ← Native 固有（将来の高速 CLI 用。今は空）
│   │       └── main/scala/meltc/
│   │
│   ├── sbt-meltc/                           ← sbt プラグイン
│   │   └── src/
│   │       ├── main/scala/meltc/sbt/
│   │       │   ├── MeltcPlugin.scala        ← プラグイン定義
│   │       │   ├── MeltcKeys.scala          ← sbt キー定義
│   │       │   └── MeltcTasks.scala         ← タスク実装
│   │       └── sbt-test/                    ← sbt scripted テスト
│   │           └── basic/
│   │               ├── build.sbt
│   │               ├── test
│   │               └── src/
│   │                   └── Counter.melt
│   │
│   ├── runtime/                             ← melt-runtime
│   │   └── src/
│   │       ├── main/scala/melt/runtime/
│   │       │   ├── Var.scala
│   │       │   ├── Signal.scala
│   │       │   ├── Bind.scala
│   │       │   ├── Managed.scala
│   │       │   ├── AsyncState.scala
│   │       │   ├── MeltEffect.scala
│   │       │   ├── Context.scala
│   │       │   ├── HtmlAttrs.scala          ← HTML 属性コンテナ
│   │       │   ├── HtmlProps.scala           ← HtmlProps 階層（Button, Input 等）
│   │       │   └── Extensions.scala         ← 演算子 + コレクションヘルパー
│   │       └── test/scala/melt/runtime/
│   │           ├── VarSpec.scala
│   │           ├── SignalSpec.scala
│   │           └── BindSpec.scala
│   │
│   └── melt-testing/                       ← テストユーティリティ
│       └── src/main/scala/melt/testing/
│           ├── MeltSuite.scala             ← テストベースクラス
│           └── MountedComponent.scala      ← DOM クエリ API
│
├── examples/                                ← サンプルプロジェクト
│   ├── todo-app/
│   │   ├── build.sbt
│   │   ├── project/plugins.sbt
│   │   ├── package.json
│   │   ├── vite.config.js
│   │   └── src/
│   │       ├── components/
│   │       │   ├── App.melt
│   │       │   ├── TodoList.melt
│   │       │   └── TodoItem.melt
│   │       ├── stores/
│   │       │   └── TodoStore.scala
│   │       └── shared/
│   │           └── Models.scala
│   └── counter/
│       ├── build.sbt
│       └── src/
│           └── Counter.melt
│
├── editors/                                 ← エディタサポート
│   ├── language-server/                     ← melt-language-server（Scala / LSP）
│   │   └── src/main/scala/meltc/lsp/
│   │       ├── MeltLanguageServer.scala     ← LSP エントリーポイント
│   │       ├── VirtualFileGenerator.scala   ← .melt → 仮想 .scala 生成
│   │       ├── HtmlDummyTransformer.scala   ← HTML タグのダミー変換
│   │       └── PositionMapper.scala         ← 仮想 .scala ↔ .melt の行番号マッピング
│   │
│   ├── vscode/                              ← VS Code 拡張
│   │   ├── package.json
│   │   ├── syntaxes/
│   │   │   └── melt.tmLanguage.json         ← TextMate grammar
│   │   └── src/
│   │       └── extension.ts                 ← LSP クライアント接続
│   │
│   ├── neovim/                              ← Neovim サポート
│   │   ├── queries/                         ← Tree-sitter クエリ
│   │   │   ├── highlights.scm
│   │   │   └── injections.scm
│   │   └── lua/
│   │       └── melt.lua                     ← LSP 接続設定
│   │
│   └── intellij/                            ← IntelliJ プラグイン
│       ├── build.gradle.kts
│       └── src/main/kotlin/
│           └── MeltPlugin.kt               ← LSP4IJ 経由 or 独自プラグイン
│
├── docs/                             ← ドキュメント
│   ├── design.md                     ← デザインドキュメント
│   ├── getting-started.md
│   └── api-reference.md
│
├── .github/
│   └── workflows/
│       ├── ci.yml                    ← CI（テスト + ビルド）
│       └── release.yml               ← リリース（Maven Central publish）
│
├── .scalafmt.conf                    ← コードフォーマッタ設定
├── .scalafix.conf                    ← Linter 設定
├── LICENSE
└── README.md
```

### 1.2 パッケージの依存関係

```
meltc（コア — 依存なし）
  ├── meltc.jvm    ── sbt-meltc（sbt プラグイン）
  ├── meltc.jvm    ── melt-language-server（LSP — Metals 連携）
  ├── meltc.js     ── [将来] npm CLI
  └── meltc.native ── [将来] ネイティブ CLI（高速コールドスタート）

melt-runtime（scala-js-dom に依存）
  │
  ├── melt-testing（テストユーティリティ）
  ├── [将来] melt-forms（Iron ベースのフォームライブラリ）
  ├── [将来] melt-cats-effect（melt-runtime + cats-effect に依存）
  └── [将来] melt-zio（melt-runtime + zio に依存）

melt-language-server（meltc.jvm + LSP4J に依存）
  │
  ├── vscode 拡張（LSP クライアント）
  ├── neovim 設定（LSP クライアント）
  └── intellij プラグイン（LSP4IJ 経由）
```

`meltc`（コンパイラ）と `melt-runtime`（ランタイム）は互いに依存しない。コンパイラはランタイムの API 名をハードコードした文字列として生成するのみ。

---

## 2. ビルド設定

### 2.1 build.sbt

```scala
ThisBuild / organization := "io.github.takapi327"
ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.5.0"

// ── コアコンパイラ（JVM + JS + Native）──
lazy val meltc = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .in(file("modules/meltc"))
  .settings(
    name := "meltc",
    libraryDependencies ++= Seq(
      "org.scalameta" %%% "munit" % "1.0.0" % Test,
    ),
  )
  .jsSettings(
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
  )
  .nativeSettings(
    // Native 固有の設定（必要に応じて追加）
  )

// ── sbt プラグイン（JVM 版の meltc に依存）──
lazy val `sbt-meltc` = project
  .in(file("modules/sbt-meltc"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-meltc",
    sbtPlugin := true,
    scriptedLaunchOpts += s"-Dplugin.version=${version.value}",
    scriptedBufferLog := false,
  )
  .dependsOn(meltc.jvm)

// ── ランタイム（Scala.js ライブラリ）──
lazy val runtime = project
  .in(file("modules/runtime"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "melt-runtime",
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "2.8.0",
      "org.scalameta" %%% "munit"      % "1.0.0" % Test,
    ),
  )

// ── Language Server（LSP — 全エディタ共通）──
lazy val `language-server` = project
  .in(file("editors/language-server"))
  .settings(
    name := "melt-language-server",
    libraryDependencies ++= Seq(
      "org.eclipse.lsp4j" % "org.eclipse.lsp4j" % "0.22.0",
      "org.scalameta" %% "munit" % "1.0.0" % Test,
    ),
  )
  .dependsOn(meltc.jvm)

// ── テストユーティリティ（Scala.js）──
lazy val `melt-testing` = project
  .in(file("modules/melt-testing"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "melt-testing",
    libraryDependencies ++= Seq(
      "org.scalameta" %%% "munit" % "1.0.0",
    ),
  )
  .dependsOn(runtime)

// ── ルート（publish しない）──
lazy val root = project
  .in(file("."))
  .aggregate(meltc.jvm, meltc.js, meltc.native, `sbt-meltc`, runtime, `melt-testing`, `language-server`)
  .settings(
    publish / skip := true,
  )

// 将来の追加:
// lazy val catsEffect = project.in(file("modules/cats-effect"))...
// lazy val zio = project.in(file("modules/zio"))...
```

### 2.2 project/plugins.sbt

```scala
addSbtPlugin("org.scala-js"       % "sbt-scalajs"              % "1.16.0")
addSbtPlugin("org.scala-native"   % "sbt-scala-native"         % "0.5.4")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.3.2")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.3.2")
addSbtPlugin("org.scalameta"      % "sbt-scalafmt"             % "2.5.2")
addSbtPlugin("ch.epfl.scala"      % "sbt-scalafix"             % "0.12.1")
addSbtPlugin("com.github.sbt"     % "sbt-ci-release"           % "1.5.12")
```

---

## 3. 各パッケージの詳細

### 3.1 meltc（コアコンパイラ）

**責務:** `.melt` の文字列を受け取り、`.scala` の文字列を返す。純粋関数。ファイル I/O なし。

**構造:** `crossProject(JVMPlatform, JSPlatform, NativePlatform)` で3プラットフォーム対応。コアロジックは全て `shared/` に置く。各プラットフォーム固有のコードは現時点では不要だが、将来のCLI等で使う。

| プラットフォーム | 用途 | 状態 |
|---|---|---|
| JVM | sbt-meltc プラグインから参照 | 初期から使用 |
| JS | npm CLI / Vite プラグイン | 将来 |
| Native | 高速 CLI（コールドスタート ~5ms） | 将来 |

```scala
package meltc

/** meltc コンパイラのエントリーポイント */
object MeltCompiler:

  /** .melt ソースをコンパイルする */
  def compile(source: String, filename: String): CompileResult =
    val parsed = Parser.parse(source, filename)
    parsed match
      case Left(errors) => CompileResult.failure(errors)
      case Right(ast)   =>
        val scalaCode = CodeGen.generate(ast, filename)
        val scopedCss = CssScoper.scope(ast.style, ast.scopeId)
        CompileResult.success(scalaCode, scopedCss)

/** コンパイル結果 */
case class CompileResult(
  scalaCode: Option[String],
  scopedCss: Option[String],
  errors: List[CompileError],
  warnings: List[CompileWarning],
):
  def isSuccess: Boolean = errors.isEmpty

/** コンパイルエラー */
case class CompileError(
  message: String,
  line: Int,
  column: Int,
  filename: String,
)

/** コンパイル警告 */
case class CompileWarning(
  message: String,
  line: Int,
  column: Int,
  filename: String,
)
```

**テスト方針:** 入力 `.melt` → 期待される `.scala` のスナップショットテスト。

```scala
class CodeGenSpec extends munit.FunSuite:
  test("simple counter component") {
    val input = """
      |<script lang="scala" props="Props">
      |  case class Props(label: String)
      |  val count = Var(0)
      |</script>
      |
      |<div>{count}</div>
      |
      |<style></style>
      """.stripMargin

    val result = MeltCompiler.compile(input, "Counter.melt")
    assert(result.isSuccess)
    assert(result.scalaCode.get.contains("object Counter"))
    assert(result.scalaCode.get.contains("case class Props(label: String)"))
    assert(result.scalaCode.get.contains("Bind.text(count"))
  }
```

### 3.2 sbt-meltc（sbt プラグイン）

**責務:** `.melt` ファイルの検出・監視、meltc の呼び出し、生成 `.scala` ファイルの配置。`meltc.jvm` に依存。

```scala
package meltc.sbt

import sbt._
import sbt.Keys._

object MeltcPlugin extends AutoPlugin:
  override def requires = plugins.JvmPlugin
  override def trigger  = allRequirements

  object autoImport:
    val meltcSourceDirectory = settingKey[File]("Directory containing .melt files")
    val meltcOutputDirectory = settingKey[File]("Directory for generated .scala files")
    val meltcGenerate        = taskKey[Seq[File]]("Generate .scala from .melt files")

  import autoImport._

  override def projectSettings: Seq[Setting[_]] = Seq(
    meltcSourceDirectory := (Compile / sourceDirectory).value / "components",
    meltcOutputDirectory := (Compile / sourceManaged).value / "meltc",

    meltcGenerate := {
      val srcDir    = meltcSourceDirectory.value
      val outDir    = meltcOutputDirectory.value
      val log       = streams.value.log
      val meltFiles = (srcDir ** "*.melt").get

      meltFiles.map { meltFile =>
        val name   = meltFile.base
        val source = IO.read(meltFile)
        val result = MeltCompiler.compile(source, meltFile.name)

        result.errors.foreach { err =>
          log.error(s"${meltFile.name}:${err.line}: ${err.message}")
        }

        val outFile = outDir / s"$name.scala"
        result.scalaCode.foreach(code => IO.write(outFile, code))
        outFile
      }
    },

    // meltcGenerate を Compile の前に自動実行
    Compile / sourceGenerators += meltcGenerate.taskValue,
  )
```

ユーザーの `build.sbt`:

```scala
// project/plugins.sbt
addSbtPlugin("io.github.takapi327" % "sbt-meltc" % "0.1.0")

// build.sbt
enablePlugins(ScalaJSPlugin, MeltcPlugin)

scalaVersion := "3.5.0"
libraryDependencies += "io.github.takapi327" %%% "melt-runtime" % "0.1.0"

// デフォルト: src/main/components/*.melt → target/scala-3.5.0/src_managed/main/meltc/*.scala
// カスタマイズ:
// meltcSourceDirectory := baseDirectory.value / "src" / "views"
```

### 3.3 melt-runtime

**責務:** ユーザーの Scala.js コードと一緒にコンパイルされるランタイムライブラリ。

配布: Maven Central に Scala.js ライブラリとして publish。

ユーザーの `build.sbt`:
```scala
libraryDependencies += "io.github.takapi327" %%% "melt-runtime" % "0.1.0"
```

ユーザーの scala-cli:
```scala
//> using dep "io.github.takapi327::melt-runtime::0.1.0"
```

### 3.4 将来の拡張

`meltc` は初期段階から3プラットフォーム構造になっているため、CLI や npm 版の追加は各プラットフォーム固有ディレクトリにラッパーを書くだけで済む。`shared/` のコアロジックの変更は不要。

#### Scala Native CLI（高速コールドスタート）

```scala
// modules/meltc/native/src/main/scala/meltc/Main.scala（将来追加）
package meltc

object Main:
  def main(args: Array[String]): Unit =
    val command = args.headOption.getOrElse("help")
    command match
      case "generate" => // .melt → .scala 生成
      case "check"    => // 構文チェック
      case "watch"    => // ファイル監視モード
      case _          => println("Usage: meltc <generate|check|watch> [files]")
```

```bash
# Native バイナリをビルド
sbt meltcNative/nativeLink
# → modules/meltc/native/target/scala-3.5.0/meltc（ネイティブバイナリ）

# 起動 ~5ms。エディタ連携・CI に最適
./meltc generate src/Counter.melt
./meltc check src/Counter.melt
```

#### npm CLI（Node.js 環境向け）

```scala
// modules/meltc/js/src/main/scala/meltc/MeltcCli.scala（将来追加）
package meltc

import scala.scalajs.js.annotation.*

@JSExportTopLevel("meltc")
object MeltcCli:
  @JSExport
  def compile(source: String, filename: String): String =
    MeltCompiler.compile(source, filename).scalaCode.getOrElse("")
```

```bash
# JS 版をビルドして npm publish
sbt meltcJS/fullLinkJS
npm publish
```

---

## 4. テスト

### 4.1 テストフレームワーク

全パッケージで **MUnit** を使用する。Scala.js にも対応。

### 4.2 テスト戦略

| パッケージ | テスト種別 | 内容 |
|---|---|---|
| meltc | ユニットテスト | Parser / CodeGen / CssScoper 単体 |
| meltc | スナップショットテスト | `.melt` → `.scala` の結果を期待値と比較 |
| meltc | プロパティテスト | 任意の入力でクラッシュしないことを確認 |
| sbt-meltc | scripted テスト | 実際の sbt プロジェクトで動作確認 |
| runtime | ユニットテスト | Var / Signal / Bind の動作確認 |
| runtime | ブラウザテスト | DOM バインディングの実際の動作（jsdom or Selenium） |
| language-server | ユニットテスト | 仮想ファイル生成・位置マッピングの確認 |
| language-server | 統合テスト | LSP プロトコル経由での補完・エラー応答 |
| examples | E2E テスト | サンプルアプリがビルド・動作することを確認 |

### 4.3 スナップショットテスト

`meltc` のテストの中心。`.melt` ファイルと期待される `.scala` ファイルのペアを用意する。

```
modules/meltc/
└── shared/src/test/
    ├── scala/meltc/
    │   └── SnapshotSpec.scala
    └── resources/snapshots/
        ├── counter/
        │   ├── input.melt
        │   └── expected.scala
        ├── todo-app/
        │   ├── input.melt
        │   └── expected.scala
        ├── props-case-class/
        │   ├── input.melt
        │   └── expected.scala
        ├── props-named-tuple/
        │   ├── input.melt
        │   └── expected.scala
        ├── bind-value/
        │   ├── input.melt
        │   └── expected.scala
        └── pattern-match/
            ├── input.melt
            └── expected.scala
```

```scala
class SnapshotSpec extends munit.FunSuite:
  val snapshotDir = os.resource / "snapshots"

  for dir <- os.list(snapshotDir) if os.isDir(dir) do
    test(s"snapshot: ${dir.last}") {
      val input    = os.read(dir / "input.melt")
      val expected = os.read(dir / "expected.scala")
      val result   = MeltCompiler.compile(input, s"${dir.last}.melt")

      assert(result.isSuccess, s"Compile errors: ${result.errors}")
      assertEquals(result.scalaCode.get.trim, expected.trim)
    }
```

### 4.4 sbt scripted テスト

sbt プラグインの統合テスト。実際のプロジェクトでビルドが通ることを確認。

```
modules/sbt-meltc/
└── src/sbt-test/
    └── basic/
        ├── build.sbt
        ├── project/plugins.sbt
        ├── test                    ← scripted テストスクリプト
        └── src/main/
            ├── components/
            │   └── Counter.melt
            └── scala/
                └── Main.scala
```

```bash
# sbt-meltc/src/sbt-test/basic/test
> meltcGenerate
$ exists target/scala-3.5.0/src_managed/main/meltc/Counter.scala
> compile
> run
```

---

## 5. CI（GitHub Actions）

### 5.1 CI ワークフロー

```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: [main]
  pull_request:

jobs:
  test:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        java: [17, 21]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: ${{ matrix.java }}
          cache: sbt

      - name: Install Scala Native dependencies
        run: sudo apt-get update && sudo apt-get install -y clang libstdc++-12-dev

      - name: Check formatting
        run: sbt scalafmtCheckAll

      - name: Compile (JVM + JS + Native)
        run: sbt compile

      - name: Test (JVM + JS + Native)
        run: sbt test

      - name: Plugin scripted tests
        run: sbt sbt-meltc/scripted

      - name: Build Native binary
        run: sbt meltcNative/nativeLink

      - name: Build examples
        run: |
          cd examples/counter && sbt compile
          cd ../todo-app && sbt compile
```

### 5.2 リリースワークフロー

sbt-ci-release を使い、Git タグ push で Maven Central に自動 publish。

```yaml
# .github/workflows/release.yml
name: Release

on:
  push:
    tags: ["v*"]

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
          cache: sbt

      - name: Publish to Maven Central
        run: sbt ci-release
        env:
          PGP_PASSPHRASE: ${{ secrets.PGP_PASSPHRASE }}
          PGP_SECRET: ${{ secrets.PGP_SECRET }}
          SONATYPE_PASSWORD: ${{ secrets.SONATYPE_PASSWORD }}
          SONATYPE_USERNAME: ${{ secrets.SONATYPE_USERNAME }}
```

### 5.3 バージョニング

セマンティックバージョニングを採用。全パッケージのバージョンを統一する。

```
v0.1.0  ← 初回リリース（実験的）
v0.x.y  ← 破壊的変更あり
v1.0.0  ← 安定版（API 確定）
```

リリース手順:

```bash
git tag v0.1.0
git push origin v0.1.0
# → GitHub Actions がトリガーされ、Maven Central に自動 publish
```

---

## 6. 開発コマンド

```bash
# 全体ビルド
sbt compile

# 全テスト
sbt test

# meltc のテストのみ（JVM + JS + Native）
sbt meltc/test

# meltc プラットフォーム別
sbt meltcJVM/test
sbt meltcJS/test
sbt meltcNative/test

# runtime のテストのみ
sbt runtime/test

# sbt プラグインの scripted テスト
sbt sbt-meltc/scripted

# Native バイナリのビルド
sbt meltcNative/nativeLink

# コードフォーマット
sbt scalafmtAll

# Lint
sbt "scalafixAll --check"

# サンプルプロジェクトの動作確認
cd examples/todo-app
sbt ~compile           # .melt 監視 + 生成 + コンパイル（全自動）
npm run dev            # Vite 開発サーバー（別ターミナル）

# 全パッケージのローカル publish（例：サンプルプロジェクトでの動作確認用）
sbt publishLocal
```

---

## 7. IDE / エディタサポート

### 7.1 アーキテクチャ

Svelte と同じ方式。専用の Language Server が `.melt` ファイルの各セクションを適切な Language Server に振り分ける。

```
melt-language-server（LSP）
  │
  ├─ <script> 部分
  │    → 仮想 .scala ファイルを生成（メモリ上）
  │    → Metals に委譲（補完・型チェック・定義ジャンプ）
  │    → .melt の行番号にマッピングして返す
  │
  ├─ テンプレート部分
  │    ├─ HTML タグ・属性 → HTML Language Server に委譲
  │    └─ {} 内の Scala 式 → 仮想 .scala に挿入 → Metals に委譲
  │
  └─ <style> 部分
       → CSS Language Server に委譲
```

### 7.2 テンプレート内 `{}` の補完

`{}` 内は全て Scala の式なので、テンプレート専用の変換ルールがほぼ不要。HTML タグをダミー関数に置き換えるだけ。

```
テンプレート:
  {if count.map(_ > 10) then
    <p>Too many!</p>
  else
    <p>OK</p>}

   ↓ melt-language-server が変換

仮想 .scala:
  if count.map(_ > 10) then
    _html()       // <p> → ダミー
  else
    _html()       // <p> → ダミー
```

Svelte は `{#if}` / `{#each}` / `{#snippet}` 等の独自構文を TypeScript に変換する ~12 種類のルールが必要。Melt はテンプレート専用構文がゼロなので、変換ルールは **HTML ダミー化の1つだけ**。

### 7.3 仮想ファイル生成

`<script>` 内のコードとテンプレート内の `{}` 式を1つの仮想 `.scala` ファイルに合成し、Metals に渡す。

```scala
// melt-language-server が Counter.melt から生成する仮想ファイル

package generated
import melt.runtime.*

object Counter:
  case class Props(label: String, count: Int = 0)

  def create(props: Props): dom.Element =
    // <script> 内のコード
    val internal = Var(props.count)
    val doubled = internal.map(_ * 2)
    def increment(): Unit = internal += 1

    // テンプレート内の {} 式
    val _expr0 = props.label         // {props.label} → 補完が効く
    val _expr1 = internal            // {internal} → 補完が効く
    val _expr2 = if internal.map(_ > 10) then _html() else _html()

    ???
```

ユーザーがテンプレート内で `props.` と入力すると、仮想ファイル上の対応する位置で Metals が補完候補を返し、`.melt` の行番号にマッピングしてエディタに表示する。

### 7.4 位置マッピング

仮想 `.scala` の行番号と `.melt` の行番号の対応を保持する。エラーメッセージ・定義ジャンプ・ホバー表示で使用。

```
Counter.melt:15  →  仮想Counter.scala:22  → Metals が処理 → 結果を Counter.melt:15 に戻す
```

### 7.5 エディタ別の対応

| エディタ | ハイライト | 補完（LSP） | 実装 |
|---|---|---|---|
| VS Code | TextMate grammar | melt-language-server | `editors/vscode/` |
| Neovim | Tree-sitter | melt-language-server | `editors/neovim/` |
| Vim | coc.nvim 経由 | melt-language-server | LSP 設定のみ |
| IntelliJ | JetBrains プラグイン | LSP4IJ 経由 | `editors/intellij/` |

全エディタで同じ `melt-language-server` を共有。エディタ固有のコードはハイライト定義と LSP 接続設定のみ。

### 7.6 ハイライト

#### VS Code（TextMate grammar）

```json
{
  "scopeName": "source.melt",
  "patterns": [
    {
      "begin": "<script lang=\"scala\"[^>]*>",
      "end": "</script>",
      "contentName": "source.scala",
      "patterns": [{ "include": "source.scala" }]
    },
    {
      "begin": "<style[^>]*>",
      "end": "</style>",
      "contentName": "source.css",
      "patterns": [{ "include": "source.css" }]
    },
    {
      "name": "meta.embedded.expression.melt",
      "begin": "\\{",
      "end": "\\}",
      "contentName": "source.scala",
      "patterns": [
        { "include": "source.scala" },
        { "include": "text.html.basic" }
      ]
    },
    { "include": "text.html.basic" }
  ]
}
```

#### Neovim（Tree-sitter injections）

```scheme
; queries/injections.scm
((script_element
  (raw_text) @injection.content)
  (#set! injection.language "scala"))

((style_element
  (raw_text) @injection.content)
  (#set! injection.language "css"))

((expression
  (raw_text) @injection.content)
  (#set! injection.language "scala"))
```

### 7.7 Language Server の実装

```scala
// editors/language-server/src/main/scala/meltc/lsp/MeltLanguageServer.scala

package meltc.lsp

import org.eclipse.lsp4j.*
import meltc.MeltCompiler

class MeltLanguageServer:

  /** 補完要求を処理 */
  def completion(params: CompletionParams): CompletionList =
    val uri      = params.getTextDocument.getUri
    val position = params.getPosition
    val meltSource = readFile(uri)

    // 1. カーソル位置がどのセクションにあるか判定
    val section = detectSection(meltSource, position)

    section match
      case Section.Script =>
        // <script> 内 → 仮想 .scala を生成して Metals に委譲
        val virtual = VirtualFileGenerator.generate(meltSource, position)
        val mapped  = PositionMapper.toVirtual(position)
        delegateToMetals(virtual, mapped)

      case Section.Template(expr) =>
        // テンプレート内の {} → 仮想 .scala に式を挿入して Metals に委譲
        val virtual = VirtualFileGenerator.generateWithExpr(meltSource, expr, position)
        val mapped  = PositionMapper.toVirtual(position)
        delegateToMetals(virtual, mapped)

      case Section.Html =>
        // HTML タグ・属性 → HTML LS に委譲
        delegateToHtmlLS(meltSource, position)

      case Section.Style =>
        // CSS → CSS LS に委譲
        delegateToCssLS(meltSource, position)
```

### 7.8 フェーズ別ロードマップ

| フェーズ | 内容 | 工数 | 対象 |
|---|---|---|---|
| 1 | TextMate grammar（ハイライトのみ） | ~1日 | VS Code |
| 1 | Tree-sitter grammar | ~2-3日 | Neovim |
| 2 | melt-language-server + `<script>` 内補完 | ~2-4週間 | 全エディタ |
| 3 | テンプレート `{}` 内の補完 | ~2-4週間 | 全エディタ |
| 4 | 定義ジャンプ・リネーム・ホバー | ~2-4週間 | 全エディタ |
| 5 | IntelliJ プラグイン | ~4-8週間 | IntelliJ |

---

## 8. エンドユーザー向けプロジェクトテンプレート

ユーザーが `sbt new` で新しい Melt プロジェクトを作れるようにする（将来）。

```
melt/
└── templates/
    └── melt-vite.g8/              ← Giter8 テンプレート
        └── src/main/g8/
            ├── build.sbt
            ├── project/
            │   └── plugins.sbt
            ├── package.json
            ├── vite.config.js
            ├── index.html
            └── src/
                └── components/
                    └── App.melt
```

```bash
# ユーザーが新しいプロジェクトを作る
sbt new io.github.takapi327/melt-vite.g8
cd my-app
sbt ~meltcGenerate   # Terminal 1
npm run dev          # Terminal 2
```
