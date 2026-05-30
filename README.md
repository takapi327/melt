# Melt

> Scala を溶かして JS にするコンパイラ

[![Continuous Integration](https://github.com/takapi327/melt/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/takapi327/melt/actions/workflows/ci.yml)
[![Apache 2.0 License](https://img.shields.io/badge/license-Apache%202.0-blue)](https://www.apache.org/licenses/LICENSE-2.0)
[![Scala Version](https://img.shields.io/badge/scala-v3.3.x%20%2F%20v3.8.x-red)](https://github.com/scala/scala3)

Melt は Scala.js 向けのシングルファイルコンポーネント（SFC）フレームワークです。Svelte にインスパイアされた `.melt` ファイルに Scala・HTML・CSS を1ファイルで記述し、コンパイラが素の DOM 操作コードへ変換します。

> [!NOTE]
> **Melt** は現在活発に開発中です。1.0 リリース前は API の破壊的変更が発生する可能性があります。

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

## コンセプト

- **コンパイラがフレームワーク** — Svelte と同様、ランタイムフレームワーク不要。コンパイラが DOM 操作コードを直接生成
- **Scala の型システムを完全保持** — テンプレート内の式も含め、型チェックはすべて scalac が行う
- **テンプレートに専用構文なし** — `{#if}` / `{#each}` のような独自構文は不要。`if`/`else`・`.map()` など素の Scala 式をそのまま使う
- **明示的なリアクティビティ** — `effect` の依存値は必ず明示する。暗黙的トラッキングによる無限ループが発生しない
- **ランタイムは最小限** — `State` / `Signal` / `Memo` を提供する小さなランタイムのみ
- **SSR 対応** — 同じ `.melt` ファイルを JVM 側で HTML 文字列として出力可能（`CompileMode.SSR`）

## モジュール

| モジュール | JVM | JS | 説明 |
|---|:---:|:---:|---|
| `melt-preprocessor` | ✅ | ✅ | 汎用プリプロセッサ API |
| `melt-sass-preprocessor` | ✅ | ❌ | Dart Sass (SCSS) サポート |
| `melt-compiler` | ✅ | ✅ | コアコンパイラ（`.melt` → `.scala`） |
| `melt-runtime` | ✅ | ✅ | リアクティブランタイム |
| `melt-codegen` | ✅ | ✅ | コードジェネレータ |
| `melt-testkit` | ❌ | ✅ | コンポーネントテストユーティリティ |
| `meltkit` | ✅ | ✅ | ルーティング DSL |
| `meltkit-adapter-browser` | ❌ | ✅ | ブラウザアダプタ |
| `meltkit-adapter-node` | ❌ | ✅ | Node.js サーバアダプタ |
| `meltkit-adapter-http4s` | ✅ | ✅ | http4s アダプタ |
| `sbt-melt` | ✅ | ❌ | sbt コンパイラプラグイン（Scala 2.12） |
| `sbt-meltkit` | ✅ | ❌ | sbt meltkit 統合プラグイン（Scala 2.12） |
| `melt-language-server` | ✅ | ❌ | LSP サーバ |

---

## `.melt` ファイル構文

`.melt` ファイルは 3 つのセクションで構成されます。

```html
<script lang="scala" props="Props">
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

### テンプレート構文

#### 式展開

```html
<p>{message}</p>
<p>{count * 2}</p>
<p>{if isActive then "active" else "inactive"}</p>
```

#### コントロールフロー

Melt はテンプレート内で通常の Scala 式を使います。`{#if}` / `{#each}` のような専用構文は不要です。

```html
<!-- 条件分岐 -->
{if count > 0 then
  <p class="positive">Positive: {count}</p>
else
  <p class="zero">Zero or negative</p>
}

<!-- リスト描画 -->
<ul>
  {items.map { item =>
    <li>{item.name}</li>
  }}
</ul>

<!-- キー付きリスト（FLIP アニメーション・差分更新に対応） -->
<ul>
  {items.keyed(_.id).map { item =>
    <li>{item.name}</li>
  }}
</ul>

<!-- 強制再マウント（key が変わるたびにブロックを破棄・再生成） -->
<melt:key this={id}>
  <Component />
</melt:key>

<!-- ネストしたコンテナなしの条件分岐 -->
{if isLoggedIn then
  <UserPanel />
else
  <LoginForm />
}
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

#### Props と内部状態の分離

`props="Props"` に宣言するのは**親から受け取る外部入力**のみです。このコンポーネント固有の状態はスクリプト本文で `val count = State(0)` と宣言します。

`State[T]` を Props に含めると、親が保持する State の参照を受け取る**双方向バインディング**になります。子が `.update()` を呼ぶと変更が親に即時伝播します。

```html
<!-- StepControl.melt — 親の State[Int] を Props で受け取る -->
<script lang="scala" props="Props">
case class Props(count: State[Int], step: Int = 1)
</script>

<div>
  <button onclick={_ => props.count.update(_ - props.step)}>−{props.step}</button>
  <span>{props.count}</span>
  <button onclick={_ => props.count.update(_ + props.step)}>+{props.step}</button>
</div>
```

```html
<!-- 親コンポーネント — 自身の State をそのまま渡す -->
<script lang="scala">
val count = State(0)
</script>

<div>
  <p>Count: {count}</p>
  <StepControl count={count} step={5} />
</div>
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
case class Props(items: State[List[Todo]], renderItem: Snippet[Todo])
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
  <melt:pending>
    <p>Loading…</p>
  </melt:pending>
  <melt:failed (error, reset)>
    <p>Error: {error.getMessage()}</p>
  </melt:failed>
</melt:boundary>
```

#### ジェネリックコンポーネント

```html
<script lang="scala" props="Props[T]">
case class Props[T](items: State[List[T]], render: Snippet[T])
</script>
```

---

## Runtime API

### リアクティブプリミティブ

#### `State[A]` — ミュータブルなリアクティブ変数

```scala
val count = State(0)

// 読み取り
val current: Int = count.value

// 更新
count.set(5)
count.update(_ + 1)

// 算術拡張メソッド
count += 1   // Int / Long / Double / String
count -= 1
count *= 2

// コレクション拡張（State[List[A]]）
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

### 派生値

#### `map` — 常に再計算される派生値

```scala
val count   = State(0)
val doubled = count.map(_ * 2)   // count が変化するたびに再計算
val label   = count.map(n => if n > 0 then "positive" else "zero or negative")
```

#### `memo` — 結果が変化したときのみ伝播する派生値

`map` と異なり、`memo` は計算結果が前回と等しい場合は下流への更新を抑制します。
再描画コストの高い値（真偽値・分類値など）の不要な更新を防ぎたいときに使います。

```scala
val isEven = count.memo(_ % 2 == 0)
// count が 0→2 と変化しても isEven は true のまま → 再描画をスキップ
```

### エフェクト

```scala
// エフェクト（依存値変化時に実行）
effect(count) { n =>
  dom.document.title = s"Count: $n"
}

// 複数の依存値
effect(a, b) { (va, vb) =>
  println(s"a=$va, b=$vb")
}

// レイアウトエフェクト（DOM 更新前に実行）
layoutEffect(count) { n =>
  val height = el.getBoundingClientRect().height
  containerHeight.set(height.toInt)
}
```

> [!NOTE]
> Melt のエフェクトは**依存値を明示的に指定**します。
> Svelte 5 の `$effect` のような暗黙的なトラッキングがないため、
> 意図しない依存による無限ループが発生しません。

### ライフサイクル

```scala
// DOM 挿入後に一度だけ実行
onMount {
  fetchData()
}

// クリーンアップが必要な場合は ctx.onCleanup で登録
onMount { ctx =>
  val observer = new dom.IntersectionObserver(...)
  observer.observe(myEl)
  ctx.onCleanup(() => observer.disconnect())
}

// コンポーネント破棄時に実行（subscribe の解除など）
onCleanup(() => subscription.cancel())

// 次の DOM 更新を待つ
tick { ... }              // コールバック式
tickAsync().foreach { _ => ... }  // Future 式
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

// 信頼済み HTML — 静的・開発者管理コンテンツ
val safe = TrustedHtml.unsafe("<strong>validated</strong>")

// 信頼済み HTML — ユーザー入力をサニタイザ経由で生成（JVM: jsoup, JS: DOMPurify 等）
val sanitized = TrustedHtml.sanitize(userInput, mySanitizer)
```

---

## 使い方

### 1. sbt セットアップ

```scala
// project/plugins.sbt
addSbtPlugin("io.github.takapi327" % "sbt-melt" % "0.1.0-SNAPSHOT")

// build.sbt
enablePlugins(ScalaJSPlugin, MeltcPlugin)

scalaVersion := "3.3.7"

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

meltMode := "ssr"
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
sbt meltJVM/test runtimeJVM/test

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
| 2.12.21 | `sbt-melt` プラグインのみ |

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
sbt scripted
```

---

## ライセンス

Apache 2.0 — 詳細は [LICENSE](LICENSE) を参照してください。
