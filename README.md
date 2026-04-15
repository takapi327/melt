# Melt

> Scala を溶かして JS にするコンパイラ

Melt は Scala.js 向けのシングルファイルコンポーネント（SFC）フレームワークです。Svelte にインスパイアされた `.melt` ファイルに Scala・HTML・CSS を1ファイルで記述し、コンパイラが素の DOM 操作コードへ変換します。

```html
<!-- Counter.melt -->
<script lang="scala">
  val count = Var(0)
</script>

<div>
  <button onclick={_ => count += 1}>Count: {count}</button>
</div>

<style>
button { font-size: 1.5rem; cursor: pointer; }
</style>
```

## コンセプト

- **コンパイラがフレームワーク** — Svelte と同様、ランタイムフレームワーク不要。コンパイラが DOM 操作コードを直接生成
- **Scala の型システムを完全保持** — テンプレート内の式も含め、型チェックはすべて scalac が行う
- **ランタイムは最小限** — `Var` / `Signal` / `Bind` を提供する小さなランタイムのみ
- **SSR 対応** — 同じ `.melt` ファイルを JVM 側で HTML 文字列として出力可能（`CompileMode.SSR`）

## Status

> 実験的 — Phase 0〜9 完了、Phase 10 以降開発中

| フェーズ | 内容 | 状態 |
|---------|------|------|
| Phase 0 | モノレポスケルトン | ✅ 完了 |
| Phase 1 | `melt-runtime` コア（`Var` / `Signal`） | ✅ 完了 |
| Phase 2 | `meltc` パーサー | ✅ 完了 |
| Phase 3 | コード生成 + sbt プラグイン | ✅ 完了 |
| Phase 4 | リアクティブバインディング | ✅ 完了 |
| Phase 5 | コンポーネントシステム + CSS スコーピング | ✅ 完了 |
| Phase 6 | テンプレート完全対応 | ✅ 完了 |
| Phase 7 | ライフサイクル & 状態管理 | ✅ 完了 |
| Phase 8 | 高度な機能 | ✅ 完了 |
| Phase 9 | トランジション & アニメーション | ✅ 完了 |
| Phase 10 | テストキット（`melt-testkit`） | 🚧 開発中 |
| Phase 11 | IDE サポート / LSP | 🚧 開発中 |
| Phase 12+ | フォームライブラリ・ドキュメント・リリース | 📋 予定 |

実装フェーズの詳細は [docs/meltc-implementation-phases.md](docs/meltc-implementation-phases.md) を参照してください。

---

## モジュール構成

| モジュール | 説明 | プラットフォーム |
|-----------|------|----------------|
| `meltc` | `.melt` → `.scala` コンパイラ | JVM / JS / Native |
| `sbt-meltc` | sbt プラグイン（`.melt` 自動コンパイル） | JVM (Scala 2.12) |
| `melt-runtime` | リアクティブランタイム（`Var` / `Signal` / `Bind`） | JVM / JS |
| `melt-testkit` | コンポーネントテストユーティリティ | JS |
| `melt-language-server` | LSP サーバー（IDE 統合） | JVM |

---

## `.melt` ファイル構文

`.melt` ファイルは 3 つのセクションで構成されます。

```html
<script lang="scala" props="Props">
  <!-- Scala コード（Props 宣言を含む） -->
  case class Props(title: String = "Hello", count: Var[Int])
  val doubled = count.map(_ * 2)
</script>

<!-- HTML テンプレート -->
<div>
  <h1>{title}</h1>
  <p>Count: {count} / Doubled: {doubled}</p>
  <button onclick={_ => count += 1}>+1</button>
</div>

<style>
  /* CSS（自動スコーピング） */
  h1 { color: #ff3e00; }
</style>
```

### テンプレート構文

#### 式展開

```html
<p>{message}</p>
<p>{count * 2}</p>
<p>{if isActive then "active" else "inactive"}</p>
```

#### イベントハンドラー

```html
<button onclick={_ => count += 1}>+1</button>
<input onchange={e => name.set(e.target.value)} />
<!-- on: ディレクティブ（修飾子付き） -->
<form on:submit|preventDefault={_ => handleSubmit()}>...</form>
```

#### データバインディング

```html
<!-- テキスト入力 -->
<input bind:value={name} />
<input bind:value-int={age} />
<input bind:value-double={price} />

<!-- チェックボックス -->
<input type="checkbox" bind:checked={enabled} />

<!-- ラジオ / チェックボックスグループ -->
<input type="radio"    value="a" bind:group={selected} />
<input type="checkbox" value="x" bind:group={selectedItems} />

<!-- セレクトボックス -->
<select bind:value={selectedOption}>
  <option value="a">A</option>
  <option value="b">B</option>
</select>

<!-- textarea -->
<textarea bind:value={content}></textarea>

<!-- 要素参照 -->
<canvas bind:this={canvasRef}></canvas>

<!-- innerHTML / textContent -->
<div bind:innerHTML={trustedHtml}></div>
<div bind:textContent={text}></div>
```

