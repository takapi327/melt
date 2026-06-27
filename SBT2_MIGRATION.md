# sbt 2 完全移行計画 (Scala 3 統一)

## Context

melt プロジェクトは現在 sbt 1.12.8 上で動作しており、sbt プラグイン (`sbt-melt`, `sbt-meltkit`) は Scala 2.12.21 でビルドされている。今回これを **sbt 2.0.0 (正式版、2026-06-14 リリース)** に完全移行し、プラグインも含めて **Scala 3** に統一する。

### なぜ今やるか
- sbt 2 は Scala 3 をプラグイン記述言語として第一級サポートし、本体ビルドと同じ言語で書ける
- sbt 2 の Coursier/タスクキャッシュ統合により、ビルド時間とディスク使用量が改善される
- ユーザー要件として「sbt 1 互換性は不要」「Scala バージョンを 3 系に統一」が明示された

### 既知の解消済みブロッカー
- `sbt-scalajs-crossproject` の sbt 2 対応 → 公式は `1.3.2` (2023-07-06) で止まっており未対応 → ローカルフォーク (`/Users/takapi327/Development/oss/scala/sbt-crossproject` の `sbt2` ブランチ) を継続使用
- `sbt-scalajs` の sbt 2 対応 → 公式 `1.22.0` が 2026-06-20 リリース済み
- `sbt` 本体 → **2.0.0 正式版** (2026-06-14) リリース済み、RC を使う必要なし

### 残る最大のリスク
- `ivyConfigurations` / `configurationFilter` (sbt-melt が `melt-compiler` 専用 Ivy Configuration を使って codegen JAR を fork 用 classpath に解決している) が sbt 2 (Coursier ベース) でそのまま動作するかは未確認。RFC-3 (カスタム設定削除) は部分受理・保留で 2.0.0 では削除されていないため動作する可能性は高いが、要動作確認
- 具体的な箇所 (`MeltPlugin.scala`):
  ```scala
  ivyConfigurations += MeltCompilerConfig                              // config("melt-compiler").hide
  meltCompilerClasspath := update.value.select(
    configurationFilter(MeltCompilerConfig.name)
  )
  ```

### 確認済みプラグイン最新バージョン (2026-06-24 時点)
| プラグイン | 最新版 | リリース日 | sbt 2 対応 |
|---|---|---|---|
| sbt | **2.0.0** | 2026-06-14 | (本体) |
| sbt-scalajs | 1.22.0 | 2026-06-20 | ✅ |
| sbt-scalajs-crossproject | 1.3.2 | 2023-07-06 | ❌ → ローカルフォーク使用 |
| sbt-scalafmt | 2.6.1 | 2026-05-10 | ✅ (2.6.0+ で対応) |
| sbt-scalafix | 0.14.7 | 2026-06-12 | ✅ |
| sbt-header (com.github.sbt) | 5.11.0 | 2025-09-12 | ✅ (現在使用中 `de.heikoseeberger` から移行必要) |
| sbt-github-actions | 0.31.0 | 2026-06-23 | ✅ (**0.31.0 必須**: sbt 2 でのセミコロン区切りコマンド生成バグを修正) |
| sbt-assembly | 2.3.1 | 2025-01-20 | ✅ (cross-publish 済み。shading 使用時は `exportJars := false` が必要) |
| sbt-boilerplate | 0.8.0 | 2025-12-13 | ✅ (cross-publish 済み) |
| sbt2-compat | **0.1.0** | 2026-02-10 | ✅ (`com.github.sbt` ─ Classpath 型移行用ライブラリ) |

### プラグイン suffix 命名規則 (重要)
sbt 2 + Scala 3 のプラグイン artifact suffix は **`_sbt2_3`**。sbt 1.x の `_2.12_1.0` と対応する。

```
# sbt 1.x + Scala 2.12
org.example:sbt-foo_2.12_1.0

# sbt 2.x + Scala 3
org.example:sbt-foo_sbt2_3
```

`pluginCrossBuild / sbtVersion := "2.0.0"` を設定すると sbt が自動的に `_sbt2_3` suffix でパブリッシュする。

---

## 影響範囲サマリー

| 種別 | パス |
|---|---|
| meta-build | `project/build.properties`, `project/Versions.scala`, `project/BuildSettings.scala`, `project/Implicits.scala`, `project/NoPublishPlugin.scala`, `project/plugins.sbt`, `project/build.sbt` |
| ルートビルド | `build.sbt` |
| プラグイン本体 | `plugins/sbt-melt/src/main/scala/melt/sbt/MeltPlugin.scala` (467 行) |
| | `plugins/sbt-melt/src/main/scala/melt/sbt/MeltSourceMap.scala` |
| | `plugins/sbt-melt/src/main/scala/melt/sbt/MeltGeneratedSource.scala` (**変更不要**) |
| | `plugins/sbt-meltkit/src/main/scala/meltkit/sbt/MeltkitPlugin.scala` (615 行) |
| プラグイン unit test | `plugins/sbt-melt/src/test/scala/melt/sbt/MeltGeneratedSourceSpec.scala` |
| | `plugins/sbt-melt/src/test/scala/melt/sbt/MeltSourceMapSpec.scala` |
| プラグイン meta-build | `plugins/sbt-melt/project/build.properties` (**新規作成**), `plugins/sbt-meltkit/project/build.properties` (**新規作成**), `plugins/sbt-meltkit/project/build.sbt` |
| Scripted tests | `plugins/sbt-melt/src/sbt-test/sbt-melt/hello-world/test` |
| | `plugins/sbt-meltkit/src/sbt-test/sbt-meltkit/hello-meltkit/test` |
| 外部依存 (ローカル) | `/Users/takapi327/Development/oss/scala/sbt-crossproject` (`sbt2` ブランチ) |

