# Melt

> フルスタックな Web アプリを、1 つの `.melt` ファイルで。

[![Continuous Integration](https://github.com/takapi327/melt/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/takapi327/melt/actions/workflows/ci.yml)
[![Apache 2.0 License](https://img.shields.io/badge/license-Apache%202.0-blue)](https://www.apache.org/licenses/LICENSE-2.0)
[![Scala Version](https://img.shields.io/badge/scala-v3.8.x-red)](https://github.com/scala/scala3)

Melt は **フルスタックな Web アプリを 1 つの `.melt` ファイルで書く** Scala.js 向け SFC（シングルファイルコンポーネント）フレームワークです。Scala・HTML・CSS を 1 ファイルに書くと、コンパイラがブラウザ向けの素の DOM コードと、サーバー向けの HTML 文字列を生成します。ダッシュボード・フォーム・コンテンツサイト・CRUD を、ブラウザからサーバーまで **同じコンポーネント・同じ型** で。

> [!NOTE]
> **Melt** は現在活発に開発中です。1.0 リリース前は API の破壊的変更が発生する可能性があります。

## 何が作れるか

同じ `.melt` から、SPA・サーバーレンダリング・静的サイトまで。各項目は動くサンプルへのリンクです。

| 作りたいもの | サンプル |
|---|---|
| **SPA + API** | [http4s-spa](examples/http4s-spa) |
| **SSR + Hydration** | [http4s-ssr](examples/http4s-ssr) |
| **Tailwind CSS（フルスタック SSR）** | [meltkit-app](examples/meltkit-app) |
| **フォーム / Server Actions** | [form-actions](examples/form-actions-client) |
| **Server Functions（型付き RPC）** | [server-functions](examples/server-functions) |
| **静的サイト（SSG）** | [nested-layouts](examples/nested-layouts)・この docs サイト自体 |
| **Node.js サーバー** | [node-ssr](examples/node-ssr) |
| **Todo / CRUD** | [todo-app](examples/todo-app) |

（全サンプルは [サンプル](#サンプル) を参照）

## こう書ける

```html
<!-- Counter.melt -->
<script lang="scala">
  val count = State(0)
</script>

<div>
  <button onclick={_ => count += 1}>Count: {count}</button>
</div>

<style>
button { font-size: 1.5rem; cursor: pointer; }
</style>
```

## なぜ Melt か

- **型検査を飛ばせない** — テンプレート内の式まで `.scala` になり、scalac が通らなければビルドが落ちる。typo や型の不一致は本番ではなくコンパイル時に失敗します
- **同じ `.melt` が SPA / SSR / SSG** — JVM では HTML 文字列、ブラウザでは生きた DOM。ハイドレーションはオプトイン
- **テンプレートに専用構文なし** — `{#if}` / `{#each}` のような独自構文は不要。`if`/`else`・`.map()` など素の Scala 式をそのまま使う
- **ランタイム最小・仮想 DOM なし** — `State` / `Signal` / `Memo` の小さなランタイムのみ。木の再描画も diff もない細粒度更新
- **明示的なリアクティビティ** — `effect` の依存値は必ず明示。暗黙トラッキングによる無限ループが起きない

## モジュール

| モジュール | JVM | JS | 説明 |
|---|:---:|:---:|---|
| `melt-preprocessor` | ✅ | ✅ | 汎用プリプロセッサ API |
| `melt-sass-preprocessor` | ✅ | ❌ | Dart Sass (SCSS) サポート |
| `melt-compiler` | ✅ | ✅ | コアコンパイラ（`.melt` → `.scala`） |
| `melt-runtime` | ✅ | ✅ | リアクティブランタイム |
| `melt-codegen` | ✅ | ✅ | コードジェネレータ |
| `melt-format` | ✅ | ❌ | `.melt` フォーマッタ（scalafmt 連携、JVM のみ） |
| `melt-testkit` | ❌ | ✅ | コンポーネントテストユーティリティ |
| `meltkit` | ✅ | ✅ | ルーティング DSL |
| `meltkit-adapter-browser` | ❌ | ✅ | ブラウザアダプタ |
| `meltkit-adapter-node` | ❌ | ✅ | Node.js サーバアダプタ |
| `meltkit-adapter-http4s` | ✅ | ✅ | http4s アダプタ |
| `sbt-melt` | ✅ | ❌ | sbt コンパイラプラグイン（Scala 3.8.4） |
| `sbt-meltkit` | ✅ | ❌ | sbt meltkit 統合プラグイン（Scala 3.8.4） |
| `melt-language-server` | ✅ | ❌ | LSP サーバ |

---

## `.melt` ファイル構文

`.melt` ファイルは 3 つのセクションで構成されます。

```html
<script lang="scala">
// Props: 親から受け取る外部入力
case class Props(title: String = "Hello")

// 内部状態: このコンポーネント固有
val count   = State(0)
val doubled = count.map(_ * 2)  // Signal[Int] — 読み取り専用の派生値
</script>

<div>
  <h1>{props.title}</h1>
  <p>Count: {count} / Doubled: {doubled}</p>
  <button onclick={_ => count += 1}>+1</button>
</div>

<style>
  h1 { color: #ff3e00; }
</style>
```

Melt は `{#if}` / `{#each}` のような独自構文を持たず、テンプレート内でも素の Scala 式（`if`/`else`・`.map` など）をそのまま使います。

### 使える機能（抜粋）

- **リアクティビティ** — `State` / `Signal` / `map` / `memo` / `effect` / `batch`
- **データバインディング** — `bind:value` / `checked` / `group` / `this`、メディア・要素寸法
- **コントロールフロー** — 素の `if`/`else`・`.map`・キー付きリスト（FLIP 対応）
- **イベント** — `onclick` ほか、`on:submit|preventDefault` の修飾子
- **演出** — トランジション（fade / fly / slide …）・アニメーション・`Tween` / `Spring`
- **コンポーネント合成** — Props・スニペット（コンテンツ投影）・ジェネリック
- **特殊要素** — `melt:head` / `melt:window` / `melt:body` / `melt:element` / `melt:key` / `melt:boundary`
- **サーバー** — SSR + ハイドレーション・ルーティング・Server Functions・フォームアクション

構文と API の詳細は、動くコードの [サンプル](#サンプル) と docs サイト（`sbt docsJVM/run` で起動、ソースは `docs/`）を参照してください。

---

## 使い方

### 1. sbt セットアップ

```scala
// project/plugins.sbt
addSbtPlugin("io.github.takapi327" % "sbt-melt" % "0.1.0-SNAPSHOT")

// build.sbt
enablePlugins(ScalaJSPlugin, MeltPlugin)

scalaVersion := "3.8.4"

libraryDependencies += "io.github.takapi327" %%% "melt-runtime" % "0.1.0-SNAPSHOT"

meltPackage   := "components"  // 生成コードのパッケージ
meltHydration := false         // SSR+Hydration を使う場合は true
```

### 2. コンポーネント作成

```
src/main/scala/components/App.melt
src/main/scala/components/Counter.melt
src/main/scala/components/TodoList.melt
```

### 3. エントリーポイント

```scala
// Main.scala
import org.scalajs.dom

object Main:
  def main(args: Array[String]): Unit =
    val root = dom.document.getElementById("app")
    components.App.mount(root)
```

### 4. ビルド

```bash
sbt fastLinkJS   # 開発用（ウォッチモード: sbt ~fastLinkJS）
sbt fullLinkJS   # 本番用
```

### SSR（サーバーサイドレンダリング）

```scala
// build.sbt — JVM ターゲット側で
libraryDependencies += "io.github.takapi327" %% "melt-runtime" % "0.1.0-SNAPSHOT"

meltCodegenMode := "ssr"
```

```scala
// サーバー側（http4s など）
import components.Home

val html = Home(Home.Props(userName = "Alice", count = 42))
Ok(html.body)
```

---

## サンプル

### 基礎

| サンプル | 説明 |
|---------|------|
| [hello-world](examples/hello-world) | 最小構成 |
| [counter](examples/counter) | リアクティブ状態・双方向バインディング・コンテキスト |
| [todo-app](examples/todo-app) | コンポーネント合成・スニペット・リスト操作 |

### スタイリング

| サンプル | 説明 |
|---------|------|
| [meltkit-app](examples/meltkit-app) | **Tailwind CSS** + フルスタック SSR。単一 `enablePlugins(MeltkitAppPlugin)` で frontend/backend を自動派生し、Tailwind CSS を `<head>` に inline |
| [scss-counter](examples/scss-counter) | SCSS（スコープ付きスタイル） |

### リアクティビティ・バインディング

| サンプル | 説明 |
|---------|------|
| [reactive-scope](examples/reactive-scope) | リソース管理・スコープ |
| [layout-effect](examples/layout-effect) | DOM 計測・レイアウトエフェクト |
| [dimension-binding](examples/dimension-binding) | 要素寸法バインディング |
| [media-binding](examples/media-binding) | メディア要素バインディング |
| [select-textarea-bind](examples/select-textarea-bind) | セレクト・テキストエリアバインディング |

### テンプレート・要素・演出

| サンプル | 説明 |
|---------|------|
| [special-elements](examples/special-elements) | `melt:head` / `melt:window` / `melt:body` / `melt:element` / `melt:key` |
| [dynamic-element](examples/dynamic-element) | 動的タグ名 |
| [trusted-html](examples/trusted-html) | 生 HTML 挿入（`TrustedHtml`） |
| [transitions](examples/transitions) | トランジション・アニメーション・FLIP |
| [boundary](examples/boundary) | Error Boundary・非同期レンダリング |

### ルーティング・レイアウト

| サンプル | 説明 |
|---------|------|
| [nested-layouts](examples/nested-layouts) | ネストレイアウト |
| [prefetch-app](examples/prefetch-app) | プリフェッチ付きルーティング |

### フルスタック（SPA / SSR / SSG）

| サンプル | 説明 |
|---------|------|
| [http4s-spa](examples/http4s-spa) | SPA + API（http4s） |
| [http4s-ssr](examples/http4s-ssr) | SSR + Hydration + API（http4s） |
| [http4s-components](examples/http4s-components) | http4s コンポーネント構成 |
| [ssr-client](examples/ssr-client) | SSR / ハイドレーション共有クライアント |
| [node-ssr](examples/node-ssr) | Node.js サーバー SSR |
| [jdk-ssr](examples/jdk-ssr) | 素の JDK SSR |

### サーバー機能

| サンプル | 説明 |
|---------|------|
| [form-actions](examples/form-actions-client) | フォーム / Server Actions |
| [server-functions](examples/server-functions) | Server Functions（型付き RPC） |
| [server-env](examples/server-env) | サーバー環境変数 |

### npm ライブラリ連携・可視化

| サンプル | 説明 |
|---------|------|
| [echarts-chart](examples/echarts-chart) | npm の **ECharts** を `@JSImport` で使う（SPA、Vite バンドル） |
| [echarts-ssr-server](examples/echarts-ssr-server) | ECharts + SSR + Hydration（ブラウザ専用ライブラリの island パターン） |

---

## 開発

```bash
# コンパイル
sbt compile

# テスト（全プラットフォーム）
sbt test

# JVM のみ
sbt runtimeJVM/test compilerJVM/test codegenJVM/test

# コードフォーマット
sbt scalafmtAll

# ヘッダーチェック
sbt headerCheckAll
```

### Scala バージョン

全モジュールを **Scala 3.8.4** に統一しています（sbt プラグインも同じく Scala 3.8.4 / sbt 2.0.0）。

### Java バージョン

Java 17 / 21 / 25（Corretto）で CI テストを実施しています。

---

## Contributing

コントリビューション歓迎です！

- [Issues](https://github.com/takapi327/melt/issues) から気になるものを選ぶか、新しい Issue を立ててください
- 質問や議論も Issue / Discussion でお気軽にどうぞ

### ローカルでのテスト

```bash
# 全テスト
sbt test

# scripted テスト（sbt プラグイン）
sbt sbt-melt/scripted
```

---

## ライセンス

Apache 2.0 — 詳細は [LICENSE](LICENSE) を参照してください。
