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
  case class Props(title: String = "Hello", count: Var[Int])
  val doubled = count.map(_ * 2)
</script>

<div>
  <h1>{title}</h1>
  <p>Count: {count} / Doubled: {doubled}</p>
  <button onclick={_ => count += 1}>+1</button>
</div>

<style>
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

#### 生 HTML の挿入（XSS 注意）

`TrustedHtml.unsafe` でマークした文字列のみ raw HTML として挿入できます。コメントアンカー方式で挿入されるため、ラッパー要素は生成されません。

```html
<!-- 静的 -->
<div>{TrustedHtml.unsafe("<strong>Bold</strong>")}</div>

<!-- リアクティブ（if/else で切り替え） -->
<div>{if show then TrustedHtml.unsafe("<b>yes</b>") else TrustedHtml.unsafe("<em>no</em>")}</div>
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

<!-- 要素寸法 -->
<div bind:clientWidth={w} bind:clientHeight={h}></div>

<!-- メディア -->
<video bind:currentTime={t} bind:paused={paused} bind:volume={vol} src="..."></video>
```

#### クラス・スタイルディレクティブ

```html
<div class:active={isActive} class:disabled={!isEnabled}>...</div>
<p style:color={textColor} style:font-size="{fontSize}px">...</p>
<div {...attrs}></div>
```

#### スニペット（コンテンツ投影）

親コンポーネントでスニペットを定義し、子コンポーネントへ渡せます。

```html
<!-- 親コンポーネント -->
<List items={todos}>
  {#snippet renderItem(item: Todo)}
    <input type="checkbox" bind:checked={item.done} />
    <span>{item.text}</span>
  {/snippet}
</List>

<!-- 子コンポーネント（List.melt） -->
<script lang="scala" props="Props">
case class Props(items: Var[List[Todo]], renderItem: Snippet[Todo])
</script>

<ul>
  {props.items.map((item: Todo) =>
    <li>{@render props.renderItem(item)}</li>
  )}
</ul>
```

#### トランジション・アニメーション

```html
<div transition:fade>...</div>
<div transition:fly="{{x: 200, duration: 300}}">...</div>
<div in:slide out:fade>...</div>
<div animate:flip="{{duration: 200}}">...</div>
```

利用可能なトランジション: `fade`, `slide`, `fly`, `scale`, `blur`, `draw`, `crossfade`

イージング関数（31種）: `linear`, `cubicIn/Out/InOut`, `quadIn/Out/InOut`, `quartIn/Out/InOut`, `quintIn/Out/InOut`, `sineIn/Out/InOut`, `backIn/Out/InOut`, `elasticIn/Out/InOut`, `bounceIn/Out/InOut`, `circIn/Out/InOut`, `expoIn/Out/InOut`

#### アクション

```html
<button use:tooltip="{{text: 'Click me'}}">...</button>
<input use:autoFocus />
<div use:clickOutside={handler}>...</div>
```

#### 特殊要素

```html
<!-- <head> コンテンツ -->
<melt:head>
  <title>{pageTitle}</title>
  <meta name="description" content={description} />
</melt:head>

<!-- ウィンドウイベント・バインディング -->
<melt:window
  on:keydown={handleKeydown}
  bind:innerWidth={width}
  bind:scrollY={scrollY}
/>

<!-- body クラス・スタイル -->
<melt:body class:dark-mode={isDarkMode} />

<!-- document タイトル・lang -->
<melt:document bind:visibilityState={visibility} />

<!-- 動的タグ -->
<melt:element this={tagName} class="wrapper">...</melt:element>

<!-- 強制再マウント -->
<melt:key this={id}>
  <Component />
</melt:key>

<!-- Error Boundary -->
<melt:boundary>
  <AsyncComponent />
  {#pending}
    <p>Loading…</p>
  {/pending}
  {#failed error}
    <p>Error: {error.getMessage()}</p>
  {/failed}
</melt:boundary>
```

#### ジェネリックコンポーネント

```html
<script lang="scala" props="Props[T]">
case class Props[T](items: Var[List[T]], render: Snippet[T])
</script>
```

---

## Runtime API

### リアクティブプリミティブ

#### `Var[A]` — ミュータブルなリアクティブ変数