---

## Phase 0: ローカル sbt-crossproject フォークの仕上げ

**Path**: `/Users/takapi327/Development/oss/scala/sbt-crossproject` (`sbt2` ブランチ)

### 現状 (2026-06-27 再調査済み)
`sbt2` ブランチの最新コミットは「sbt-scalajs 1.22.0-SNAPSHOT を使った sbt 2 対応」であり、以下が**まだ未更新**：
- `sbt-scalajs` 依存: **`1.22.0-SNAPSHOT`** → **`1.22.0`** (正式リリース済み。CI が SNAPSHOT リポジトリに依存するため早急に更新が必要)
- `pluginCrossBuild / sbtVersion`: `"2.0.0-RC12"` → **`"2.0.0"`** に更新が必要
- `project/build.properties`: `sbt.version=1.12.9` → meta-build はまだ sbt 1.12.9

> ⚠️ SNAPSHOT 依存が残る限り、melt の CI が `~/.ivy2/local` のローカル publishLocal に依存し続ける。Phase 0 は他のすべての Phase より先に完了させること。

Scala 2/3 マクロ分離 (`src/main/scala-2/` / `src/main/scala-3/`) は `sbt-crossproject` 本体では完了しているが、`sbt-scalajs-crossproject` については要確認。

### 作業内容
1. `build.sbt` の `sbt-scalajs` 依存を `1.22.0-SNAPSHOT` → **`1.22.0`** に更新
2. `project/Extra.scala` の `pluginCrossBuild / sbtVersion`:
   - Scala 2.12 向けのブランチを削除 (sbt 2 + Scala 3 のみ)
   - `"2.0.0-RC12"` → **`"2.0.0"`** に更新
3. `crossScalaVersions` から Scala 2.12 を除外し、Scala 3 単独に
4. `src/main/scala-2/` ディレクトリの削除（Scala 3 だけ残す）
5. `sbt +publishLocal` 実行

### 検証
```bash
ls ~/.ivy2/local/org.portable-scala/sbt-scalajs-crossproject_sbt2_3/
```

---

## Phase 1: melt メタビルドを sbt 2 に更新

### ⚠️ sbt 2 ビルド定義の Scala バージョンについて
sbt 2.0.0 はビルド定義 (`project/` 内のコード) を **Scala 3.8.4** でコンパイルする。これはアプリ本体で使う `scala38 = "3.8.3"` とは異なるため、`project/BuildSettings.scala` 等は sbt が内部的に管理する 3.8.4 でコンパイルされることに注意する。

### 1.1 `project/build.properties`
```diff
- sbt.version=1.12.8
+ sbt.version=2.0.0
```

### 1.2 `project/Versions.scala`
```diff
object ScalaVersions {
- val scala2  = "2.12.21"
  val scala3  = "3.3.7"
  val scala38 = "3.8.3"
}
```

### 1.3 `project/plugins.sbt`
全プラグインを sbt 2 / Scala 3 対応バージョンに更新 (2026-06-24 時点で確認済み):

| プラグイン | 現バージョン | 移行後 |
|---|---|---|
| `org.scala-js:sbt-scalajs` | 1.21.0 | **1.22.0** |
| `org.portable-scala:sbt-scalajs-crossproject` | 1.3.2 | **ローカル `publishLocal` 版** |
| `org.scalameta:sbt-scalafmt` | 2.5.6 | **2.6.1** |
| `ch.epfl.scala:sbt-scalafix` | 0.14.6 | **0.14.7** |
| `de.heikoseeberger:sbt-header` | 5.10.0 | **`com.github.sbt:sbt-header:5.11.0`** |
| `com.github.sbt:sbt-github-actions` | 0.24.0 | **0.31.0** |
| `com.eed3si9n:sbt-assembly` | 2.3.1 | **2.3.1** (cross-publish 済み) |
| `com.github.sbt:sbt-boilerplate` | 0.8.0 | **0.8.0** (cross-publish 済み) |

> `sbt2-compat` はプラグインの `addSbtPlugin` ではなく `libraryDependencies` として追加する（後述 Phase 3 参照）。

### 1.4 `project/BuildSettings.scala`
`MeltSbtPluginProject` を sbt 2 / Scala 3 向けに更新:

```diff
// import
-import sbt._
-import sbt.plugins.SbtPlugin
-import sbt.Keys._
-import sbt.ScriptedPlugin.autoImport._
-import de.heikoseeberger.sbtheader.{ AutomateHeaderPlugin, CommentBlockCreator, CommentStyle }
-import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport._
-import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport.HeaderPattern.commentBetween
+import sbt.{given, *}
+import sbt.plugins.SbtPlugin
+import sbt.Keys.*
+import sbt.ScriptedPlugin.autoImport.*
+import sbtheader.{ AutomateHeaderPlugin, CommentBlockCreator, CommentStyle }
+import sbtheader.HeaderPlugin.autoImport.*
+import sbtheader.HeaderPlugin.autoImport.HeaderPattern.commentBetween

// メソッドシグネチャ
-def scriptedSettings: Seq[Setting[_]] = Seq(
+def scriptedSettings: Seq[Setting[?]] = Seq(

-def commonSettings: Seq[Setting[_]] = Def.settings(
+def commonSettings: Seq[Setting[?]] = Def.settings(

// MeltSbtPluginProject
  object MeltSbtPluginProject {
    def apply(name: String, dir: String): Project =
      Project(name, file(dir))
-       .settings(scalaVersion := scala2)
+       .settings(scalaVersion := scala3)
+       .settings(pluginCrossBuild / sbtVersion := "2.0.0")
        .settings(commonSettings)
        .settings(scriptedSettings)
        .enablePlugins(SbtPlugin, AutomateHeaderPlugin)
  }
```

### 1.5 `project/Implicits.scala`
`BuildSettings.scala` と同様に import を更新する:

```diff
-import sbt._
-import sbt.Keys._
+import sbt.{given, *}
+import sbt.Keys.*

-import de.heikoseeberger.sbtheader.AutomateHeaderPlugin
+import sbtheader.AutomateHeaderPlugin
```

`implicit class` と `implicit def` は Scala 3 では `extension` / `given` に書き換えることもできるが、互換性のある `implicit` 構文のままでも Scala 3.8.4 でコンパイル可能なので、まずは import 変更のみで対応する。

### 1.6 `project/NoPublishPlugin.scala`
```diff
-def noPublishSettings: Seq[Setting[_]] = Seq(
+def noPublishSettings: Seq[Setting[?]] = Seq(
```
`project/BuildSettings.scala` と同様に existential type を修正する。

### 1.7 `project/build.sbt`
```scala
// 現在
libraryDependencies += "org.scala-js" %% "scalajs-env-jsdom-nodejs" % "1.1.0"
```
この meta-build 依存は sbt 2 のビルド定義 (Scala 3.8.4) から参照される。`%%` が Scala 3 対応版 (`_3`) を解決できるか確認し、必要に応じて最新バージョンに更新する。

**⚠️ 事前確認が必要な事項:**  
`scalajs-env-jsdom-nodejs` の `_3` 版が Maven Central に存在しない場合は、以下のいずれかの対応が必要:
- `%` + explicit artifact 指定 (`cross CrossVersion.disabled` または `cross CrossVersion.for3Use2_13`)
- 別の `%%% ` 形式での宣言
- バージョンアップ（公式が Scala 3 対応版をリリースしていれば最新版を使う）

`sbt reload` 時に `unresolved dependency` が出た場合はこの依存を疑うこと。

### 検証
```bash
cd /Users/takapi327/Development/oss/scala/melt
sbt reload
sbt projects
```

---

## Phase 2: ルート `build.sbt` の調整

### ⚠️ bare settings の破壊的変更
sbt 2 では **トップレベルの bare settings（スコープなし設定）が全サブプロジェクトに伝播する**。現在の `build.sbt` は大半が `ThisBuild /` スコープか `.settings(...)` 内に収まっているが、全箇所を確認してスコープを明示する。

```scala
// sbt 2 で全プロジェクトに伝播する例 → LocalRootProject でスコープ明示
// NG (bare):  publish / skip := true
// OK:         LocalRootProject / publish / skip := true
// または:      root プロジェクトの .settings(...) 内に書く
```

### 作業内容

**`sbt-melt` / `sbt-meltkit` の設定変更:**
```diff
lazy val `sbt-melt` = MeltSbtPluginProject("sbt-melt", "plugins/sbt-melt")
  .settings(
-   crossScalaVersions := Seq(ScalaVersions.scala2),
    libraryDependencies += "org.scalameta" %% "munit" % "1.3.0" % Test
  )

lazy val `sbt-meltkit` = MeltSbtPluginProject("sbt-meltkit", "plugins/sbt-meltkit")
  .dependsOn(`sbt-melt`)
  .settings(
-   crossScalaVersions := Seq(ScalaVersions.scala2),
-   libraryDependencies += Defaults.sbtPluginExtra(
-     "org.scala-js" % "sbt-scalajs" % "1.21.0",
-     (pluginCrossBuild / sbtBinaryVersion).value,
-     (update / scalaBinaryVersion).value
-   )
+   addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")
  )
```

**`Defaults.sbtPluginExtra` の sbt 2 対応 (確認済み):**  
`Defaults.sbtPluginExtra` は sbt 2 でも API として残っているが、今回は Scala 2/3 のクロスビルドを廃止して Scala 3 single-target にするため、よりシンプルな `addSbtPlugin(...)` を `build.sbt` の `.settings(...)` 内に直接書く形式を採用する。

> **参考**: `(pluginCrossBuild / sbtBinaryVersion).value` は sbt 2.0.0 では `"2.0"` を返す。`addSbtPlugin` はこの値を自動的に使って `_sbt2_3` suffix でプラグインを解決するため、明示的に渡す必要はない。