#### クラス・スタイル・ディレクティブ

```html
<!-- 条件付きクラス -->
<div class:active={isActive} class:disabled={!isEnabled}>...</div>

<!-- 動的スタイル -->
<p style:color={textColor} style:font-size="{fontSize}px">...</p>

<!-- spread 属性 -->
<div {...attrs}></div>
```

#### トランジション・アニメーション

```html
<!-- 双方向トランジション -->
<div transition:fade>...</div>
<div transition:fly="{{x: 200, duration: 300}}">...</div>

<!-- 方向指定 -->
<div in:slide out:fade>...</div>

<!-- リストアニメーション（FLIP） -->
<div animate:flip="{{duration: 200}}">...</div>
```

利用可能なトランジション: `fade`, `slide`, `fly`, `scale`, `blur`, `crossfade`, `draw`

#### アクション

```html
<!-- カスタムアクション -->
<button use:tooltip="{{text: 'Click me'}}">...</button>
<input use:autofocus />
```

#### 特殊要素

```html
<!-- <head> コンテンツ -->
<melt:head>
  <title>{pageTitle}</title>
  <meta name="description" content={description} />
</melt:head>

<!-- ウィンドウイベント -->
<melt:window
  on:keydown={handleKeydown}
  bind:innerWidth={width}
  bind:scrollY={scrollY}
/>

<!-- body クラス・スタイル -->
<melt:body class:dark-mode={isDarkMode} />

<!-- 動的タグ -->
<melt:element this={tagName} class="wrapper">...</melt:element>
```

---

## Runtime API

### リアクティブプリミティブ

#### `Var[A]` — ミュータブルなリアクティブ変数

```scala
val count = Var(0)

// 読み取り
val current: Int = count.now()

// 更新
count.set(5)
count.update(_ + 1)

// 算術拡張メソッド
count += 1   // Int / Long / Double / String
count -= 1   // Int / Long / Double
count *= 2   // Int のみ

// コレクション拡張（Var[List[A]]）
items.append(newItem)
items.prepend(first)
items.removeWhere(_.id == id)
items.removeAt(0)
items.updateWhere(_.id == id)(_.copy(done = true))
items.clear()
items.sortBy(_.name)

// 派生
val doubled: Signal[Int] = count.map(_ * 2)
val text: Signal[String] = count.map(n => s"Count: $n")

// 購読
val unsubscribe = count.subscribe { n =>
  println(s"Changed to: $n")
}
unsubscribe() // 購読解除
```

#### `Signal[A]` — 読み取り専用のリアクティブ値

```scala
val doubled: Signal[Int] = count.map(_ * 2)
val frozen: Signal[Int] = Signal.pure(42)

doubled.now()
doubled.subscribe(n => println(n))
```

### エフェクト・メモ化

```scala
// エフェクト（DOM 更新後に実行）
effect(count) { n =>
  dom.document.title = s"Count: $n"
}

// レイアウトエフェクト（DOM 更新前に実行）
layoutEffect(count) { n =>
  val height = el.getBoundingClientRect().height
  height
}

// メモ化
val expensiveResult = memo(count) { n =>
  heavyComputation(n)
}
```

### ライフサイクル

```scala
onMount { () =>
  // コンポーネント DOM 挿入後に実行
  fetchData()
}

onCleanup { () =>
  // コンポーネント破棄時に実行
  subscription.cancel()
}
```

### コンテキスト API

```scala
// コンテキスト定義
val ThemeCtx = Context.create("light")

// 提供側（親コンポーネント）
ThemeCtx.provide("dark")

// 消費側（子コンポーネント）
val theme = ThemeCtx.inject()  // "dark"
```

### バッチ更新

```scala
batch {
  firstName.set("John")
  lastName.set("Doe")
  // DOM 更新は1回にまとめられる
}
```

### セキュリティユーティリティ

```scala
// HTML エスケープ（テキストコンテンツ用）
Escape.html("<script>alert(1)</script>")  // "&lt;script&gt;..."

// 属性エスケープ（改行・タブ含む）
Escape.attr(userInput)

// URL 検証（javascript:, vbscript:, file: をブロック、data: は data:image/* 以外すべてブロック）
Escape.url(hrefValue)  // 危険な URL は空文字列を返し警告を出す

// CSS 値エスケープ（expression(), @import 等をブロック）
Escape.cssValue(styleValue)

// 信頼済み値（エスケープをバイパス）
val safe = TrustedHtml("<strong>validated</strong>")
val url  = TrustedUrl.unsafe("javascript:trustedCode()")
```