```scala
val count = Var(0)

// 読み取り
val current: Int = count.value  // または count.now()

// 更新
count.set(5)
count.update(_ + 1)

// 算術拡張メソッド
count += 1   // Int / Long / Double / String
count -= 1
count *= 2

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

// 購読
val unsubscribe = count.subscribe(n => println(s"Changed to: $n"))
unsubscribe()
```

#### `Signal[A]` — 読み取り専用のリアクティブ値

```scala
val doubled: Signal[Int] = count.map(_ * 2)
val frozen: Signal[Int]  = Signal.pure(42)

doubled.value
doubled.subscribe(n => println(n))
```

### エフェクト・メモ化

```scala
// エフェクト（依存値変化時に実行）
effect(count) { n =>
  dom.document.title = s"Count: $n"
}

// 2変数
effect(a, b) { (va, vb) => ... }

// レイアウトエフェクト（DOM 更新前に実行）
layoutEffect(count) { n =>
  el.getBoundingClientRect().height
}

// メモ化（依存値が変化したときのみ再計算）
val isEven = memo(count)(_ % 2 == 0)
```

### ライフサイクル

```scala
onMount { () =>
  fetchData()
  () => cleanup()  // アンマウント時のクリーンアップを返せる
}

onCleanup { () =>
  subscription.cancel()
}

// 次の DOM 更新を待つ
tick().foreach { _ => ... }
```

### コンテキスト API

```scala
val ThemeCtx = Context.create("light")

// 提供側
ThemeCtx.provide("dark")

// 消費側
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

### Tween / Spring

```scala
val pos   = Tween(0.0, TweenOptions(duration = 400, easing = Easing.cubicOut))
val scale = Spring(1.0, SpringOptions(stiffness = 0.1, damping = 0.25))

pos.set(100.0)    // アニメーション付きで更新
scale.set(1.5)
```

### セキュリティユーティリティ

```scala
// HTML エスケープ
Escape.html("<script>alert(1)</script>")  // "&lt;script&gt;..."

// 属性エスケープ
Escape.attr(userInput)

// URL 検証（javascript:, vbscript:, file: をブロック）
Escape.url(hrefValue)

// CSS 値エスケープ
Escape.cssValue(styleValue)

// 信頼済み HTML（エスケープをバイパス）
val safe = TrustedHtml.unsafe("<strong>validated</strong>")
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
- `Escape.url` — `javascript:`, `vbscript:`, `file:` をブロック。`data:` URL は `data:image/*`（SVG 除く）のみ許可
- `Escape.cssValue` — `expression(`, `@import`, `javascript:` 等をブロック

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

meltcMode := "ssr"
```

```scala
// サーバー側（http4s など）
import components.Home

val html = Home(Home.Props(userName = "Alice", count = 42))
Ok(html.body)
```

---

## サンプル

| サンプル | 説明 |
|---------|------|
| [hello-world](examples/hello-world) | 最小構成 |
| [counter](examples/counter) | リアクティブ状態・双方向バインディング・コンテキスト |
| [todo-app](examples/todo-app) | コンポーネント合成・スニペット・リスト操作 |
| [transitions](examples/transitions) | トランジション・アニメーション・FLIP |
| [special-elements](examples/special-elements) | `melt:head` / `melt:window` / `melt:body` / `melt:element` / `melt:key` |
| [boundary](examples/boundary) | Error Boundary・非同期レンダリング |
| [layout-effect](examples/layout-effect) | DOM 計測・レイアウトエフェクト |
| [reactive-scope](examples/reactive-scope) | リソース管理パターン |
| [dynamic-element](examples/dynamic-element) | 動的タグ名 |
| [media-binding](examples/media-binding) | メディア要素バインディング |
| [dimension-binding](examples/dimension-binding) | 要素寸法バインディング |
| [select-textarea-bind](examples/select-textarea-bind) | セレクト・テキストエリアバインディング |
| [trusted-html](examples/trusted-html) | 生 HTML 挿入（`TrustedHtml`） |
| [http4s-spa](examples/http4s-spa) | SPA + API（http4s） |
| [http4s-ssr](examples/http4s-ssr) | SSR + Hydration + API（http4s） |

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

## ライセンス

Apache 2.0 — 詳細は [LICENSE](LICENSE) を参照してください。