**その他の作業:**
- `ThisBuild / crossScalaVersions := Seq(scala3, scala38)` は維持
- bare settings を全洗い出しし、必要に応じてスコープ明示
- GitHub Actions workflow 定義の `scala` マトリクスから Scala 2.12 を除外（`Workflows.scala` の `sbtScripted` ジョブは `scala3` 単独なので変更不要）

### ⚠️ CI コマンド形式の変更
sbt 2 では複数コマンドを **セミコロン区切りで一つの引数** として渡す形式を必要とする:

```bash
# sbt 1.x (sbt 2 では動作しない)
sbt clean compile test

# sbt 2.x
sbt "clean ; compile ; test"
```

`sbt-github-actions 0.31.0` を使えば `WorkflowStep.Sbt(List(...))` から生成される GitHub Actions YAML は自動で正しい形式になる。`WorkflowStep.Run(List("sbt publishLocal"))` のような単一コマンドは変更不要。

---

## Phase 3: `sbt-melt` プラグインの Scala 3 / sbt 2 対応

**Path**: `plugins/sbt-melt/src/main/scala/melt/sbt/MeltPlugin.scala` (467 行)

### 3.1 シグネチャ・import の更新
```diff
-import sbt._
-import sbt.Keys._
+import sbt.{given, *}
+import sbt.Keys.*

-override def projectSettings: Seq[Setting[_]] = Seq(
+override def projectSettings: Seq[Setting[?]] = Seq(
```

### 3.2 `sbt2-compat` の追加

「sbt 1 互換性は不要」のため、sbt 2 ネイティブな書き方でも書けるが、`sbt2-compat` (`com.github.sbt:sbt2-compat:0.1.0`) の convenience API を使うと変換コードが簡潔になる。

> **注意**: `sbt2-compat` は `addSbtPlugin` ではなく `libraryDependencies` として追加する。`project/plugins.sbt` ではなく、`build.sbt` の `sbt-melt` プロジェクト設定内に追加する。

```scala
// build.sbt の sbt-melt 設定に追加
lazy val `sbt-melt` = MeltSbtPluginProject("sbt-melt", "plugins/sbt-melt")
  .settings(
    libraryDependencies += "com.github.sbt" %% "sbt2-compat" % "0.1.0",
    libraryDependencies += "org.scalameta"  %% "munit"       % "1.3.0" % Test
  )
```

### 3.3 Classpath 型の対応 (sbt 2 の最大の差分)
sbt 2 では `Classpath = Seq[Attributed[xsbti.HashedVirtualFileRef]]`。

`MeltPlugin.scala` で `meltCompilerClasspath` を `Seq[File]` として返しているため、変換が必要:

```scala
// sbt2-compat 使用 (推奨)
// 正確な import パス (確認済み: sbt2-compat の package は sbtcompat)
import sbtcompat.PluginCompat.*
// toFiles(cp)(using fileConverter.value) で Seq[File] を取得

// または手書き
given conv: xsbti.FileConverter = fileConverter.value
val files: Seq[File] = classpath.value.map(af => conv.toPath(af.data).toFile)
```

`meltCompilerClasspath` タスクの戻り値型 `Seq[File]` は変えずに、タスク内での変換で対応する。

> **注意**: `fileConverter` は sbt 2 ネイティブのタスクキー。`implicit val conv = fileConverter.value` と書いてもよいが、Scala 3 では `given conv: xsbti.FileConverter = fileConverter.value` の形式が推奨される。

### 3.4 `ivyConfigurations` / `configurationFilter` (要動作確認)
`MeltPlugin.scala` の核心部分:

```scala
private val MeltCompilerConfig = config("melt-compiler").hide
ivyConfigurations += MeltCompilerConfig
libraryDependencies += ("io.github.takapi327" % "melt-codegen_3" % pluginVersion
  cross CrossVersion.disabled) % MeltCompilerConfig
meltCompilerClasspath := update.value.select(configurationFilter(MeltCompilerConfig.name))
```

**調査済み事項 (2026-06-27):**
- `ivyConfigurations` キー: sbt 2 develop ブランチの `Keys.scala` にも残存。削除されていない
- `configurationFilter`: `Defaults.scala` でも `autoCompilerPlugins` の実装等で同パターンが現役使用中
- ただし `config("melt-compiler").hide` の `.hide` (isPublic=false) は Ivy 固有の概念。Coursier でどう扱われるかは未確認

**⚠️ `update.value.select()` の戻り値型 (追加リスク):**
sbt 2 では `UpdateReport#select(ConfigurationFilter)` の戻り値が `Seq[File]` ではなく `Seq[HashedVirtualFileRef]` を返す可能性がある。現在の `meltCompilerClasspath` は `Seq[File]` を返すタスクなので、`update.value.select(...)` の結果から `File` への変換が別途必要になる場合がある。Phase 3.3 の変換ロジック (`sbt2-compat` の `toFiles` または `fileConverter`) を `ivyConfigurations` パスでも適用することを想定すること。