---

## セキュリティ機能

コンパイル時・ランタイムの2段階でセキュリティを担保します。

### コンパイル時チェック（`SecurityChecker`）

| パターン | 種別 | 説明 |
|---------|------|------|
| `<iframe srcdoc={...}>` | **エラー**（コンパイル失敗） | 任意 HTML の XSS 防止 |
| `<iframe src={...}>` | 警告 | URL バリデーション推奨 |
| `<object data={...}>` / `<embed src={...}>` | 警告 | プラグイン実行リスク |
| `<form action={...}>` | 警告 | 動的フォームターゲット |
| `<button formaction={...}>` | 警告 | 動的フォームターゲット |
| `<meta http-equiv="refresh" content={...}>` | 警告 | オープンリダイレクト |
| `<a target="_blank">` without `rel="noopener"` | 警告 | タブナッビング |

### ランタイムエスケープ

- `Escape.html` — `&`, `<`, `>` をエスケープ
- `Escape.attr` — `&`, `<`, `>`, `"`, `\n`, `\r`, `\t` をエスケープ
- `Escape.url` — `javascript:`, `vbscript:`, `file:` をブロック。`data:` URL は `data:image/*`（SVG 除く）のみ許可し、それ以外はすべてブロック
- `Escape.cssValue` — `expression(`, `@import`, `javascript:` 等をブロック
- `HtmlEntities.decode` — サロゲートペア（0xD800〜0xDFFF）・Unicode 範囲外・非文字（0xFFFE, 0xFFFF）を拒否

---

## 使い方

### 1. sbt セットアップ

```scala
// project/plugins.sbt
addSbtPlugin("io.github.takapi327" % "sbt-meltc" % "0.1.0-SNAPSHOT")

// build.sbt
enablePlugins(ScalaJSPlugin, MeltcPlugin)

scalaVersion := "3.3.7"

libraryDependencies += "io.github.takapi327" %%% "melt-runtime" % "0.1.0-SNAPSHOT"

meltcPackage   := "components"  // 生成コードのパッケージ
meltcHydration := false         // SSR+Hydration を使う場合は true
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

@main def run(): Unit =
  val root = dom.document.getElementById("app")
  root.appendChild(components.App())
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

// build.sbt の meltc 設定
meltcMode := "ssr"
```

```scala
// サーバー側（http4s など）
import components.Home

val html = Home(Home.Props(userName = "Alice", count = 42))
Ok(html.body)
```

---

## 開発

```bash
# コンパイル
sbt compile

# テスト（全プラットフォーム）
sbt test

# JVM のみ
sbt meltcJVM/test runtimeJVM/test

# コードフォーマット
sbt scalafmtAll

# ヘッダーチェック
sbt headerCheckAll
```

### Scala バージョン

| Scala | 用途 |
|-------|------|
| 3.3.7 | メイン（LTS） |
| 3.8.3 | 追加テスト |
| 2.12.21 | `sbt-meltc` プラグインのみ |

### Java バージョン

Java 17 / 21 / 25（Corretto）で CI テストを実施しています。

---

## サンプル

| サンプル | 説明 |
|---------|------|
| [hello-world](examples/hello-world) | 最小構成 |
| [counter](examples/counter) | リアクティブ状態・双方向バインディング |
| [todo-app](examples/todo-app) | コンポーネント合成・リスト操作 |
| [transitions](examples/transitions) | トランジション・アニメーション |
| [special-elements](examples/special-elements) | `melt:head` / `melt:window` / `melt:body` / `melt:element` |
| [layout-effect](examples/layout-effect) | DOM 計測・レイアウトエフェクト |
| [reactive-scope](examples/reactive-scope) | リソース管理パターン |
| [dynamic-element](examples/dynamic-element) | 動的タグ名 |
| [http4s-spa](examples/http4s-spa) | SSR + Hydration + API（http4s） |

---

## 未実装機能（既知の制限）

以下は主な未実装機能の一覧です。

| ID | 機能 | 備考 |
|----|------|------|
| H-1 | スニペット / スロット | 高優先度 |
| H-2 | `<select>` / `<textarea>` の `bind:value` | 高優先度（SSR は対応済み） |
| H-3 | CSS `:global()` セレクタ | `CssScoper` 拡張が必要 |
| H-4 | `<melt:document>` 特殊要素 | 将来フェーズ |
| H-5 | `<melt:boundary>` Error Boundary | 将来フェーズ |
| H-6 | `bind:this` のコンポーネント対応 | 部分対応 |

---

## ライセンス

Apache 2.0 — 詳細は [LICENSE](LICENSE) を参照してください。