- **方針 A (試行優先)**: sbt 2 でそのまま動くか `sbt --debug update` で確認
- **方針 B (動かない場合)**: Coursier ベースで `melt-codegen_3` を個別解決する関数に書き換え

### 3.5 `FileFunction.cached` の置き換え

現在の `MeltPlugin.scala` での使用箇所 (L325-410):
```scala
// コンパイラ classpath のハッシュを cacheDir に組み込んでいる
val cpFingerprint: String = {
  // compilerCp の内容をハッシュ化 → コンパイラ更新時にキャッシュが自動無効化される
  ...
}
val cacheDir = streams.cacheDirectory / "melt" / safeKey / cpFingerprint

val cachedCompile = FileFunction.cached(cacheDir, FilesInfo.hash, FilesInfo.exists) {
  (_: Set[File]) =>
    // ... compile logic ...
    Set(outFile)
}
cachedCompile(Set(meltFile)).toSeq
```

**⚠️ 単純な `Def.uncached` 置き換えでは `cpFingerprint` ロジックが失われる:**

現在の実装は `cpFingerprint` を `cacheDir` のパスに含めることで「コンパイラ (`melt-codegen`) 自体を更新した際に生成済み `.scala` を自動的に再生成する」仕組みを実現している。`Def.uncached` でラップするだけでは sbt 2 のタスクキャッシュが `.melt` ファイルの変更のみを監視し、コンパイラ更新を検知しない。

**推奨する移行方針:**

sbt 2 では `FileFunction.cached` が廃止され、代わりに `Def.task` のキャッシュ機構と `sbt.nio.Digest` ベースの入力ハッシュが使われる。以下の方針で対応する:

```scala
// sbt 2 での置き換え案
// 1. FileFunction.cached を除去
// 2. compileMeltFiles の引数に compilerCp を渡しているため、
//    sbt 2 の Def.task キャッシュが compilerCp の変化を自動検出する
//    (Seq[File] 型のタスク入力はハッシュ対象になる)
// 3. Def.uncached で副作用を明示
meltGenerate := Def.uncached {
  compileMeltFiles(
    ...,
    compilerCp = meltCompilerClasspath.value  // cp が変わればタスク全体が再実行される
  )
}.value
```

sbt 2 では `meltCompilerClasspath.value` (= `Seq[File]`) がタスク入力として宣言されているため、classpath の内容が変わるとタスクキャッシュが無効化される。`cpFingerprint` を手動計算する必要はなくなる見込みだが、動作確認の際に「コンパイラを `publishLocal` → 再コンパイルで `.melt` が再生成されるか」を必ず検証すること。

**`Def.uncached` の正確なシグネチャ (sbt 2 ソース確認済み):**
```scala
// sbt/main-settings/src/main/scala/sbt/Def.scala
inline def uncached[A1](inline a: A1): A1 = Uncached(a)
```
`inline` マクロによるマーカー関数。タスク定義全体を `Def.uncached { ... }` でラップするだけでよい。追加 import は不要（`sbt.Def` は通常 import 済み）。

sbt2-compat は sbt 1.x 向けに `def uncached[A](a: A): A = a`（no-op）を提供し、sbt 2 ではネイティブ実装が使われる。`import sbtcompat.PluginCompat.*` で両バージョン対応が可能。

### 3.6 `Fork.java` の API 確認
`MeltPlugin.scala` で使用:
```scala
val exitCode = Fork.java(ForkOptions(), javaArgs)
```
`Fork.java` と `ForkOptions` が sbt 2 で変わっていないか確認する。

### 3.7 `xsbti.Position` / `xsbti.Problem` / `xsbti.Reporter` の確認

**`xsbti.Position` (リスク: 低)**  
`MeltPlugin.scala` と `MeltSourceMap.scala` の匿名クラス `override def sourceFile(): Optional[java.io.File]`:
- sbt 2 (Zinc 2.x) では `xsbti.Position.sourceFile()` は **`Optional[java.io.File]` のまま変更なし** (調査済み)
- ただし Zinc 2 で `xsbti.Position` インターフェースに新しい抽象メソッドが追加された場合、匿名クラスの実装が不完全になりコンパイルエラーになる → コンパイル時に確認

**`xsbti.Problem` 匿名クラス (リスク: 中)**  
`MeltPlugin.scala` L452-465 の `mkProblem(...)` は `xsbti.Problem` を匿名クラスで実装している:
```scala
private def mkProblem(...): xsbti.Problem = new xsbti.Problem {
  override def category(): String = ...
  override def severity(): Severity = ...
  override def message(): String = ...
  override def position(): xsbti.Position = ...
}
```
Zinc 2 では `xsbti.Problem` に `diagnosticCode()` や `diagnosticRelatedInformation()` 等のメソッドが追加された可能性がある。これらにデフォルト実装がなければコンパイルエラーになる。**コンパイル時に未実装抽象メソッドがないか確認すること。**

**`bspReporter` キーと `xsbti.Reporter#log` (リスク: 中)**  
`MeltPlugin.scala` L253, L391, L398:
```scala
val reporter: xsbti.Reporter = (Compile / compile / bspReporter).value
// ...
reporter.log(mkProblem(...))
```
- `bspReporter` キーが sbt 2 で移動・名称変更されていないか確認する
- `xsbti.Reporter#log(xsbti.Problem)` のシグネチャが Zinc 2 で変わっていないか確認する
- 変更されていた場合、コンパイルエラーで検出できるため対応コストは低い

### 3.8 `plugins/sbt-melt/project/build.properties` 新規作成

`plugins/sbt-melt/project/build.properties` は**現在存在しない**。  
`plugins/sbt-meltkit/project/build.sbt` から `ProjectRef(file("../../sbt-melt"), "sbt-melt")` で参照されるため、sbt-melt を独立ビルドとして読み込む際の sbt バージョンが明示されない。以下の内容で**新規作成**する:
```
sbt.version=2.0.0
```

### 3.9 `MeltGeneratedSource.scala`
`sbt._` の import が一切なく、`java.io.File` と純粋 Scala のみ使用しているため **変更不要**。Scala 3 (sbt 2 のプラグイン言語) でそのままコンパイルできる。

### 3.10 ユニットテスト (`MeltGeneratedSourceSpec.scala` / `MeltSourceMapSpec.scala`)

`plugins/sbt-melt/src/test/scala/melt/sbt/` に以下が存在する（計画書未記載）:
- `MeltGeneratedSourceSpec.scala`
- `MeltSourceMapSpec.scala`

これらは現在 Scala 2.12 でコンパイルされているが、sbt 2 移行後は Scala 3 でコンパイルされる。テスト対象の `MeltGeneratedSource` は Pure Scala なので基本的に問題ないが、コンパイルが通ることを確認すること。

```bash
sbt "project sbt-melt" test
```

### 検証
```bash
sbt "project sbt-melt" compile
sbt "project sbt-melt" test
sbt "project sbt-melt" scripted
```

---

## Phase 4: `sbt-meltkit` プラグインの Scala 3 / sbt 2 対応

**Path**: `plugins/sbt-meltkit/src/main/scala/meltkit/sbt/MeltkitPlugin.scala` (615 行)

### 4.1 シグネチャ・import (Phase 3.1 と同様)
```diff
-import sbt._
-import sbt.Keys._
+import sbt.{given, *}
+import sbt.Keys.*
-Seq[Setting[_]]
+Seq[Setting[?]]
```

### 4.2 Scala.js linker output の型対応

`MeltkitPlugin.scala` では `scalaJSLinkerOutputDirectory` の値を以下の3箇所で `File` として使用している:

```scala
// L388: distDir を File として受け取り IO.copyFile / generateAssetManifest に渡す
val distDir = (clientProject / Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value
IO.copyFile(src, distDir / "index.html")          // File操作
generateAssetManifest(..., distDir = distDir, ...) // generateAssetManifest は File 引数

// L362 (fullLinkJS): generateViteInputs にも同様に渡す
val distDir = (clientProject / Compile / fullLinkJS / scalaJSLinkerOutputDirectory).value
```

`sbt-scalajs 1.22.0` でこのタスクの戻り値型が `File` から `VirtualFileRef` 系に変わった場合は、上記3箇所すべてに `fileConverter.value` を使った変換が必要になる（Phase 3.3 と同様のパターン）。

`Report.publicModules` の型が変わっていないかも合わせて確認すること。これが `MeltkitPlugin.scala` で `Phase 4` 全体の最大リスク。

### 4.3 `Report` / `StandardConfig` の import
```diff
-import org.scalajs.linker.interface._
-import ScalaJSPlugin.autoImport._
+import org.scalajs.linker.interface.*
+import ScalaJSPlugin.autoImport.*
```

### 4.4 `sbt-meltkit` の依存関係構造 (重要: 現状確認)

**現在の構造（調査済み）:**
- `plugins/sbt-meltkit/build.sbt` は**存在しない**
- `sbt-meltkit` は ROOT の `build.sbt` で `.dependsOn(`sbt-melt`)` として定義されている
- `plugins/sbt-meltkit/project/build.sbt` (meta-build) は `ProjectRef(file("../../sbt-melt"), "sbt-melt")` を使用

```scala
// 現在の root build.sbt
lazy val `sbt-meltkit` = MeltSbtPluginProject("sbt-meltkit", "plugins/sbt-meltkit")
  .dependsOn(`sbt-melt`)  // unmanagedClasspath ではなく標準の依存
  .settings(...)
```

Phase 4.4 で必要な作業は `unmanagedClasspath` のパス変更ではなく、以下：
- ROOT `build.sbt` の `sbt-meltkit` 定義の `crossScalaVersions` 削除（Phase 2 で実施）
- ROOT `build.sbt` の `Defaults.sbtPluginExtra` の sbt 2 対応確認（Phase 2 参照）

### 4.5 `Def.taskDyn` + `sourceGenerators` の動作確認

`MeltkitPlugin.scala` では `Def.taskDyn` を使った動的タスクが `sourceGenerators` に追加されている:
```scala
// MeltkitPlugin.scala L334, L355, L371
meltkitConfigGenerate := Def.taskDyn { ... }.value
meltkitAssetManifestGenerate := Def.taskDyn { ... }.value

// L353, L405
Compile / sourceGenerators += meltkitConfigGenerate.taskValue
Compile / sourceGenerators += meltkitAssetManifestGenerate.taskValue
```

sbt 2 では `Def.taskDyn` は引き続き使用可能だが、タスクグラフのキャッシュ機構が変わっているため、動的依存の評価タイミングが変わる可能性がある。コンパイルとリンクが正常に通ることを scripted テストで確認する。リスクは低いが盲点になりやすい。

### 4.6 `plugins/sbt-meltkit/project/build.sbt` (meta-build)
```scala
lazy val root = (project in file("."))
  .dependsOn(ProjectRef(file("../../sbt-melt"), "sbt-melt"))
```
`ProjectRef` はそのまま動作するはずだが、**`project/build.properties`** の sbt バージョンを `2.0.0` に更新：
```diff
- sbt.version=1.12.8
+ sbt.version=2.0.0
```

> **現状確認済み**: `plugins/sbt-meltkit/project/build.properties` は**存在しない**。新規作成が必要:
> ```
> sbt.version=2.0.0
> ```

### 検証
```bash
sbt "project sbt-meltkit" compile
sbt "project sbt-meltkit" scripted
```

---

## Phase 5: Scripted テストの更新

**現在の scripted test ファイル（調査済み）:**

`plugins/sbt-melt/src/sbt-test/sbt-melt/hello-world/test`:
```
> meltGenerate
$ exists target/scala-3.3.7/src_managed/main/melt/components/App.scala
> compile
> fastLinkJS
```

`plugins/sbt-meltkit/src/sbt-test/sbt-meltkit/hello-meltkit/test`:
```
> meltGenerate
$ exists target/scala-3.8.3/src_managed/main/melt/components/App.scala
> compile
> fastLinkJS
```

### 5.1 `project/build.properties` (各 scripted test プロジェクト)

**⚠️ 存在確認が必要 — ファイルの有無を確認してから操作すること:**

| パス | 現状 | 対応 |
|---|---|---|
| `plugins/sbt-melt/src/sbt-test/sbt-melt/hello-world/project/build.properties` | **存在しない** | **新規作成** |
| `plugins/sbt-meltkit/src/sbt-test/sbt-meltkit/hello-meltkit/project/build.properties` | **存在しない** | **新規作成** |

両ファイルを以下の内容で作成する:
```
sbt.version=2.0.0
```

> scripted テストは各テストプロジェクトで `project/build.properties` がないと親 sbt のバージョンを引き継ぐため、明示的に `2.0.0` を指定するファイルが必要。

### 5.2 `project/plugins.sbt` (各 scripted test プロジェクト)
- `addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.21.0")` → `1.22.0`
- `addSbtPlugin("io.github.takapi327" % "sbt-melt" % v)` の解決パスが sbt 2 のローカル Ivy 配置 (`_sbt2_3`) に追従することを確認

### 5.3 `build.sbt` (各 scripted test プロジェクト)
- `scalaVersion := "3.3.7"` / `"3.8.3"` はそのまま (アプリ本体の Scala バージョンは変更不要)
- `%%%` を使った `melt-runtime` 依存の動作を確認する:
  ```scala
  // hello-world/build.sbt
  libraryDependencies += "io.github.takapi327" %%% "melt-runtime" % sys.props.getOrElse("plugin.version", "0.1.0-SNAPSHOT")
  ```
  sbt 2 + sbt-scalajs 1.22.0 での `%%%` が `_sjs1_3` suffix を正しく解決することを `sbt scripted` の実行で確認する。

### 5.4 `test` スクリプト (パス変更が必ず必要)
sbt 2 では target ディレクトリ構造が変わる:
```
# sbt 1.x
<subproject>/target/scala-<ver>/

# sbt 2.x  
target/out/jvm/scala-<ver>/<subproject>/
```

**hello-world/test の修正:**
```diff
- $ exists target/scala-3.3.7/src_managed/main/melt/components/App.scala
+ $ exists target/**/src_managed/main/melt/components/App.scala
```

**hello-meltkit/test の修正:**
```diff
- $ exists target/scala-3.8.3/src_managed/main/melt/components/App.scala
+ $ exists target/**/src_managed/main/melt/components/App.scala
```

sbt 2 の scripted では `exists` / `absent` コマンドが glob (`**`) をサポートすることを **sbt ソースコードで確認済み** (`FileCommands.scala` が `sbt.nio.file.Glob` と `RecursiveGlob` を使って実装されている)。バージョン依存のパスは glob に置き換えることで対応できる。

### 検証
```bash
sbt "project sbt-melt" scripted sbt-melt/hello-world
sbt "project sbt-meltkit" scripted sbt-meltkit/hello-meltkit
```

---

## Phase 6: フル統合テスト・CI 調整

### 6.1 ローカル統合テスト
```bash
sbt clean
sbt +publishLocal     # 全モジュール + プラグインを Scala 3 / sbt 2 形式で発行
sbt scripted          # 全 scripted テスト実行
sbt testFull          # 全ユニットテスト強制実行 (sbt 2 では sbt test はインクリメンタル)
```

> **注意**: sbt 2 では `sbt test` がキャッシュ付きインクリメンタルテストになる（変更された分だけ実行）。CI での全テスト実行には `sbt testFull` を使うこと（公式ドキュメントで明記）。

### 6.2 GitHub Actions workflow の調整
- `build.sbt` 内の `githubWorkflowBuild` Scala マトリクスから Scala 2.12 を除外 (Phase 2 で実施済み)
- `Workflows.sbtScripted` の `WorkflowStep.Run(List("sbt scripted"))` は単一コマンドなので変更不要
- プラグインのジョブも Scala 3 単独で動くことを確認

### 6.3 ローカルフォーク依存の解消方針
sbt-crossproject 公式リリースが sbt 2 対応するまではローカル `publishLocal` 依存。`README.md` または `CONTRIBUTING.md` に「Phase 0 を最初に実行する」旨を追記する。

---

## API 置換マッピング (チートシート)

| sbt 1 | sbt 2 |
|---|---|
| `import sbt._` | `import sbt.{given, *}` |
| `Seq[Setting[_]]` | `Seq[Setting[?]]` |
| `Classpath = Seq[Attributed[File]]` | `Seq[Attributed[xsbti.HashedVirtualFileRef]]` |
| `cp.map(_.data)` で File 取得 | `cp.toFiles` (sbt2-compat) または `conv.toPath(af.data).toFile` |
| `ivyConfigurations` (Ivy 専用) | 動けばそのまま / 動かなければ Coursier ベース置換 |
| `FileFunction.cached(...)` | `Def.uncached { ... }` (sbt 2 ネイティブ) |
| プラグイン suffix `_2.12_1.0` | **`_sbt2_3`** |
| `pluginCrossBuild / sbtVersion := "1.5.8"` | `:= "2.0.0"` |
| `(pluginCrossBuild / sbtBinaryVersion).value` | `"2.0"` を返す (sbt 2.0.0 時点) |
| `import sbtcompat.PluginCompat._` (sbt2-compat) | `import sbtcompat.PluginCompat.*` (package は `sbtcompat`、`com.github.sbt` ではない) |
| bare settings (暗黙的にルートのみ) | スコープ明示が必要 (全プロジェクトに伝播) |
| `sbt clean compile test` (CLI) | `sbt "clean ; compile ; test"` |
| `sbt test` (全テスト) | `sbt testFull` (全テスト強制実行) |
| `import de.heikoseeberger.sbtheader.*` | `import sbtheader.*` (`com.github.sbt:sbt-header` のパッケージは `sbtheader`) |
| `"org.foo" %%% "bar"` | `"org.foo" %% "bar"` (sbt 2 は `platform` キーで `_sjs1` suffix を自動付与するため `%%%` 不要) |
| scripted: `target/scala-X.Y/...` | `target/**/...` (glob) |

---

## 既存ユーティリティ・参照すべき先行事例

- `/Users/takapi327/Development/oss/scala/sbt-crossproject` — `CrossPluginCompat` の Scala 3 マクロ実装、`pluginCrossBuild` 設定例
- [sbt 2.0.0 移行ガイド](https://www.scala-sbt.org/2.x/docs/en/changes/migrating-from-sbt-1.x.html)
- [sbt 2.0 変更サマリー](https://www.scala-sbt.org/2.x/docs/en/changes/sbt-2.0-change-summary.html)
- [sbt test リファレンス](https://www.scala-sbt.org/2.x/docs/en/reference/sbt-test.html) — `testFull` の説明
- [sbt2-compat 移行ガイド](https://www.scala-lang.org/blog/2026/03/02/sbt2-compat.html) — `com.github.sbt:sbt2-compat:0.1.0`
- `sbt-scalajs 1.22.0` リリースノート — sbt 2 対応の差分、`Report` API 変更有無

---

## 検証 (エンドツーエンド)

| ステップ | コマンド | 期待結果 |
|---|---|---|
| 0. フォーク準備 | `cd ~/Development/oss/scala/sbt-crossproject && sbt +publishLocal` | `~/.ivy2/local/.../sbt-scalajs-crossproject_sbt2_3/` 配置 |
| 1. メタビルド reload | `sbt reload` | エラーなくロード成功 |
| 2. 全コンパイル | `sbt compile` | 全モジュールビルド成功 |
| 3. プラグイン scripted | `sbt scripted` | hello-world / hello-meltkit 両方パス |
| 4. ユニットテスト | `sbt testFull` | 既存テストすべて緑 |
| 5. ローカル publish | `sbt +publishLocal` | プラグインが `_sbt2_3` 形式で公開 |
| 6. examples 動作確認 | examples 配下で `sbt run` / `sbt fastLinkJS` 等 | 既存と同じ挙動 |
| 7. CI 緑化 | GitHub Actions push | 全マトリクスジョブ成功 |

---

## ロールバック方針

各 Phase は独立した PR / コミットに分割し、問題発生時は該当 Phase のみリバート可能にする:
- Phase 0: sbt-crossproject フォーク (リポジトリ別)
- Phase 1-2: meta-build & root build.sbt (1 PR)
- Phase 3: sbt-melt (1 PR)
- Phase 4: sbt-meltkit (1 PR)
- Phase 5-6: scripted + CI (1 PR)

「sbt 1 互換性は不要」というユーザー要件のため、cross-build による段階移行ではなく **clean cut** で進める。
