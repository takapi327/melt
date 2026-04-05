# Meltc Design Document

> Scalaを溶かしてJSにするコンパイラ
>
> Status: Draft — 設計フェーズ
> Last Updated: 2026-04-01

---

## 1. 概要

### 1.1 Meltcとは

MeltcはScala.js向けのシングルファイルコンポーネントコンパイラである。Svelteのように`.melt`ファイルにScala・HTML・CSSを1ファイルで記述し、コンパイラがフレームワークなしの素のDOM操作コードに変換する。

### 1.2 コンセプト

- **コンパイラがフレームワークである** — Svelteと同様、ランタイムフレームワーク（Laminar等）を必要としない。コンパイラが直接 `document.createElement` 等のDOM操作コードを生成する。
- **Scalaの型システムを完全に保持する** — meltcはテキスト変換のみ行い、型チェックはscalacに委ねる。テンプレート部分も含めて型安全。
- **ランタイムは最小限** — `melt-runtime`（約100行）が提供する `Var` / `Signal` / `Bind` のみ。
- **scala-js-domはゼロコスト** — 型定義のみでランタイムオーバーヘッドなし。TypeScriptの `lib.dom.d.ts` と同等。

### 1.3 命名

| 項目 | 名前 |
|---|---|
| コンパイラ | `meltc` |
| ファイル拡張子 | `.melt` |
| npmパッケージ（コンパイラ） | `meltc` |
| npmパッケージ（Viteプラグイン） | `vite-plugin-melt` |
| npmパッケージ（ランタイム） | `melt-runtime` |

由来: melt（溶かす）+ c（compiler）。`scalac`、`rustc`、`tsc` と同じ命名規則。Scalaのコンパイラがコードを「溶かして」軽量なJSに精錬するイメージ。

---

## 2. コンパイルパイプライン

```
Counter.melt                    ← 開発者が書くファイル
    │
    ├─ meltc                    ← テキスト変換のみ。型ロジックなし
    │
    ├─ Counter.scala            ← 生成コード（素のDOM操作）
    │
    ├─ scalac + Scala.js        ← 型チェック + JS出力
    │
    ├─ main.js                  ← ブラウザが実行するJS
    │
    └─ melt-runtime (~100行)    ← Var / Signal / Bind
```

### 2.1 meltcの責務

meltcは `.melt` ファイルを `.scala` ファイルに変換するテキストプロセッサである。

**やること:**

- ファイルの各セクション（script・テンプレート・style）を分割（`<script lang="scala">` のみを Scala セクションとして認識。`lang="scala"` のない `<script>` は HTML として扱う）
- HTMLテンプレートを `dom.document.createElement(...)` 等のScala.jsコードに変換
- `{expression}` をリアクティブバインディング（`Bind.text(...)` 等）に変換
- `onevent` をイベントリスナー登録に変換
- `bind:value` / `bind:checked` / `bind:group` を双方向バインディングに変換
- `bind:this` を `Ref.set()` 呼び出しに変換
- `class:name={expr}` を `Bind.classToggle()` に変換
- `style:property={expr}` を `Bind.style()` に変換
- `use:action={params}` をアクション関数呼び出し + cleanup 登録に変換
- `transition:` / `in:` / `out:` をトランジション実行コードに変換
- `animate:` をリスト並べ替えアニメーションコードに変換
- `{html(expr)}` を `Bind.html()` に変換（XSS 警告を出す）
- CSSにスコープIDを付与
- `styled` 属性のあるコンポーネントに親のスコープIDを付与
- `def f(): Html = { <tag>...</tag> }` のHtml関数を `def f(): dom.Element = { ... }` に変換
- テンプレート内の `Var` 参照に `.signal` を自動補完
- アクセシビリティ警告（`alt` 漏れ、`label` 漏れ等）を出力

**やらないこと:**

- 型チェック（scalacが行う）
- 型推論（scalacが行う）
- 最適化（Scala.jsリンカーが行う）
- バンドル（Viteが行う）

### 2.2 Svelteとの比較

| | Svelte | Meltc |
|---|---|---|
| 入力 | `.svelte` | `.melt` |
| コンパイラが生成 | `document.createElement(...)` のJS | `document.createElement(...)` のJS（Scala.js経由） |
| ランタイム | ~2KB のヘルパー | ~100行の `melt-runtime` |
| 型チェック | TypeScript（テンプレート内は不完全） | scalac（テンプレート含め完全） |
| フレームワーク依存 | なし | なし |

---

## 3. `.melt` ファイル構造

```
┌─────────────────────────────────────┐
│ <script lang="scala" [props="X"]>   │
│   import                            │
│   型定義 (case class, type)          │
│   state (Var, Signal)               │
│   ロジック (def increment)           │
│   パーツ (def row(): Html)           │  ← Html関数（HTML混在を許可）
│ </script>                           │
├─────────────────────────────────────┤
│ トップレベル HTML                    │  ← メインUI
├─────────────────────────────────────┤
│ <style>                             │  ← スコープ付きCSS
└─────────────────────────────────────┘
```

Scalaのコード（import、型定義、state、ロジック、パーツ）は全て `<script>` 内に書く。meltc は `<script>` 内の import と型定義を生成 `.scala` ファイルの適切な位置に配置する。

**`<script>` タグの区別:** meltc は `<script lang="scala">` のみを Scala セクションとして処理する。`lang="scala"` を持たない `<script>` タグ（通常の JavaScript や外部スクリプト参照）は HTML テンプレートの一部としてそのまま出力される。

```html
<!-- meltc が処理する -->
<script lang="scala">
  val count = Var(0)
</script>

<script lang="scala" props="Props">
  case class Props(label: String)
</script>

<!-- meltc が無視する（HTML としてそのまま出力）-->
<script>console.log("hello")</script>
<script src="https://example.com/analytics.js"></script>
<script type="module">import { x } from './lib.js'</script>
```

### 3.1 完全な例

```html
<!-- Counter.melt -->

<script lang="scala" props="Props">
  import shared.Theme

  case class Props(label: String, count: Int = 0)

  val internal = Var(props.count)
  val doubled = internal.map(_ * 2)

  def increment(): Unit = internal += 1
  def reset(): Unit = internal.set(0)

  def badge(text: String, color: String = "blue"): Html = {
    <span class={s"badge badge--$color"}>{text}</span>
  }
</script>

<div class="counter">
  <h1>{props.label}</h1>
  {badge(internal.now().toString, "red")}
  <button onclick={increment}>+1</button>
  <button onclick={reset}>Reset</button>
  <p>Doubled: {doubled}</p>
</div>

<style>
.counter { text-align: center; padding: 2rem; }
h1 { color: #ff3e00; }
</style>
```

---

## 4. コンポーネントモデル

### 4.1 Props

**確定事項:**

- `case class` または Named Tuple で `<script>` 内に定義する
- `<script>` タグの `props` 属性で型名を指定する
- script内・テンプレート内で `props.xxx` としてアクセスする
- Propsが不要なコンポーネントは `props` 属性を省略する
- 型は `<script>` 内に直接書くか、外部 `.scala` ファイルからimportして使う

#### case class による定義

```html
<script lang="scala" props="Props">
  case class Props(label: String, count: Int = 0)

  val doubled = props.count * 2
</script>

<div>
  <h1>{props.label}</h1>
  <p>{doubled}</p>
</div>

<style></style>
```

#### Named Tuple による定義

```html
<script lang="scala" props="Props">
  type Props = (title: String, color: String = "blue")

  val cls = s"card card--${props.color}"
</script>

<div class={cls}>
  <h2>{props.title}</h2>
</div>

<style></style>
```

#### Propsなしコンポーネント

```html
<script lang="scala">
  val count = Var(0)
  def increment(): Unit = count += 1
</script>

<button onclick={increment}>
  Count: {count}
</button>

<style></style>
```

#### 外部型の共有

```scala
// shared/Models.scala（普通のScalaファイル）
package shared

case class User(firstName: String, lastName: String, email: String)

enum Theme(val cssClass: String):
  case Light extends Theme("theme-light")
  case Dark  extends Theme("theme-dark")
```

```html
<!-- UserCard.melt -->
<script lang="scala" props="Props">
  import shared.{User, Theme}

  type Props = (user: User, theme: Theme = Theme.Light)

  val displayName = s"${props.user.firstName} ${props.user.lastName}"
</script>

<div class={props.theme.cssClass}>
  <p>{displayName}</p>
</div>

<style></style>
```

#### meltcの生成コード

```scala
// meltcが生成する Counter.scala
package generated

import shared.Theme

object Counter:
  case class Props(label: String, count: Int = 0)

  def create(props: Props): dom.Element =
    val internal = Var(props.count)
    val doubled = internal.map(_ * 2)
    def increment(): Unit = internal += 1
    ...
```

#### HtmlProps / restProps

コンポーネントが HTML 属性を受け取り、内部の HTML 要素に透過させる仕組み。Props に `extends HtmlProps`（またはその派生 trait）を追加するだけで有効になる。

##### HtmlProps 階層

melt-runtime が要素別の HtmlProps trait を提供する。各 trait は対応する HTML 要素で有効な属性のみを受け付ける。

```scala
// melt-runtime が提供

// ── ベース: 全 HTML 要素共通 ──
trait HtmlProps:
  val html: HtmlAttrs = HtmlAttrs.empty
  def withHtml(attrs: HtmlAttrs): this.type
// id, class, style, title, hidden, tabIndex, aria-*, data-*, role, ...

// ── 要素別の専用 trait ──
trait ButtonHtmlProps extends HtmlProps
// disabled, type (submit/button/reset), form, formAction, name, value, ...

trait InputHtmlProps extends HtmlProps
// type, placeholder, required, min, max, pattern, step, minLength, maxLength, ...

trait AnchorHtmlProps extends HtmlProps
// href, target, rel, download, hreflang, ...

trait FormHtmlProps extends HtmlProps
// action, method, enctype, novalidate, autocomplete, ...

trait ImgHtmlProps extends HtmlProps
// src, alt, width, height, loading, decoding, crossorigin, ...

trait SelectHtmlProps extends HtmlProps
// disabled, required, multiple, size, name, form, ...

trait TextAreaHtmlProps extends HtmlProps
// placeholder, required, rows, cols, minLength, maxLength, wrap, ...
```

| trait | 対象要素 | 固有の属性例 |
|---|---|---|
| `HtmlProps` | 全要素 | id, class, style, title, hidden, aria-*, data-* |
| `ButtonHtmlProps` | `<button>` | disabled, type, form, formAction, name, value |
| `InputHtmlProps` | `<input>` | type, placeholder, required, min, max, pattern |
| `AnchorHtmlProps` | `<a>` | href, target, rel, download |
| `FormHtmlProps` | `<form>` | action, method, enctype, novalidate |
| `ImgHtmlProps` | `<img>` | src, alt, width, height, loading |
| `SelectHtmlProps` | `<select>` | disabled, required, multiple, size |
| `TextAreaHtmlProps` | `<textarea>` | placeholder, required, rows, cols, wrap |

##### コンポーネント定義側

```html
<!-- Button.melt -->
<script lang="scala" props="Props">
  case class Props(
    label: String,
    variant: String = "primary"
  ) extends ButtonHtmlProps   // ← Button 固有の HTML 属性を受け取れる
</script>

<button class={s"btn btn-${props.variant}"} {...props.html}>
  {props.label}
</button>
```

##### 使用側

カスタム props と HTML 属性をフラットに書ける。Props に定義されていない属性は meltc が自動的に `props.html` に集約する。

```html
<Button
  label="Submit"
  variant="danger"
  id="submit"           <!-- HtmlProps: OK -->
  disabled              <!-- ButtonHtmlProps: OK -->
  type="submit"         <!-- ButtonHtmlProps: OK -->
  aria-label="Submit"   <!-- HtmlProps: OK -->
  href="/somewhere"     <!-- ERROR: ButtonHtmlProps に href はありません -->
/>
```

##### meltc が行うこと

1. `Props extends ButtonHtmlProps` を検出
2. 使用側のテンプレートで Props に定義されていない属性（`id`, `disabled`, `type`, `aria-label`）を検出
3. その属性が `ButtonHtmlProps` で許可されているか確認
4. 許可されている → `HtmlAttrs` に自動的にまとめる
5. 許可されていない → コンパイルエラー
6. テンプレート内の `{...props.html}` を全属性の一括適用に変換

```scala
// meltc が生成するコード（使用側）
Button.create(
  Props(label = "Submit", variant = "danger")
    .withHtml(HtmlAttrs("id" -> "submit", "disabled" -> "", "type" -> "submit", "aria-label" -> "Submit"))
)

// meltc が生成するコード（コンポーネント内 {...props.html}）
props.html.apply(_button0)   // 全 HTML 属性を一括適用
```

`extends HtmlProps` を書かない場合、Props に未定義の属性はコンパイルエラーになる。

##### 完全な例: Input コンポーネント

```html
<!-- Input.melt -->
<script lang="scala" props="Props">
  case class Props(
    label: String,
    error: Option[String] = None
  ) extends InputHtmlProps
</script>

<div class="field">
  <label>{props.label}</label>
  <input {...props.html} class={if props.error.isDefined then "error" else ""} />
  {props.error match
    case Some(msg) => <span class="error-msg">{msg}</span>
    case None      => <span></span>}
</div>

<style>
.error { border-color: red; }
.error-msg { color: red; font-size: 0.8rem; }
</style>
```

```html
<!-- 使用側: HTML属性がそのまま input に渡る -->
<Input
  label="Email"
  error={emailError}
  type="email"
  placeholder="you@example.com"
  required
  aria-describedby="email-help"
/>
```

#### Option / Boolean の自動処理

HTML 属性に `Option` や `Boolean` が渡された場合、meltc が自動で適切な処理コードを生成する。

```html
<input
  id={userId}             <!-- Option[String]: Some → 設定, None → 属性削除 -->
  disabled={isDisabled}   <!-- Boolean: true → 設定, false → 属性削除 -->
  class="field"           <!-- String: そのまま設定 -->
/>
```

| 型 | 値 | 結果 |
|---|---|---|
| `String` | `"hello"` | `setAttribute("x", "hello")` |
| `Option[String]` | `Some("hello")` | `setAttribute("x", "hello")` |
| `Option[String]` | `None` | `removeAttribute("x")` |
| `Boolean` | `true` | `setAttribute("x", "")` |
| `Boolean` | `false` | `removeAttribute("x")` |
| `Int` / `Double` | `42` | `setAttribute("x", "42")` |
| `Option[Int]` | `Some(42)` | `setAttribute("x", "42")` |
| `Option[Int]` | `None` | `removeAttribute("x")` |

### 4.2 パーツ（Html関数）

**確定事項:**

- パーツは `<script>` 内で `def name(args): Html = { <tag>...</tag> }` として定義する
- meltcが `Html` 返り値を `dom.Element` に変換する
- テンプレート内から `{name(args)}` で呼び出す
- パーツ内から他のパーツを呼び出せる（通常のScala関数呼び出し）
- パーツをpropsとして子コンポーネントに渡せる

#### 基本

```html
<script lang="scala">
  def greeting(name: String): Html = {
    <p>Hello, {name}!</p>
  }
</script>

<div>
  {greeting("World")}
  {greeting("Scala")}
</div>

<style></style>
```

#### パーツ間の呼び出し

```html
<script lang="scala">
  case class Item(name: String, price: Int)

  def badge(text: String, color: String = "blue"): Html = {
    <span class={s"badge badge--$color"}>{text}</span>
  }

  def row(item: Item): Html = {
    <tr>
      <td>{item.name}</td>
      <td>{badge(item.price.toString, "green")}</td>
    </tr>
  }
</script>

<table>
  {items.keyed(_.name).map(row)}
</table>

<style></style>
```

#### Propsとして渡す

```html
<!-- List.melt -->
<script lang="scala" props="Props">
  type Props[T] = (items: Signal[Seq[T]], renderItem: T => Element)
</script>

<ul>
  {props.items.map(props.renderItem)}
</ul>

<style></style>
```

```html
<!-- 使う側 -->
<script lang="scala">
  import components.List

  val fruits = Var(List("Apple", "Banana", "Cherry"))

  def fruitRow(name: String): Html = {
    <li class="fruit">{name}</li>
  }
</script>

<List items={fruits} renderItem={fruitRow} />

<style></style>
```

### 4.3 children / スニペット（slot 代替）

Svelte 5 の `{#snippet}` や React の children / render props に相当する機能。Melt では専用構文を導入せず、Props の関数型フィールドとして実現する。パーツ関数（`def f(): Html`）をそのまま渡せる。

#### children（単純な子要素の受け渡し）

```html
<!-- Card.melt -->
<script lang="scala" props="Props">
  case class Props(
    title: String,
    children: () => Element
  )
</script>

<div class="card">
  <h2>{props.title}</h2>
  <div class="card-body">
    {props.children()}
  </div>
</div>

<style></style>
```

```html
<!-- 使う側: タグの中身が自動的に children として渡される -->
<Card title="Settings">
  <p>Card content</p>
  <button onclick={save}>Save</button>
</Card>
```

#### 名前付きスロット（複数のテンプレート断片を渡す）

```html
<!-- Layout.melt -->
<script lang="scala" props="Props">
  case class Props(
    header: () => Element,
    footer: () => Element,
    children: () => Element
  )
</script>

<div class="layout">
  <header>{props.header()}</header>
  <main>{props.children()}</main>
  <footer>{props.footer()}</footer>
</div>

<style></style>
```

```html
<!-- 使用側 -->
<script lang="scala">
  def myHeader(): Html = {
    <h1>Site Title</h1>
  }

  def myFooter(): Html = {
    <p>© 2026</p>
  }
</script>

<Layout header={myHeader} footer={myFooter}>
  <p>Main content</p>
</Layout>
```

#### 引数付きスニペット（Svelte 5 の `{#snippet row(item)}` に相当）

テンプレートの断片に引数を渡したい場合、Props の型を `A => Element` にする。

```html
<!-- Table.melt -->
<script lang="scala" props="Props">
  case class Props[A](
    data: Var[List[A]],
    row: A => Element
  )
</script>

<table>
  <tbody>
    {props.data.map(_.map(props.row))}
  </tbody>
</table>

<style></style>
```

```html
<!-- 使用側: パーツ関数をそのまま渡す -->
<script lang="scala">
  case class Item(name: String, price: Double)
  val items = Var(List(Item("Apple", 1.0), Item("Banana", 2.0)))

  def itemRow(item: Item): Html = {
    <tr>
      <td>{item.name}</td>
      <td>{item.price.toString}</td>
    </tr>
  }
</script>

<Table data={items} row={itemRow} />
```

#### 複数の引数付きスニペット

```html
<!-- DataGrid.melt -->
<script lang="scala" props="Props">
  case class Props[A](
    data: Var[List[A]],
    header: () => Element,
    row: A => Element,
    empty: () => Element = () => { <p>No data</p> }
  )
</script>

<div class="grid">
  <div class="grid-header">{props.header()}</div>
  {if props.data.map(_.isEmpty) then
    props.empty()
  else
    <div class="grid-body">
      {props.data.map(_.map(props.row))}
    </div>}
</div>

<style></style>
```

```html
<!-- 使用側 -->
<script lang="scala">
  def gridHeader(): Html = {
    <div class="row">
      <span>Name</span>
      <span>Price</span>
    </div>
  }

  def gridRow(item: Item): Html = {
    <div class="row">
      <span>{item.name}</span>
      <span>{item.price.toString}</span>
    </div>
  }

  def noItems(): Html = {
    <div class="empty">
      <p>No items found</p>
      <button onclick={_ => loadItems()}>Reload</button>
    </div>
  }
</script>

<DataGrid
  data={items}
  header={gridHeader}
  row={gridRow}
  empty={noItems}
/>
```

#### Svelte 5 / React との比較

| | Svelte 5 | React | Melt |
|---|---|---|---|
| 子要素 | `<slot />` | `{children}` | `{props.children()}` |
| 名前付きスロット | `{#snippet name()}` + `{@render}` | render props | `() => Element` の Props |
| 引数付き | `{#snippet row(item)}` | `(item) => <tr>...</tr>` | `A => Element` の Props |
| 専用構文 | `{#snippet}`, `{@render}` | なし | なし（パーツ関数がそのまま使える） |
| 型安全性 | 部分的 | TypeScript 依存 | 完全（scalac） |

---

## 5. リアクティビティ

### 5.1 テンプレート内の自動補完

**確定事項:** テンプレート内で `Var` や `Signal` を参照した場合、meltcが自動的にリアクティブバインディングに変換する。`Var` と `Signal` のどちらも同じ構文で使える。

```html
<script lang="scala">
  val count = Var(0)                   // Var[Int]
  val doubled = count.map(_ * 2)       // Signal[Int]
</script>

<!-- どちらも同じように書ける -->
<span>{count}</span>      <!-- Bind.text(count, node) -->
<span>{doubled}</span>    <!-- Bind.text(doubled, node) -->
```

### 5.2 Var — 書き換え可能なリアクティブ変数

`Var[A]` は値を保持し、変更を全ての購読者に通知する。

#### 基本API

```scala
val count = Var(0)

count.set(5)            // 値を直接セット
count.update(_ + 1)     // 現在値を基に更新
count.now()             // 現在値を取得（非リアクティブ）
count.signal            // Signal[A] を明示的に取得（通常は不要）
```

#### 演算子

型安全な演算子を extension で提供する。関係のない型には演算子が漏れない。

```scala
val count = Var(0)
val name = Var("hello")
val flag = Var(true)

// 数値
count += 1              // count.update(_ + 1)
count -= 5              // count.update(_ - 5)
count *= 2              // count.update(_ * 2)

// 文字列
name += " world"        // name.update(_ + " world")

// 真偽値
flag.toggle()           // flag.update(!_)
```

melt-runtime 実装:

```scala
extension (v: Var[Int])
  def +=(n: Int): Unit = v.update(_ + n)
  def -=(n: Int): Unit = v.update(_ - n)
  def *=(n: Int): Unit = v.update(_ * n)

extension (v: Var[Long])
  def +=(n: Long): Unit = v.update(_ + n)
  def -=(n: Long): Unit = v.update(_ - n)

extension (v: Var[Double])
  def +=(n: Double): Unit = v.update(_ + n)
  def -=(n: Double): Unit = v.update(_ - n)

extension (v: Var[String])
  def +=(s: String): Unit = v.update(_ + s)

extension (v: Var[Boolean])
  def toggle(): Unit = v.update(!_)
```

#### コレクションヘルパー

`Var[List[A]]` に対してよく使う操作を extension で提供する。

```scala
val todos = Var(List(
  Todo(1, "Buy milk", false),
  Todo(2, "Walk dog", true),
))

// 追加
todos.append(Todo(3, "Cook", false))
todos.prepend(Todo(0, "Wake up", true))

// 削除
todos.removeWhere(_.id == 2)
todos.removeAt(0)

// 変換
todos.mapItems(_.copy(done = true))
todos.updateWhere(_.id == 1)(_.copy(done = true))

// その他
todos.clear()
todos.sortBy(_.text)

// 複雑な操作は update で
todos.update(_.filter(_.done).sortBy(_.id))
```

melt-runtime 実装:

```scala
extension [A](v: Var[List[A]])
  def append(item: A): Unit = v.update(_ :+ item)
  def prepend(item: A): Unit = v.update(item :: _)
  def removeWhere(pred: A => Boolean): Unit = v.update(_.filterNot(pred))
  def removeAt(index: Int): Unit = v.update(_.patch(index, Nil, 1))
  def mapItems(f: A => A): Unit = v.update(_.map(f))
  def updateWhere(pred: A => Boolean)(f: A => A): Unit =
    v.update(_.map(item => if pred(item) then f(item) else item))
  def clear(): Unit = v.set(List.empty)
  def sortBy[B: Ordering](f: A => B): Unit = v.update(_.sortBy(f))
```

### 5.3 Signal — 読み取り専用の派生値

`Signal[A]` は1つ以上の `Var` や `Signal` から派生する読み取り専用の値。依存元が変わると自動で再計算される。

#### 派生の書き方（使い分けルール）

| Signalの数 | 書き方 | 例 |
|---|---|---|
| 1つ | `.map(...)` | `count.map(_ * 2)` |
| 2つ以上 | `for ... yield` | `for a <- x; b <- y yield ...` |
| 動的切り替え | `for` + `flatMap` | `for f <- flag; v <- if f then a else b yield v` |

```scala
val first = Var("Taro")
val last = Var("Yamada")
val age = Var(25)
val todos = Var(List.empty[Todo])

// ── 1つ → map ──
val upper = first.map(_.toUpperCase)
val remaining = todos.map(_.count(!_.done))
val allDone = remaining.map(_ == 0)

// ── 2つ以上 → for式 ──
val fullName = for
  f <- first
  l <- last
yield s"$f $l"

val summary = for
  ts <- todos
  r  <- remaining
yield s"${ts.length} items, $r remaining"

// ── 動的切り替え → flatMap ──
val useNickname = Var(false)
val nickname = Var("Ta-kun")

val displayName = for
  useNick <- useNickname
  name <- if useNick then nickname else first
yield name
```

#### Var から直接 map / flatMap が使える

`Var[A]` に `map` / `flatMap` を直接定義しているため、`.signal` を経由する必要がない。

```scala
class Var[A]:
  def signal: Signal[A] = ...         // 明示取得も可能
  def map[B](f: A => B): Signal[B] = signal.map(f)
  def flatMap[B](f: A => Signal[B]): Signal[B] = signal.flatMap(f)
```

> **⚠️ 注意 — サブスクライバーの解除について**
>
> `map` / `flatMap` で生成した `Signal` は内部でサブスクライバーを登録するが、
> 外部からそのサブスクリプションを解除する手段を現在持っていない。
> Phase 4 (DOM バインディング) でコンポーネントが破棄される際に、
> 派生 Signal が保持するサブスクライバーが残り続けるメモリリークが発生しうる。
> Phase 4 実装時は `onCleanup` / スコープ付きエフェクト等の仕組みと組み合わせて
> サブスクリプションの回収手段を設計すること。

これにより `for` 式で `Var` と `Signal` を区別なく混ぜて書ける:

```scala
val a = Var(1)                  // Var[Int]
val b = Var(2)                  // Var[Int]
val c = a.map(_ * 10)          // Signal[Int]

val result = for
  x <- a          // Var から直接
  y <- b          // Var から直接
  z <- c          // Signal から
yield x + y + z   // Signal[Int] が返る
```

#### Var と Signal の型の関係

```
Var[A]   — 読み書き可能。set / update / += 等が使える
  │
  └─ .map / for → Signal[A] — 読み取り専用。set 不可（コンパイルエラー）
```

```scala
val count = Var(0)              // Var[Int]   — 書ける
val doubled = count.map(_ * 2)  // Signal[Int] — 読むだけ

count.set(5)     // OK
doubled.set(10)  // コンパイルエラー: Signal には set がない
count += 1       // OK
doubled += 1     // コンパイルエラー: extension は Var[Int] にのみ定義
```

### 5.4 完全な例

```html
<!-- TodoApp.melt -->

case class Todo(id: Int, text: String, done: Boolean)
case class Props(title: String)

<script lang="scala" props="Props">
  val todos = Var(List.empty[Todo])
  val newText = Var("")
  val nextId = Var(1)

  // 派生値
  val remaining = todos.map(_.count(!_.done))
  val allDone = remaining.map(_ == 0)
  val summary = for
    ts <- todos
    r  <- remaining
  yield s"${ts.length} items, $r remaining"

  // 操作
  def addTodo(): Unit =
    val text = newText.now()
    if text.nonEmpty then
      todos.append(Todo(nextId.now(), text, false))
      nextId += 1
      newText.set("")

  def toggleTodo(id: Int): Unit =
    todos.updateWhere(_.id == id)(t => t.copy(done = !t.done))

  def removeTodo(id: Int): Unit =
    todos.removeWhere(_.id == id)

  def clearDone(): Unit =
    todos.removeWhere(_.done)

  def row(todo: Todo): Html = {
    <li class={if todo.done then "done" else ""}>
      <input type="checkbox"
        checked={todo.done}
        onchange={_ => toggleTodo(todo.id)} />
      <span>{todo.text}</span>
      <button onclick={_ => removeTodo(todo.id)}>x</button>
    </li>
  }
</script>

<div class="todo-app">
  <h1>{props.title}</h1>
  <div class="input-row">
    <input bind:value={newText} placeholder="What needs to be done?" />
    <button onclick={addTodo}>Add</button>
  </div>
  <ul>
    {todos.keyed(_.id).map(row)}
  </ul>
  <p>{summary}</p>
  <button onclick={clearDone} disabled={allDone}>Clear done</button>
</div>

<style>
.done span { text-decoration: line-through; color: #999; }
</style>
```

---

## 6. ライフサイクル & リソース管理

### 6.1 設計思想

Cats Effect の `Resource` パターンに着想を得ている。リソースの獲得と解放を常にペアにし、解放忘れを構造的に防ぐ。ただし melt-runtime 自体は Cats Effect に依存しない。

### 6.2 `managed` — リソースの安全な管理

獲得（acquire）と解放（release）をペアにして登録する。コンポーネント破棄時に自動で release が呼ばれる。

```scala
// タイマー
val timer = managed(
  acquire = dom.window.setInterval(() => count += 1, 1000),
  release = id => dom.window.clearInterval(id)
)

// WebSocket
val ws = managed(
  acquire = new dom.WebSocket("ws://localhost:8080"),
  release = _.close()
)
ws.onmessage = (e: dom.MessageEvent) => messages.append(e.data.toString)
```

effect 内で使うと、Signal 変化時に前回のリソースが自動解放されてから新しいリソースが獲得される:

```scala
effect(serverUrl) { url =>
  val ws = managed(
    acquire = new dom.WebSocket(url),
    release = _.close()
  )
  ws.onmessage = (e: dom.MessageEvent) =>
    messages.append(e.data.toString)
}
// serverUrl が "ws://a" → "ws://b" に変わると:
//   1. "ws://a" の接続が close() される
//   2. "ws://b" の接続が acquire される
```

### 6.3 `effect` — リアクティブな副作用

Signal の変化を監視して副作用を実行する。依存は明示的に指定する。

```scala
// 単一Signal
effect(count) { c =>
  dom.document.title = s"Count: $c"
}

// 複数Signal
effect(count, name) { (c, n) =>
  dom.document.title = s"$n: $c"
}
```

effect 内でリソースを管理する場合は `managed` または `onCleanup` を使う:

```scala
// managed でリソース管理（推奨）
effect(serverUrl) { url =>
  val ws = managed(
    acquire = new dom.WebSocket(url),
    release = _.close()
  )
  ws.onmessage = (e: dom.MessageEvent) =>
    messages.append(e.data.toString)
}

// onCleanup で手動管理（より細かい制御が必要な場合）
effect(serverUrl) { url =>
  val ws = new dom.WebSocket(url)
  ws.onmessage = (e: dom.MessageEvent) =>
    messages.append(e.data.toString)
  onCleanup(() => ws.close())
}
```

使い分け:

| 状況 | 使うもの |
|---|---|
| 単純な監視（リソースなし） | `effect(sig) { ... }` |
| effect内でリソース管理 | `effect` + `managed` |
| effect内で細かい手動制御 | `effect` + `onCleanup` |
| コンポーネントレベルの固定リソース | トップレベルの `managed` |

### 6.4 非同期処理

#### `MeltEffect` 型クラス — エフェクトシステム抽象化

melt-runtime は特定のエフェクトライブラリに依存しない。`MeltEffect` 型クラスでエフェクト型を抽象化する。

```scala
// melt-runtime が定義
trait MeltEffect[F[_]]:
  def runAsync[A](fa: F[A])(
    onSuccess: A => Unit,
    onError: Throwable => Unit
  ): () => Unit   // 返り値 = キャンセル関数

  def delay[A](a: => A): F[A]
```

デフォルトで `Future` に対する実装を同梱（追加依存なし）。`ExecutionContext` は melt-runtime がデフォルト（Scala.js の JS イベントループ）を提供するが、ユーザーが `given ExecutionContext` を定義すれば上書きできる:

```scala
// melt-runtime が提供するデフォルト EC
given meltDefaultEC: ExecutionContext = ExecutionContext.global

given MeltEffect[Future] with
  def runAsync[A](fa: Future[A])(onSuccess: A => Unit, onError: Throwable => Unit) =
    fa.onComplete {
      case Success(a) => onSuccess(a)
      case Failure(e) => onError(e)
    }
    () => ()   // Future はキャンセル不可

  def delay[A](a: => A): Future[A] = Future(a)
```

#### `asyncState` — 非同期状態管理

非同期処理の loading / value / error を自動管理するヘルパー。

```scala
// マウント時に1回 fetch
val data = asyncState {
  fetch("/api/data")   // Future[Data]
}

// data.loading: Signal[Boolean]
// data.value: Signal[Option[Data]]
// data.error: Signal[Option[Throwable]]
```

Signal に連動した自動再 fetch:

```scala
val userId = Var(1)

// userId が変わるたびに再fetch + 前回のリクエストを自動キャンセル
val user = asyncState.derived(userId) { id =>
  fetch(s"/api/users/$id")
}
```

テンプレートでの使い方:

```html
{if user.loading then
  <p>Loading...</p>
else if user.error.map(_.isDefined) then
  <p>Error: {user.error.map(_.get.getMessage)}</p>
else
  <p>{user.value.map(_.name)}</p>}
```

#### effect 内での非同期（細かい制御が必要な場合）

`asyncState` で足りない場合は、effect 内で `asyncRun` + `onCleanup` を使う:

```scala
val userId = Var(1)
val userData = Var(Option.empty[User])
val loading = Var(false)

effect(userId) { id =>
  loading.set(true)

  val cancel = asyncRun(fetch(s"/api/users/$id")) {
    case Success(user) =>
      userData.set(Some(user))
      loading.set(false)
    case Failure(e) =>
      loading.set(false)
  }

  onCleanup(cancel)  // userId 変更時に前回の fetch をキャンセル
}
```

使い分け:

| パターン | 使うもの |
|---|---|
| マウント時に1回だけ fetch | `asyncState { fetch(...) }` |
| Signal 変化で自動再 fetch | `asyncState.derived(signal) { ... }` |
| 細かく制御したい | `effect` + `asyncRun` + `onCleanup` |

#### Cats Effect / ZIO 統合

別パッケージとして提供。`MeltEffect` 型クラスの実装を差し替えるだけで、`asyncState` 等の全 API がそのまま動く。

```scala
// melt-cats-effect パッケージ
given MeltEffect[IO] with
  def runAsync[A](fa: IO[A])(onSuccess: A => Unit, onError: Throwable => Unit) =
    val fiber = fa.unsafeRunCancelable {
      case Right(a) => onSuccess(a)
      case Left(e)  => onError(e)
    }
    () => fiber.cancel.unsafeRunSync()   // キャンセル可能

  def delay[A](a: => A): IO[A] = IO.delay(a)
```

```scala
// melt-zio パッケージ
given MeltEffect[Task] with
  // ZIO 用の実装
  ...
```

使う側のコードは変わらない:

```scala
// Future 版
val data = asyncState { fetch("/api/data") }

// Cats Effect IO 版（パッケージ追加のみ。書き方同じ）
val data = asyncState { fetchIO("/api/data") }
```

### 6.5 ライフサイクル完全例

```html
<!-- Dashboard.melt -->

case class Props(apiBase: String)

<script lang="scala" props="Props">
  val count = Var(0)
  val serverUrl = Var(s"${props.apiBase}/ws")
  val messages = Var(List.empty[String])

  // 固定リソース: コンポーネント破棄時に解放
  val timer = managed(
    acquire = dom.window.setInterval(() => count += 1, 1000),
    release = id => dom.window.clearInterval(id)
  )

  // 非同期データ: マウント時に1回 fetch
  val config = asyncState {
    fetch(s"${props.apiBase}/config")
  }

  // Signal連動の動的リソース: serverUrl 変化で安全に再接続
  effect(serverUrl) { url =>
    val ws = managed(
      acquire = new dom.WebSocket(url),
      release = _.close()
    )
    ws.onmessage = (e: dom.MessageEvent) =>
      messages.append(e.data.toString)
  }

  // 単純な監視
  effect(count) { c =>
    dom.document.title = s"Dashboard ($c)"
  }

  def msgRow(msg: String): Html = {
    <li>{msg}</li>
  }
</script>

<div class="dashboard">
  {if config.loading then
    <p>Loading config...</p>
  else
    <div>
      <h1>Dashboard — {count} ticks</h1>
      <ul>{messages.map(msgRow)}</ul>
    </div>}
</div>

<style>
.dashboard { padding: 2rem; }
</style>
```

---

## 7. コンポーネント間の状態共有

### 7.1 Store（グローバル共有）

**確定事項:** 通常のScala `object` に `Var` / `Signal` を持たせ、import して使う。専用のStore APIは不要。

```scala
// stores/CounterStore.scala（普通の Scala ファイル — meltc は関与しない）
package stores

import melt.runtime.Var

object CounterStore:
  val count = Var(0)
  val doubled = count.map(_ * 2)
  def increment(): Unit = count += 1
  def reset(): Unit = count.set(0)
```

```html
<!-- ComponentA.melt -->
<script lang="scala">
  import stores.CounterStore
</script>

<button onclick={CounterStore.increment}>
  Count: {CounterStore.count}
</button>
<p>Doubled: {CounterStore.doubled}</p>
```

```html
<!-- ComponentB.melt（同じ Store を import → 自動的に同期）-->
<script lang="scala">
  import stores.CounterStore
</script>

<p>Count from another component: {CounterStore.count}</p>
```

#### ファイルの処理フロー

`.scala` ファイルと `.melt` ファイルは別々の経路でコンパイルされ、最終的に scalac で合流する:

```
.melt ファイル → meltc → .scala → scalac + Scala.js → main.js
.scala ファイル → そのまま → scalac + Scala.js → main.js
```

meltc は `.melt` ファイルだけを処理する。Store の `.scala` ファイルには一切触らない。

#### クロスプラットフォーム（JVM + JS）の場合

| コード | 置く場所 | 使えるもの |
|---|---|---|
| 型定義（case class, enum） | shared or client | Scala 標準ライブラリのみ |
| Store（`Var` を使う） | client のみ | melt-runtime |
| `.melt` ファイル | client のみ | melt-runtime |
| サーバーコード | server のみ | JVM ライブラリ |

### 7.2 Context（親→子孫へのDI）

**確定事項:** React 風の Context オブジェクトを使う。名前のついたオブジェクトを介して provide / inject するため、コードの追跡が容易。

#### Context の定義

```scala
// contexts/ThemeContext.scala
package contexts

import melt.runtime.Context
import shared.Theme

val ThemeContext = Context.create[Theme](Theme.Light)  // デフォルト値付き
```

#### Provider（親コンポーネント）

```html
<!-- ThemeProvider.melt -->
<script lang="scala" props="Props">
  import contexts.ThemeContext
  import shared.Theme

  type Props = (theme: Theme, children: () => Element)

  ThemeContext.provide(props.theme)
</script>

{props.children()}
```

#### Consumer（子孫コンポーネント）

```html
<!-- ThemedButton.melt -->
<script lang="scala">
  import contexts.ThemeContext

  val theme = ThemeContext.inject()  // 最寄りの祖先から取得
</script>

<button class={theme.cssClass}>Themed Button</button>
```

#### 使用例: アプリ全体にテーマを適用

```html
<!-- App.melt -->
<script lang="scala">
  import shared.Theme
  import components.{ThemeProvider, ThemedButton}

  val isDark = Var(false)
  val theme = isDark.map(d => if d then Theme.Dark else Theme.Light)
</script>

<ThemeProvider theme={theme}>
  <div>
    <ThemedButton />
    <button onclick={isDark.toggle}>Toggle Theme</button>
  </div>
</ThemeProvider>
```

#### 同じ型の複数 Context

Context オブジェクトが別であれば、同じ型でも区別できる:

```scala
val UserNameContext = Context.create[String]("Guest")
val AdminNameContext = Context.create[String]("Admin")

// Provider 側
UserNameContext.provide("Taro")
AdminNameContext.provide("SuperAdmin")

// Consumer 側
val userName = UserNameContext.inject()    // "Taro"
val adminName = AdminNameContext.inject()  // "SuperAdmin"
```

#### Context 未提供時の挙動

`Context.create` で指定したデフォルト値が使われる。デフォルト値なしで Context を作ることもでき、その場合は `inject()` が `Option[T]` を返す:

```scala
val RequiredCtx = Context.create[Theme](Theme.Light)  // デフォルトあり
val OptionalCtx = Context.createOptional[Theme]        // デフォルトなし

val a = RequiredCtx.inject()   // Theme（必ず値がある）
val b = OptionalCtx.inject()   // Option[Theme]（None の可能性あり）
```

### 7.3 Store vs Context の使い分け

| | Store（object） | Context |
|---|---|---|
| スコープ | アプリ全体（グローバル） | コンポーネントツリーの一部 |
| 定義場所 | `.scala` ファイル | `.scala` ファイル |
| 使い方 | import するだけ | provide → inject |
| 複数インスタンス | 不可（シングルトン） | 可能（ツリーごとに異なる値） |
| 用途 | 認証状態、アプリ設定 | テーマ、ロケール、権限 |

---

## 8. イベントバインディング

### 8.1 基本

HTML 標準のイベント属性名（`onclick`, `oninput`, `onsubmit` 等）をそのまま使う。値には Scala の関数を渡す。

```html
<script lang="scala">
  val count = Var(0)

  def handleClick(e: dom.MouseEvent): Unit =
    count += 1

  def handleSubmit(e: dom.Event): Unit =
    e.preventDefault()
    // submit logic
</script>

<!-- 関数参照 -->
<button onclick={handleClick}>+1</button>

<!-- インラインラムダ -->
<button onclick={_ => count += 1}>+1</button>

<!-- イベントオブジェクトを使う -->
<button onclick={e => println(e.clientX)}>Log position</button>

<!-- フォーム -->
<form onsubmit={handleSubmit}>
  <button type="submit">Submit</button>
</form>
```

### 8.2 イベント型の自動推論

meltc が要素名 + イベント名からイベントオブジェクトの型を自動推論する。

| イベント | 型 |
|---|---|
| `onclick` | `dom.MouseEvent` |
| `ondblclick` | `dom.MouseEvent` |
| `onkeydown` / `onkeyup` / `onkeypress` | `dom.KeyboardEvent` |
| `onfocus` / `onblur` | `dom.FocusEvent` |
| `oninput` / `onchange` | `dom.Event` |
| `onsubmit` | `dom.Event` |
| `onscroll` | `dom.UIEvent` |
| `ondrag` / `ondrop` | `dom.DragEvent` |
| `onmouseenter` / `onmouseleave` | `dom.MouseEvent` |
| `ontouchstart` / `ontouchend` | `dom.TouchEvent` |

```html
<!-- meltc が型を推論 -->
<div onkeydown={e => if e.key == "Enter" then submit()}>
  <!-- e の型は dom.KeyboardEvent → e.key が補完で出る -->
</div>
```

### 8.3 meltc が生成するコード

```html
<button onclick={handleClick}>Click</button>
```

```scala
// meltc が生成
val _button0 = dom.document.createElement("button")
_button0.addEventListener("click", (e: dom.MouseEvent) => handleClick(e))
```

### 8.4 カスタムコンポーネントへのイベント伝搬

カスタムコンポーネントのイベントハンドラは Props の関数型フィールドとして定義する。特別な構文は不要。

```html
<!-- Button.melt -->
<script lang="scala" props="Props">
  case class Props(
    label: String,
    onClick: dom.MouseEvent => Unit = _ => ()
  ) extends ButtonHtmlProps
</script>

<button onclick={props.onClick} {...props.html}>
  {props.label}
</button>
```

```html
<!-- 使用側 -->
<Button label="Submit" onClick={e => handleSubmit(e)} />
```

### 8.5 イベント修飾子

Svelte 4 の `|preventDefault|stopPropagation` のような修飾子構文は導入しない。Scala の関数で自然に書ける。

```html
<script lang="scala">
  // preventDefault
  def handleSubmit(e: dom.Event): Unit =
    e.preventDefault()
    // submit logic

  // stopPropagation
  def handleClick(e: dom.MouseEvent): Unit =
    e.stopPropagation()
    // click logic

  // once（1回だけ実行）
  var clicked = false
  def handleOnce(e: dom.MouseEvent): Unit =
    if !clicked then
      clicked = true
      // logic
</script>
```

---

## 9. 双方向バインディング

### 9.1 基本

`bind:` ディレクティブは HTML 要素の属性と `Var` を双方向に接続する。`Signal` は読み取り専用のため `bind:` に使用できない（scalac が型エラーで弾く）。

```html
<script lang="scala">
  val name = Var("")
  val agreed = Var(false)
  val selected = Var("apple")
  val content = Var("")
  val volume = Var(50)
</script>

<!-- テキスト入力 -->
<input bind:value={name} placeholder="Your name" />

<!-- チェックボックス -->
<input type="checkbox" bind:checked={agreed} />

<!-- セレクト -->
<select bind:value={selected}>
  <option value="apple">Apple</option>
  <option value="banana">Banana</option>
</select>

<!-- テキストエリア -->
<textarea bind:value={content}></textarea>

<!-- レンジスライダー -->
<input type="range" bind:value={volume} min="0" max="100" />
```

### 9.2 ラジオボタン / 複数チェックボックス（bind:group）

Svelte 風の `bind:group` を採用する。同じ `Var` への参照がグループを定義するため、HTML の `name` 属性は不要。

```html
<script lang="scala">
  val flavor = Var("vanilla")
  val toppings = Var(List.empty[String])
</script>

<!-- ラジオボタン: Var[String] にバインド → 排他選択 -->
<label><input type="radio" bind:group={flavor} value="vanilla" /> Vanilla</label>
<label><input type="radio" bind:group={flavor} value="chocolate" /> Chocolate</label>
<label><input type="radio" bind:group={flavor} value="strawberry" /> Strawberry</label>

<!-- 複数チェックボックス: Var[List[String]] にバインド → チェック時に追加、解除時に削除 -->
<label><input type="checkbox" bind:group={toppings} value="nuts" /> Nuts</label>
<label><input type="checkbox" bind:group={toppings} value="syrup" /> Syrup</label>
<label><input type="checkbox" bind:group={toppings} value="cherry" /> Cherry</label>
```

### 9.3 数値入力の自動型変換

HTML の `input.value` は常に `String` だが、meltc は `Var` の型を見て自動的に変換コードを生成する。これは Scala の型システムがあるからこそ可能な機能。

```html
<script lang="scala">
  val age = Var(0)       // Var[Int]
  val price = Var(0.0)   // Var[Double]
</script>

<!-- Var[Int] に bind → 文字列↔整数の自動変換 -->
<input type="number" bind:value={age} />

<!-- Var[Double] に bind → 文字列↔小数の自動変換 -->
<input type="number" bind:value={price} />
```

meltc が生成するコード:

```scala
// Var[Int] の場合
Bind.inputInt(inputEl, age)
// 内部: inputEl.value = age.now().toString
//       input時: age.set(inputEl.value.toIntOption.getOrElse(age.now()))

// Var[Double] の場合
Bind.inputDouble(inputEl, price)
```

### 9.4 カスタムコンポーネントへの双方向バインディング

カスタムコンポーネントには `bind:` 構文を使わない。代わりに `Var` を props としてそのまま渡す。子コンポーネントは受け取った `Var` に対して `.set()` で書き込める。

```html
<!-- ColorPicker.melt -->
type Props = (value: Var[String])

<script lang="scala" props="Props">
</script>

<input type="color" bind:value={props.value} />
```

```html
<!-- 使う側: Var を渡すだけ。bind: は不要 -->
<script lang="scala">
  val color = Var("#ff0000")
</script>

<ColorPicker value={color} />
<p>Selected: {color}</p>
```

`Var` を渡す行為そのものが双方向バインディングであり、特別な構文は必要ない。

### 9.5 bind サポート一覧

| 要素 | 構文 | Var の型 |
|---|---|---|
| テキスト入力 | `bind:value` | `Var[String]` |
| 数値入力 | `bind:value` | `Var[Int]` or `Var[Double]` |
| チェックボックス（単体） | `bind:checked` | `Var[Boolean]` |
| ラジオボタン | `bind:group` | `Var[String]` |
| 複数チェックボックス | `bind:group` | `Var[List[String]]` |
| セレクト | `bind:value` | `Var[String]` |
| テキストエリア | `bind:value` | `Var[String]` |
| レンジ | `bind:value` | `Var[Int]` or `Var[Double]` |
| カスタムコンポーネント | props に `Var` を渡す | 任意 |

---

## 10. コンポーネントの組み合わせ方

### 10.1 コンポーネントの参照

**確定事項:** コンポーネントは常に明示的に import する。自動解決はしない。PascalCase のタグが `.melt` コンポーネントとして認識される。

```html
<script lang="scala">
  import components.Header
  import components.Counter
  import pages.{Home, About}
</script>

<div>
  <Header title="My App" />      <!-- PascalCase → コンポーネント -->
  <Counter count={0} />
  <div class="content">...</div>  <!-- lowercase → HTML要素 -->
</div>
```

### 10.2 Props の渡し方

```html
<script lang="scala">
  import components.Counter

  val name = Var("Hello")
  val counterProps = Counter.Props(label = "Hello", count = 42)
</script>

<!-- 静的値 -->
<Counter label="Hello" count={42} />

<!-- リアクティブ値 -->
<Counter label={name} count={count} />

<!-- スプレッド（case class を展開）-->
<Counter {...counterProps} />

<!-- 省略記法: 変数名と prop 名が同じ -->
<Counter {label} {count} />
```

### 10.3 動的コンポーネント（パターンマッチ）

**確定事項:** 動的コンポーネントの切り替えはパターンマッチで行う。`sealed trait` + パターンマッチにより、scalac が網羅性チェックを行い、Props の型不一致をコンパイル時に検出する。

```html
<script lang="scala">
  import pages.{HomePage, AboutPage, UserProfilePage}

  sealed trait Route
  case object Home extends Route
  case object About extends Route
  case class UserPage(userId: Int) extends Route

  val route = Var[Route](Home)

  def renderRoute(r: Route): Html = r match {
    case Home         => { <HomePage /> }
    case About        => { <AboutPage version="2.0" /> }
    case UserPage(id) => { <UserProfilePage userId={id} /> }
    // Route を追加して case を書き忘れ → scalac が警告
  }
</script>

<div class="app">
  {renderRoute(route.now())}
</div>
```

### 10.4 循環参照

meltc は循環参照を特別扱いしない。生成された `.scala` ファイル間で循環参照があれば scalac がエラーを出す。

### 10.5 完全な例: アプリ構成

```
src/
├── components/
│   ├── App.melt
│   ├── Header.melt
│   ├── TodoList.melt
│   └── TodoItem.melt
├── pages/
│   ├── Home.melt
│   └── About.melt
├── stores/
│   └── TodoStore.scala
├── contexts/
│   └── ThemeContext.scala
└── shared/
    └── Models.scala
```

```html
<!-- App.melt -->
<script lang="scala">
  import components.Header
  import pages.{Home, About}
  import shared.Route

  val route = Var[Route](Route.Home)

  def renderRoute(r: Route): Html = r match {
    case Route.Home  => { <Home /> }
    case Route.About => { <About /> }
  }
</script>

<div class="app">
  <Header onNavigate={r => route.set(r)} />
  {renderRoute(route.now())}
</div>

<style>
.app { max-width: 800px; margin: 0 auto; }
</style>
```

```html
<!-- TodoList.melt -->
<script lang="scala">
  import components.TodoItem
  import stores.TodoStore

  def item(todo: Todo): Html = {
    <TodoItem
      todo={todo}
      onToggle={() => TodoStore.toggle(todo.id)}
      onRemove={() => TodoStore.remove(todo.id)}
    />
  }
</script>

<ul>
  {TodoStore.items.keyed(_.id).map(item)}
</ul>

<style></style>
```

---

## 11. テンプレート制御構文

### 11.1 設計思想

**確定事項:** テンプレート内の制御構文は全て Scala の式で書く。`{#if}` / `{#each}` / `{#match}` のようなテンプレート専用構文は一切導入しない。

`{ }` 内に書くのは常に Scala の式であり、その式が `Html` を返す。これにより：

- 覚えるべき構文がゼロ（Scala を知っていればテンプレートも書ける）
- scalac の型チェック・網羅性チェックがテンプレート内にも完全に効く
- パーツ関数（`def f(): Html`）との一貫性が保たれる

### 11.2 条件分岐（if / else）

```html
<!-- 単純な条件 -->
{if count.map(_ > 10) then
  <p>Too many!</p>
else
  <p>Keep going</p>}

<!-- else if -->
{if count.map(_ > 10) then
  <p>Too many!</p>
else if count.map(_ > 5) then
  <p>Getting there</p>
else
  <p>Keep going</p>}

<!-- 条件付き表示（else なし） -->
{if showWarning then
  <p class="warning">Warning!</p>}
```

### 11.3 パターンマッチ（match）

Scala の `match` 式をそのまま使う。`sealed trait` + パターンマッチで scalac が網羅性チェックを行う。

```html
<script lang="scala">
  sealed trait LoadState[+A]
  case object Loading extends LoadState[Nothing]
  case class Failed(error: Throwable) extends LoadState[Nothing]
  case class Loaded[A](data: A) extends LoadState[A]

  val state = Var[LoadState[User]](Loading)
</script>

{state match
  case Loading    => <div class="spinner">Loading...</div>
  case Failed(e)  => <p class="error">Error: {e.getMessage}</p>
  case Loaded(u)  =>
    <div class="profile">
      <h2>{u.name}</h2>
      <p>{u.email}</p>
    </div>}
```

### 11.4 リスト描画（map）

Scala の `map` でリストを描画する。パーツ関数と組み合わせて使う。

```html
<script lang="scala">
  val items = Var(List("Apple", "Banana", "Cherry"))

  def fruitRow(name: String): Html = {
    <li class="fruit">{name}</li>
  }
</script>

<!-- map + パーツ関数 -->
<ul>
  {items.map(fruitRow)}
</ul>

<!-- map + インラインHtml -->
<ul>
  {items.map(name => <li>{name}</li>)}
</ul>
```

#### キー付きリスト（効率的な差分更新）

`keyed` メソッドでキーを指定してから `map` する。DOM の差分更新が効率的になる。

```html
<script lang="scala">
  case class Todo(id: Int, text: String, done: Boolean)
  val todos = Var(List.empty[Todo])

  def todoRow(todo: Todo): Html = {
    <li class={if todo.done then "done" else ""}>
      <span>{todo.text}</span>
    </li>
  }
</script>

<ul>
  {todos.keyed(_.id).map(todoRow)}
</ul>
```

meltc が生成するコード:

```scala
// keyed なし → 全置換（シンプルだが非効率）
// items.map(f) → Bind.list(items, f, anchor)

// keyed あり → キーベースの差分更新
// items.keyed(_.id).map(f) → Bind.each(items, _.id, f, anchor)
```

#### 空リストの場合

Scala の `if` 式と組み合わせる:

```html
{if items.map(_.isEmpty) then
  <p>No items yet</p>
else
  <ul>{items.keyed(_.id).map(todoRow)}</ul>}
```

### 11.5 式の組み合わせ

全てが Scala の式なので、自由に組み合わせられる:

```html
<script lang="scala">
  sealed trait Tab
  case object TodoTab extends Tab
  case object DoneTab extends Tab

  val tab = Var[Tab](TodoTab)
  val todos = Var(List.empty[Todo])

  val visible = for
    t  <- tab
    ts <- todos
  yield t match
    case TodoTab => ts.filter(!_.done)
    case DoneTab => ts.filter(_.done)

  def todoRow(todo: Todo): Html = {
    <li>{todo.text}</li>
  }
</script>

<div>
  <nav>
    <button onclick={_ => tab.set(TodoTab)}>Todo</button>
    <button onclick={_ => tab.set(DoneTab)}>Done</button>
  </nav>

  {if visible.map(_.isEmpty) then
    <p>{tab match
      case TodoTab => "All done!"
      case DoneTab => "Nothing completed yet"}</p>
  else
    <ul>{visible.keyed(_.id).map(todoRow)}</ul>}
</div>
```

### 11.6 Svelte / React との比較

| 操作 | Svelte | React | Melt |
|---|---|---|---|
| 条件分岐 | `{#if}...{:else}...{/if}` | `{cond ? a : b}` | `{if cond then a else b}` |
| パターンマッチ | なし | なし | `{x match case ... => ...}` |
| リスト | `{#each items as x (key)}` | `{items.map(x => ...)}` | `{items.keyed(_.id).map(f)}` |
| 空リスト | `{:empty}` | 手動 if | `{if items.map(_.isEmpty) then ...}` |
| 非同期 | `{#await}` | 手動 | `asyncState` + `match` |
| 制御構文の数 | 4つ | 0 | 0 |
| 要素ディレクティブ | `bind:` `class:` `use:` `transition:` `animate:` | なし | `bind:` `class:` `use:` `transition:` `animate:` |
| 型安全性 | 部分的 | TypeScript依存 | 完全（scalac） |

---

## 12. CSS スタイリング

### 12.1 スコープ付き CSS

`<style>` 内の CSS はコンポーネントにスコープされる。meltc が各セレクタにスコープ ID を付与し、他のコンポーネントに影響しない。

```html
<!-- Button.melt -->
<script lang="scala" props="Props">
  case class Props(label: String)
</script>

<button class="btn">{props.label}</button>

<style>
  .btn {
    background: blue;
    color: white;
  }
</style>
```

meltc が生成する CSS:

```css
.btn[data-melt-x7k2f] {
  background: blue;
  color: white;
}
```

DOM 出力:

```html
<button class="btn" data-melt-x7k2f>Click</button>
```

### 12.2 子コンポーネントのスタイルカスタマイズ

デフォルトではコンポーネントのスタイルは完全にカプセル化され、親から子の内部スタイルを変更できない。カスタマイズには以下の方法を優先順位順に使う。

#### 方法1: variant props（型安全なバリエーション）

コンポーネントが Props で選択肢を用意する。

```html
<!-- Button.melt -->
<script lang="scala" props="Props">
  case class Props(
    label: String,
    variant: String = "primary"
  ) extends ButtonHtmlProps
</script>

<button class={s"btn btn--${props.variant}"} {...props.html}>
  {props.label}
</button>

<style>
  .btn { background: var(--btn-bg, #007bff); color: var(--btn-color, white); }
  .btn--danger { --btn-bg: #dc3545; }
  .btn--success { --btn-bg: #28a745; }
</style>
```

```html
<Button label="削除" variant="danger" />
```

#### 方法2: CSS カスタムプロパティ（CSS 変数）

コンポーネントがカスタマイズポイントを CSS 変数として公開する。CSS 変数は子孫に自然に継承されるため、スコーピングを破壊しない。

```html
<!-- 使用側 -->
<div class="form">
  <Button label="送信" />
</div>

<style>
  .form {
    --btn-bg: green;
    --btn-color: white;
    --btn-padding: 1rem 2rem;
  }
</style>
```

#### 方法3: `styled` 属性（親のスコープ ID をルート要素に付与）

子コンポーネントに `styled` を付けると、子のルート要素に親のスコープ ID が追加される。これにより親のスコープ付き CSS が子のルート要素に届く。

```html
<!-- 使用側 -->
<div class="form">
  <Button label="全幅ボタン" styled />
</div>

<style>
  .form .btn {
    width: 100%;           /* Button のルート要素に届く ✅ */
    margin-bottom: 1rem;
  }
</style>
```

`styled` の動作:

```html
<!-- styled なし → 親のスコープは付かない -->
<button class="btn" data-melt-bbb>通常</button>

<!-- styled あり → 親のスコープ ID がルート要素に追加 -->
<button class="btn" data-melt-bbb data-melt-aaa>全幅ボタン</button>
                                  ^^^^^^^^^^^^^^
                                  親のスコープ ID
```

浸透範囲はルート要素のみ。子コンポーネント内部の要素には親のスコープ ID は付かず、カプセル化は維持される。

```html
<!-- Button の内部が複数要素の場合 -->
<div class="btn-wrapper" data-melt-bbb data-melt-aaa>  ← ルートのみ親スコープ
  <button class="btn" data-melt-bbb>                    ← 子スコープのみ
    <span class="btn-label" data-melt-bbb>送信</span>   ← 子スコープのみ
  </button>
</div>
```

#### 方法4: style 属性（HtmlProps 経由）

1回限りの微調整に使う。

```html
<Button label="特殊" style="opacity: 0.5;" />
```

### 12.3 カスタマイズの推奨優先順位

| 優先度 | 方法 | 用途 | スコープ安全 |
|---|---|---|---|
| 1 | variant props | 定型バリエーション | ✅ 完全 |
| 2 | CSS カスタムプロパティ | 色・サイズ等の柔軟な調整 | ✅ 完全 |
| 3 | `styled` | ルート要素のレイアウト調整 | ✅ ルートのみ |
| 4 | style 属性 | 1回限りの微調整 | ✅ 完全 |

### 12.4 完全な例

```html
<!-- Button.melt -->
<script lang="scala" props="Props">
  case class Props(
    label: String,
    variant: String = "primary"
  ) extends ButtonHtmlProps
</script>

<button class={s"btn btn--${props.variant}"} {...props.html}>
  {props.label}
</button>

<style>
  .btn {
    background: var(--btn-bg, #007bff);
    color: var(--btn-color, white);
    padding: var(--btn-padding, 0.5rem 1rem);
    border-radius: var(--btn-radius, 4px);
    font-size: var(--btn-font-size, 1rem);
    border: none;
    cursor: pointer;
  }
  .btn--danger { --btn-bg: #dc3545; }
  .btn--success { --btn-bg: #28a745; }
</style>
```

```html
<!-- 使用側 -->
<script lang="scala">
  import components.Button
</script>

<div class="form">
  <!-- variant: コンポーネントの選択肢 -->
  <Button label="削除" variant="danger" />

  <!-- CSS 変数: 色をカスタマイズ -->
  <Button label="送信" />

  <!-- styled: ルート要素の幅を親から調整 -->
  <Button label="全幅ボタン" styled />

  <!-- style 属性: 透明度の微調整 -->
  <Button label="控えめ" style="opacity: 0.7;" />
</div>

<style>
  .form {
    --btn-bg: #333;
    --btn-color: #eee;
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
  }

  .form .btn {
    width: 100%;
  }
</style>
```

---

## 13. テンプレートディレクティブ

テンプレート内の制御構文（if / match / map）は全て Scala の式だが、**要素に付与するディレクティブ**は専用構文を提供する。これらは要素の属性的な振る舞いであり、制御フローではない。

### 13.1 `bind:this`（DOM 要素参照）

DOM 要素の直接参照が必要なケース（Canvas, Video, 外部ライブラリ連携等）に使う。

```html
<script lang="scala">
  val canvasRef = Ref.empty[dom.html.Canvas]

  effect(canvasRef) { canvas =>
    val ctx = canvas.getContext("2d")
    ctx.fillRect(0, 0, 100, 100)
  }
</script>

<canvas bind:this={canvasRef} width="400" height="300"></canvas>
```

```scala
// melt-runtime
class Ref[A <: dom.Element]:
  private var _el: Option[A] = None
  def get: Option[A] = _el
  def set(el: A): Unit = _el = Some(el)
  def foreach(f: A => Unit): Unit = _el.foreach(f)

object Ref:
  def empty[A <: dom.Element]: Ref[A] = new Ref[A]
```

meltc は `bind:this={ref}` を見つけると、要素生成後に `ref.set(element)` を呼ぶコードを生成する。

### 13.2 `class:` ディレクティブ（条件付きクラス）

`Signal[Boolean]` に応じてクラスを動的に追加/削除する。

```html
<script lang="scala">
  val isActive = Var(true)
  val disabled = Var(false)
  val selected = Var(false)
</script>

<!-- フル記法 -->
<div class="item" class:active={isActive} class:disabled={disabled}>

<!-- 省略記法（変数名 = クラス名の場合）-->
<div class:selected class:disabled>
```

meltc が生成するコード:

```scala
Bind.classToggle(_div0, "active", isActive)
// isActive が true → classList.add("active")
// isActive が false → classList.remove("active")
```

### 13.3 `use:` アクション

DOM 要素にカスタムロジック（ツールチップ、ドラッグ、フォーカス管理等）を宣言的に付与する。

#### 組み込みアクション

melt-runtime がよく使うアクションを提供。import して使うだけ。

```html
<script lang="scala">
  import melt.runtime.actions.*
</script>

<input use:autoFocus />
<div use:clickOutside={() => menuOpen.set(false)}>...</div>
<div use:trapFocus>...</div>
```

#### カスタムアクション

`.scala` ファイルに定義して import する。`<script>` 内に長い定義を書く必要はない。

```scala
// actions/Tooltip.scala
package actions

import org.scalajs.dom
import melt.runtime.Action

val tooltip = Action[String] { (el, text) =>
  val tip = dom.document.createElement("div")
  tip.className = "tooltip"
  tip.textContent = text
  el.appendChild(tip)
  onCleanup(() => tip.remove())
}
```

```html
<!-- Component.melt -->
<script lang="scala">
  import actions.tooltip
</script>

<div use:tooltip={"Hello"}>Hover me</div>
```

#### Action ヘルパー

```scala
// melt-runtime
object Action:
  // 引数付きアクション
  def apply[P](f: (dom.Element, P) => Unit): Action[P]
  // 引数なしアクション
  def apply(f: dom.Element => Unit): Action[Unit]
```

`Action` ブロック内で `onCleanup` を使ってクリーンアップを登録できる。コンポーネント破棄時に自動実行される。

#### リアクティブな引数

引数が `Var` / `Signal` の場合、値が変わるたびにアクションを再適用する:

```html
<script lang="scala">
  import actions.tooltip
  val tooltipText = Var("Hello")
</script>

<div use:tooltip={tooltipText}>Hover me</div>
```

meltc が自動で effect + cleanup パターンを生成:

```scala
effect(tooltipText) { text =>
  val cleanup = tooltip(_div0, text)
  onCleanup(cleanup)
}
```

### 13.4 `transition:` / `in:` / `out:`（トランジション）

要素が DOM に追加/削除されるときにアニメーションを実行する。

```html
<script lang="scala">
  import melt.runtime.transition.{fade, fly, slide, scale}

  val visible = Var(false)
</script>

<!-- 双方向（出入り同じアニメーション）-->
{if visible then
  <div transition:fade={{ duration = 300 }}>Fades in and out</div>
else <span></span>}

<!-- 入りと出で別のアニメーション -->
{if visible then
  <div in:fly={{ y = 200 }} out:fade>Flies in, fades out</div>
else <span></span>}
```

#### トランジション関数の型

```scala
// melt-runtime
trait Transition:
  def apply(
    node: dom.Element,
    params: TransitionParams,
    direction: Direction
  ): TransitionConfig

enum Direction:
  case In, Out, Both

case class TransitionConfig(
  delay: Int = 0,
  duration: Int = 300,
  easing: Double => Double = identity,
  css: Option[(Double, Double) => String] = None,
  tick: Option[(Double, Double) => Unit] = None
)
```

#### 組み込みトランジション

```scala
// melt-runtime が提供
object fade extends Transition     // 透明度
object fly extends Transition      // 位置 + 透明度
object slide extends Transition    // 高さ
object scale extends Transition    // スケール + 透明度
object blur extends Transition     // ブラー + 透明度
object draw extends Transition     // SVG ストローク
```

#### カスタムトランジション

```scala
// カスタムトランジション定義
val typewriter = Transition { (node, params, direction) =>
  val text = node.textContent.getOrElse("")
  TransitionConfig(
    duration = text.length * 50,
    tick = Some { (t, _) =>
      node.textContent = text.take((text.length * t).toInt)
    }
  )
}
```

### 13.5 `animate:` （リストアニメーション）

キー付きリストの並べ替え時にアニメーションを実行する。

```html
<script lang="scala">
  import melt.runtime.animate.flip
</script>

<ul>
  {items.keyed(_.id).map(item =>
    <li animate:flip={{ duration = 300 }}>{item.name}</li>
  )}
</ul>
```

### 13.6 `style:` ディレクティブ（条件付きインラインスタイル）

個別の CSS プロパティをリアクティブに設定する。`class:` の対になるもの。

```html
<script lang="scala">
  val textColor = Var("red")
  val fontSize = Var(16)
</script>

<!-- 個別プロパティ -->
<div style:color={textColor} style:font-size={fontSize.map(s => s"${s}px")}>
  Styled text
</div>

<!-- CSS カスタムプロパティ -->
<div style:--theme-color={textColor}>
  <span>Uses theme color</span>
</div>

<!-- Option[String] → Some で設定、None で削除 -->
<div style:display={if isVisible then Some("block") else None}>
```

meltc が生成するコード:

```scala
Bind.style(_div0, "color", textColor)
// 内部: textColor.subscribe(v => _div0.style.setProperty("color", v))
```

### 13.7 ディレクティブ一覧

| ディレクティブ | 用途 | 引数の型 |
|---|---|---|
| `bind:value` / `bind:checked` / `bind:group` | 双方向バインディング | `Var[T]` |
| `bind:this` | DOM 要素参照 | `Ref[T]` |
| `class:name` | 条件付きクラス | `Signal[Boolean]` |
| `style:property` | 条件付きインラインスタイル | `Signal[String]` or `Signal[Option[String]]` |
| `use:action` | 要素アクション | `Action[P]` |
| `transition:name` | 双方向トランジション | `Transition` |
| `in:name` / `out:name` | 入り/出りトランジション | `Transition` |
| `animate:name` | リスト並べ替えアニメーション | `Animation` |
| `styled` | 親スコープ継承 | なし |

---

## 14. ランタイム API（Window / Document / 生 HTML）

### 14.1 Window API

window イベントやプロパティへのリアクティブなアクセスを提供する。`<script>` 内で API として使う。コンポーネント破棄時に自動でイベントリスナーを解除する。

```html
<script lang="scala">
  import melt.runtime.Window

  Window.on("keydown") { e =>
    if e.asInstanceOf[dom.KeyboardEvent].key == "Escape" then
      modalOpen.set(false)
  }

  val scrollY = Window.scrollY          // Signal[Double]
  val innerWidth = Window.innerWidth    // Signal[Double]
  val innerHeight = Window.innerHeight  // Signal[Double]
</script>

<div class={if scrollY.map(_ > 100) then "scrolled" else ""}>
  Window: {innerWidth} x {innerHeight}
</div>
```

```scala
// melt-runtime
object Window:
  def on(event: String)(handler: dom.Event => Unit): Unit
  val scrollX: Signal[Double]
  val scrollY: Signal[Double]
  val innerWidth: Signal[Double]
  val innerHeight: Signal[Double]
  val online: Signal[Boolean]
```

### 14.2 Document API

```html
<script lang="scala">
  import melt.runtime.Document

  Document.title(pageTitle)    // Signal[String] → document.title をリアクティブに更新
</script>
```

```scala
// melt-runtime
object Document:
  def title(t: Signal[String]): Unit
  def on(event: String)(handler: dom.Event => Unit): Unit
```

### 14.3 生 HTML 挿入

`html()` 関数で生の HTML 文字列を DOM に挿入する。XSS リスクがあるため、meltc がコンパイル時に警告を出す。

```html
<script lang="scala">
  val content = Var("<p>Hello <strong>World</strong></p>")
</script>

<div class="blog-post">
  {html(content)}
</div>
```

meltc が生成するコード:

```scala
val _div0 = dom.document.createElement("div")
Bind.html(_div0, content)
// 内部: _div0.innerHTML = content.now()
//       content.subscribe(v => _div0.innerHTML = v)
```

```scala
// melt-runtime
def html(content: Signal[String]): RawHtml

object Bind:
  def html(el: dom.Element, content: Signal[String]): Unit
```

**注意:** `html()` に渡す文字列はサニタイズされない。信頼できないソース（ユーザー入力等）を渡す場合は必ずエスケープすること。

### 14.4 エラーバウンダリ

子コンポーネントでエラーが発生したときにフォールバック UI を表示する。melt-runtime が `Boundary` コンポーネントを提供する（専用タグではなく通常のコンポーネント）。

```html
<script lang="scala">
  import melt.runtime.Boundary
</script>

<Boundary fallback={(error, reset) => {
  <div class="error">
    <p>Error: {error.getMessage}</p>
    <button onclick={_ => reset()}>Retry</button>
  </div>
}}>
  <RiskyComponent />
</Boundary>
```

```scala
// melt-runtime
object Boundary:
  case class Props(
    children: () => Element,
    fallback: (Throwable, () => Unit) => Element,
    onError: Throwable => Unit = _ => ()
  )
```

`fallback` は2つの引数を受け取る：`error`（発生した例外）と `reset`（再レンダリングを試みる関数）。`onError` はログ送信などの副作用に使う。

### 14.5 `untrack`（依存追跡の除外）

`effect` 内で特定の値を読んでも依存として追跡しない。

```html
<script lang="scala">
  import melt.runtime.untrack

  val data = Var(Map.empty[String, String])
  val timestamp = Var(0L)

  // data が変わったら保存する。timestamp の変化では実行しない
  effect(data) { d =>
    val ts = untrack(timestamp)
    saveToServer(d, ts)
  }
</script>
```

```scala
// melt-runtime
def untrack[A](v: Var[A]): A       // 現在値を返すが依存登録しない
def untrack[A](s: Signal[A]): A
def untrack[A](f: => A): A         // ブロック内の全ての読み取りを非追跡に
```

Melt の `effect` は依存を明示的に引数で指定するため、Svelte ほど `untrack` が必要になるケースは少ない。`effect(data) { ... }` の中で `timestamp.now()` を呼んでも、`timestamp` は引数にないので追跡されない。`untrack` は意図を明確にするための推奨表現として提供する。

### 14.6 `tick`（DOM 更新の待機）

`Var` を変更した直後に DOM を読みたい場合に使う。次のマイクロタスクで実行され、DOM 更新後であることが保証される。

```html
<script lang="scala">
  import melt.runtime.tick

  val message = Var("Hello")
  val messageRef = Ref.empty[dom.html.Paragraph]

  def updateAndMeasure(): Unit =
    message.set("Updated!")
    tick {
      messageRef.foreach { el =>
        println(s"Height: ${el.offsetHeight}")
      }
    }
</script>

<p bind:this={messageRef}>{message}</p>
<button onclick={_ => updateAndMeasure()}>Update & Measure</button>
```

```scala
// melt-runtime
def tick(f: => Unit): Unit =
  dom.window.queueMicrotask(() => f)

def tickAsync(): Future[Unit]   // Future を返すバージョン
```

---

## 15. JS ライブラリ連携

Scala.js は JavaScript と相互運用できる。Melt コンポーネント内から既存の npm パッケージを使う方法を示す。

### 15.1 方法一覧

| 規模 | 方法 | 例 |
|---|---|---|
| ブラウザ標準 API | scala-js-dom（既存） | `dom.window.fetch` |
| 小規模 npm パッケージ | 手動 Facade | chart.js, confetti |
| 大規模 npm パッケージ | ScalablyTyped（自動生成） | three.js, d3 |
| プロトタイプ | `js.Dynamic`（型定義なし） | 何でも |

### 15.2 手動 Facade

npm パッケージの API を Scala.js の型として定義する。

```scala
// facades/ChartJS.scala
package facades

import scala.scalajs.js
import scala.scalajs.js.annotation.*

@js.native
@JSImport("chart.js", "Chart")
class Chart(ctx: dom.html.Canvas, config: js.Dynamic) extends js.Object:
  def update(): Unit = js.native
  def destroy(): Unit = js.native
```

### 15.3 `use:` アクションでラップ

外部ライブラリを `use:` アクションにラップすると、テンプレート内で宣言的に使える。

```scala
// actions/Tippy.scala
package actions

import scala.scalajs.js
import scala.scalajs.js.annotation.*
import melt.runtime.Action

@js.native
@JSImport("tippy.js", JSImport.Default)
def tippy(el: dom.Element, options: js.Object): js.Dynamic = js.native

val tooltip = Action[String] { (el, content) =>
  val instance = tippy(el, js.Dynamic.literal(content = content))
  onCleanup(() => instance.destroy())
}
```

```html
<script lang="scala">
  import actions.tooltip
</script>

<button use:tooltip={"ツールチップ"}>Hover me</button>
```

### 15.4 ScalablyTyped（大規模ライブラリ向け）

TypeScript の `.d.ts` から Scala.js Facade を自動生成する sbt プラグイン。

```scala
// project/plugins.sbt
addSbtPlugin("org.scalablytyped.converter" % "sbt-converter" % "1.0.0-beta44")

// package.json に npm パッケージを追加するだけで型定義が自動生成
```

---

## 16. テスト

### 16.1 テストの3レベル

| レベル | 対象 | DOM 必要？ | ツール |
|---|---|---|---|
| ロジックテスト | Store, ヘルパー関数, Var/Signal | ❌ | MUnit |
| レンダリングテスト | コンポーネントの DOM 出力 | ✅（jsdom） | MUnit + melt-testing |
| E2E テスト | ブラウザ上のフル動作 | ✅（ブラウザ） | Playwright |

### 16.2 ロジックテスト（DOM 不要）

Store やバリデーションロジックは通常の Scala テストとして書ける。

```scala
class TodoStoreSpec extends munit.FunSuite:
  test("add todo") {
    val store = new TodoStore
    store.add("Buy milk")
    assertEquals(store.items.now().length, 1)
    assertEquals(store.items.now().head.text, "Buy milk")
  }

  test("remaining count") {
    val store = new TodoStore
    store.add("A")
    store.add("B")
    store.toggle(store.items.now().head.id)
    assertEquals(store.remaining.now(), 1)
  }
```

### 16.3 レンダリングテスト（melt-testing）

melt-testing パッケージが DOM マウント + クエリ API を提供する。

```scala
import melt.testing.*

class CounterSpec extends MeltSuite:
  test("renders initial count") {
    val component = mount(Counter.create(Counter.Props("Test", 5)))
    assertEquals(component.text("h1"), "Test")
    assertEquals(component.text("p"), "Count: 5")
  }

  test("increments on click") {
    val component = mount(Counter.create(Counter.Props("Test", 0)))
    component.click("button")
    assertEquals(component.text("p"), "Count: 1")
  }
```

```scala
// melt-testing API
class MeltSuite extends munit.FunSuite:
  def mount(element: dom.Element): MountedComponent

class MountedComponent:
  def text(selector: String): String
  def attr(selector: String, name: String): Option[String]
  def click(selector: String): Unit
  def input(selector: String, value: String): Unit
  def exists(selector: String): Boolean
  def findAll(selector: String): List[dom.Element]
  def unmount(): Unit
```

### 16.4 E2E テスト

```scala
class TodoAppSpec extends PlaywrightSuite:
  test("full workflow") {
    page.goto("http://localhost:5173")
    page.fill("input", "Buy milk")
    page.click("button:has-text('Add')")
    assert(page.textContent("ul li").contains("Buy milk"))
  }
```

---

## 17. アクセシビリティ（a11y）

### 17.1 方針

強制ではなく警告。meltc がコンパイル時に一般的な a11y 問題を検出して警告する（エラーではないのでビルドは止まらない）。

### 17.2 meltc の a11y 警告ルール

| ルール | 内容 |
|---|---|
| `a11y-img-alt` | `<img>` に `alt` が必要 |
| `a11y-label` | `<input>` に `<label>` か `aria-label` が必要 |
| `a11y-click-events` | `onclick` のある非インタラクティブ要素に `role` と `tabindex` が必要 |
| `a11y-heading-order` | 見出しレベルの飛ばし検出（h1 → h3） |
| `a11y-no-redundant-roles` | 冗長な `role` の検出 |
| `a11y-media` | `<video>` / `<audio>` にキャプションが必要 |

```html
<!-- meltc 警告: <img> に alt 属性がありません -->
<img src="photo.jpg" />

<!-- 警告なし -->
<img src="photo.jpg" alt="プロフィール写真" />

<!-- meltc 警告: onclick のある <div> に role と tabindex がありません -->
<div onclick={handleClick}>Click me</div>

<!-- 警告なし -->
<button onclick={handleClick}>Click me</button>
```

### 17.3 FormField の自動 a11y

melt-forms の FormField コンポーネントが自動的に適切な aria 属性を付与する。

```html
<!-- FormField が内部で生成する DOM -->
<div class="form-field">
  <label id="name-label" for="name-input">Name</label>
  <input
    id="name-input"
    aria-labelledby="name-label"
    aria-describedby="name-desc name-error"
    aria-invalid={hasError}
    aria-required={isRequired}
  />
  <span id="name-desc">説明テキスト</span>
  <span id="name-error" aria-live="assertive">エラーメッセージ</span>
</div>
```

---

## 18. パフォーマンス最適化

### 18.1 Melt の性能特性

Svelte と同じ直接 DOM 操作アーキテクチャにより、以下の性能特性を持つ：

- 仮想 DOM なし → diff/patch のオーバーヘッドゼロ
- コンパイル時に更新先が確定 → 最小限の DOM 操作
- ランタイムが小さい（~2KB）
- 不要なコードが生成されない

### 18.2 `batch` — Signal のバッチ更新

複数の Var を同時に変更した場合、Signal の再計算を1回にまとめる。

```scala
// batch なし: 2回再計算 + 2回 DOM 更新
count.set(1)
name.set("X")

// batch あり: 1回再計算 + 1回 DOM 更新
batch {
  count.set(1)
  name.set("X")
}
```

### 18.3 `memo` — 重い計算のメモ化

`Signal.map` と異なり、結果が同じなら下流に通知しない。

```scala
val expensiveResult = memo(items) { list =>
  list.filter(complexFilter).sortBy(complexSort).take(100)
}
```

### 18.4 `keyed` リストの差分更新

`items.keyed(_.id).map(f)` がキーベースの最小 DOM 操作を行う（設計済み）。

### 18.5 遅延初期化

`if` 式により、表示されないコンポーネントは DOM に存在しない。条件が true になった時点で初めて生成される。

### 18.6 開発時のデバッグツール

```scala
import melt.runtime.devtools.*

debugSignalGraph()    // Signal の依存グラフを出力
debugEffectCount()    // effect の実行回数をカウント
```

---

## 19. ツールチェイン

### 19.1 npmパッケージ構成

| パッケージ | 依存 | 用途 | npm |
|---|---|---|---|
| `meltc` | なし | コンパイラCLI | ✅ 空き確認済 |
| `vite-plugin-melt` | meltc | Viteプラグイン | ✅ 空き確認済 |
| `melt-runtime` | scala-js-dom のみ | Var, Signal, Bind, managed, asyncState | ✅ 空き確認済 |
| `melt-cats-effect` | melt-runtime + cats-effect | `MeltEffect[IO]`, Resource統合 | — |
| `melt-zio` | melt-runtime + zio | `MeltEffect[Task]` | — |

### 19.2 開発ワークフロー

```
Terminal 1: npm run dev          ← Vite開発サーバー
Terminal 2: sbt ~fastLinkJS      ← Scala.js継続コンパイル
              or
            scala-cli --watch    ← sbt不要の軽量版
```

`vite-plugin-melt` が `.melt` ファイルの変更を検知し、meltcで `.scala` に変換 → scalacが型チェック+コンパイル → Scala.jsがJS出力 → ViteがHMRでブラウザ更新。

### 19.3 エラーメッセージ

meltc自身は型エラーを出さない。生成された `.scala` ファイルに対するscalacのエラーが、元の `.melt` ファイルの行番号にマッピングされる（ソースマップ方式）。

---

## 20. melt-runtime

フレームワーク唯一のランタイム依存。約200行（extension + managed + asyncState 含む）。

### 20.1 コアプリミティブ

```scala
// 書き換え可能なリアクティブ変数
class Var[A]:
  def now(): A
  def set(v: A): Unit
  def update(f: A => A): Unit
  def signal: Signal[A]
  // Var から直接 map / flatMap（.signal 不要）
  def map[B](f: A => B): Signal[B]
  def flatMap[B](f: A => Signal[B]): Signal[B]

// 読み取り専用の派生値
class Signal[A]:
  def now(): A
  def map[B](f: A => B): Signal[B]
  def flatMap[B](f: A => Signal[B]): Signal[B]
```

### 20.2 演算子 extension

```scala
// 数値演算
extension (v: Var[Int])
  def +=(n: Int): Unit
  def -=(n: Int): Unit
  def *=(n: Int): Unit

extension (v: Var[Long])
  def +=(n: Long): Unit
  def -=(n: Long): Unit

extension (v: Var[Double])
  def +=(n: Double): Unit
  def -=(n: Double): Unit

// 文字列
extension (v: Var[String])
  def +=(s: String): Unit

// 真偽値
extension (v: Var[Boolean])
  def toggle(): Unit
```

### 20.3 コレクション extension

```scala
extension [A](v: Var[List[A]])
  def append(item: A): Unit
  def prepend(item: A): Unit
  def removeWhere(pred: A => Boolean): Unit
  def removeAt(index: Int): Unit
  def mapItems(f: A => A): Unit
  def updateWhere(pred: A => Boolean)(f: A => A): Unit
  def clear(): Unit
  def sortBy[B: Ordering](f: A => B): Unit

// キー付きリスト（効率的な差分更新）
extension [A](v: Var[Seq[A]] | Signal[Seq[A]])
  def keyed[K](key: A => K): KeyedList[A, K]

class KeyedList[A, K](source: Signal[Seq[A]], key: A => K):
  def map(render: A => dom.Element): KeyedBinding
  // meltc がテンプレート内の keyed(...).map(...) を
  // Bind.each(source, key, render, anchor) に変換
```

### 20.4 DOMバインディング

```scala
object Bind:
  // テキスト表示
  def text(v: Var[?], parent: dom.Node): dom.Text
  def text(signal: Signal[?], parent: dom.Node): dom.Text
  def attr(el: dom.Element, name: String, signal: Signal[?]): Unit

  // Option / Boolean 属性の自動処理
  def optionalAttr[A](el: dom.Element, name: String, signal: Signal[Option[A]]): Unit
  def booleanAttr(el: dom.Element, name: String, signal: Signal[Boolean]): Unit

  // bind:value（文字列）
  def inputValue(input: dom.html.Input, v: Var[String]): Unit
  // bind:value（数値 — 自動型変換）
  def inputInt(input: dom.html.Input, v: Var[Int]): Unit
  def inputDouble(input: dom.html.Input, v: Var[Double]): Unit
  // bind:checked
  def inputChecked(input: dom.html.Input, v: Var[Boolean]): Unit
  // bind:group（ラジオボタン — 排他選択）
  def radioGroup(input: dom.html.Input, v: Var[String], value: String): Unit
  // bind:group（複数チェックボックス — リスト追加/削除）
  def checkboxGroup(input: dom.html.Input, v: Var[List[String]], value: String): Unit

  // リスト描画
  def list[A](signal: Signal[Seq[A]], render: A => dom.Node, anchor: dom.Node): Unit
  def each[A, K](signal: Signal[Seq[A]], key: A => K, render: A => dom.Node, anchor: dom.Node): Unit
  def showIf(signal: Signal[Boolean], create: () => dom.Node, anchor: dom.Node): Unit

  // class: ディレクティブ
  def classToggle(el: dom.Element, className: String, signal: Signal[Boolean]): Unit

  // style: ディレクティブ
  def style(el: dom.Element, property: String, signal: Signal[String]): Unit
  def style(el: dom.Element, property: String, signal: Signal[Option[String]]): Unit

  // 生 HTML 挿入
  def html(el: dom.Element, content: Signal[String]): Unit

// イベントリスナー（meltc が onclick={f} から直接生成）
// el.addEventListener("click", (e: dom.MouseEvent) => f(e))
// meltc がイベント名から適切な型（MouseEvent, KeyboardEvent 等）を推論
```

### 20.5 ライフサイクル & リソース管理

```scala
// リソースの安全な管理（acquire + release をペアに）
def managed[A](acquire: => A, release: A => Unit): A

// リアクティブ副作用
def effect[A](s: Var[A] | Signal[A])(f: A => Unit): Unit
def effect[A, B](a: Var[A] | Signal[A], b: Var[B] | Signal[B])(f: (A, B) => Unit): Unit

// effect内クリーンアップ
def onCleanup(f: () => Unit): Unit

// 非同期実行（MeltEffect 型クラス経由）
def asyncRun[F[_]: MeltEffect, A](fa: F[A])(handler: Try[A] => Unit): () => Unit
```

### 20.6 非同期状態管理

```scala
object asyncState:
  // マウント時に1回 fetch
  def apply[F[_]: MeltEffect, A](fa: => F[A]): AsyncState[A]

  // Signal 変化で自動再 fetch + 前回キャンセル
  def derived[F[_]: MeltEffect, A, B](dep: Var[A] | Signal[A])(f: A => F[B]): AsyncState[B]

class AsyncState[A]:
  val loading: Signal[Boolean]
  val value: Signal[Option[A]]
  val error: Signal[Option[Throwable]]
```

### 20.7 エフェクト型クラス

```scala
// エフェクトシステムの抽象化
trait MeltEffect[F[_]]:
  def runAsync[A](fa: F[A])(
    onSuccess: A => Unit,
    onError: Throwable => Unit
  ): () => Unit   // キャンセル関数

  def delay[A](a: => A): F[A]

// デフォルト ExecutionContext（Scala.js の JS イベントループ）
// ユーザーが自分の given ExecutionContext を定義すればそちらが優先される
given meltDefaultEC: ExecutionContext = ExecutionContext.global

// デフォルト: Future（追加依存なし、EC は given から取得）
given MeltEffect[Future] with
  def runAsync[A](fa: Future[A])(onSuccess: A => Unit, onError: Throwable => Unit) =
    fa.onComplete {
      case Success(a) => onSuccess(a)
      case Failure(e) => onError(e)
    }
    () => ()   // Future はキャンセル不可
  def delay[A](a: => A): Future[A] = Future(a)

// 別パッケージで提供（EC 不要）:
// melt-cats-effect → given MeltEffect[IO]
// melt-zio         → given MeltEffect[Task]
```

### 20.8 Context

```scala
object Context:
  // デフォルト値あり（inject() は常に値を返す）
  def create[A](default: A): Context[A]
  
  // デフォルト値なし（inject() は Option[A] を返す）
  def createOptional[A]: OptionalContext[A]

class Context[A]:
  def provide(value: A): Unit       // 子孫コンポーネントに値を公開
  def inject(): A                   // 最寄りの祖先から値を取得

class OptionalContext[A]:
  def provide(value: A): Unit
  def inject(): Option[A]           // 未提供なら None
```

### 20.9 HtmlProps / HtmlAttrs

```scala
// HTML 属性のコンテナ
case class HtmlAttrs(entries: Map[String, String] = Map.empty):
  def apply(el: dom.html.Element): Unit =
    entries.foreach { (key, value) =>
      if value == "" then el.setAttribute(key, "")  // disabled 等の Boolean 属性
      else el.setAttribute(key, value)
    }

object HtmlAttrs:
  val empty = HtmlAttrs()
  def apply(pairs: (String, String)*): HtmlAttrs = HtmlAttrs(pairs.toMap)

// ── ベース: 全 HTML 要素共通 ──
trait HtmlProps:
  private var _html: HtmlAttrs = HtmlAttrs.empty
  def html: HtmlAttrs = _html
  def withHtml(attrs: HtmlAttrs): this.type =
    _html = attrs
    this
// 許可属性: id, class, style, title, hidden, tabIndex, aria-*, data-*, role, ...

// ── 要素別の専用 trait ──
trait ButtonHtmlProps extends HtmlProps
// + disabled, type, form, formAction, formMethod, formTarget, name, value, ...

trait InputHtmlProps extends HtmlProps
// + type, placeholder, required, min, max, pattern, step, minLength, maxLength, ...

trait AnchorHtmlProps extends HtmlProps
// + href, target, rel, download, hreflang, ...

trait FormHtmlProps extends HtmlProps
// + action, method, enctype, novalidate, autocomplete, ...

trait ImgHtmlProps extends HtmlProps
// + src, alt, width, height, loading, decoding, crossorigin, ...

trait SelectHtmlProps extends HtmlProps
// + disabled, required, multiple, size, name, form, ...

trait TextAreaHtmlProps extends HtmlProps
// + placeholder, required, rows, cols, minLength, maxLength, wrap, ...
```

### 20.10 その他

```scala
// CSS注入
object Style:
  def inject(scopeId: String, css: String): Unit

// マウント
object Mount:
  def apply(target: dom.Element, component: () => dom.Element): Unit
```

### 20.11 Ref（DOM 要素参照）

```scala
class Ref[A <: dom.Element]:
  def get: Option[A]
  def set(el: A): Unit
  def foreach(f: A => Unit): Unit

object Ref:
  def empty[A <: dom.Element]: Ref[A]
```

### 20.12 Action（要素アクション）

```scala
object Action:
  def apply[P](f: (dom.Element, P) => Unit): Action[P]
  def apply(f: dom.Element => Unit): Action[Unit]

// 組み込みアクション（melt.runtime.actions パッケージ）
val autoFocus: Action[Unit]
val clickOutside: Action[() => Unit]
val trapFocus: Action[Unit]
val portal: Action[String]        // セレクタ指定
val longPress: Action[() => Unit]
```

### 20.13 Transition / Animation

```scala
trait Transition:
  def apply(node: dom.Element, params: TransitionParams, direction: Direction): TransitionConfig

enum Direction:
  case In, Out, Both

case class TransitionConfig(
  delay: Int = 0,
  duration: Int = 300,
  easing: Double => Double = identity,
  css: Option[(Double, Double) => String] = None,
  tick: Option[(Double, Double) => Unit] = None
)

// 組み込みトランジション
object fade extends Transition
object fly extends Transition
object slide extends Transition
object scale extends Transition
object blur extends Transition
object draw extends Transition    // SVG 用

// 組み込みアニメーション
object flip extends Animation
```

### 20.14 Window / Document API

```scala
object Window:
  def on(event: String)(handler: dom.Event => Unit): Unit
  val scrollX: Signal[Double]
  val scrollY: Signal[Double]
  val innerWidth: Signal[Double]
  val innerHeight: Signal[Double]
  val online: Signal[Boolean]

object Document:
  def title(t: Signal[String]): Unit
  def on(event: String)(handler: dom.Event => Unit): Unit

// 生 HTML
def html(content: Signal[String]): RawHtml
```

### 20.15 Boundary（エラーバウンダリ）

```scala
object Boundary:
  case class Props(
    children: () => Element,
    fallback: (Throwable, () => Unit) => Element,
    onError: Throwable => Unit = _ => ()
  )
```

### 20.16 untrack / tick

```scala
// 依存追跡の除外
def untrack[A](v: Var[A]): A
def untrack[A](s: Signal[A]): A
def untrack[A](f: => A): A

// DOM 更新の待機
def tick(f: => Unit): Unit
def tickAsync(): Future[Unit]
```

### 20.17 batch / memo

```scala
// バッチ更新（複数の Var 変更を1回の Signal 再計算にまとめる）
def batch(f: => Unit): Unit

// メモ化（結果が同じなら下流に通知しない）
def memo[A, B](dep: Signal[A])(f: A => B): Signal[B]
```

### 20.18 開発ツール

```scala
// 開発ビルドでのみ有効。プロダクションビルドではノーオプ
object devtools:
  def debugSignalGraph(): Unit     // Signal の依存グラフをコンソールに出力
  def debugEffectCount(): Unit     // effect の実行回数をカウント
```

---

## Appendix A: 生成コードの例

### 入力: `Counter.melt`

```html
<script lang="scala" props="Props">
  case class Props(label: String, count: Int = 0)

  val internal = Var(props.count)
  val doubled = internal.map(_ * 2)
  def increment(): Unit = internal += 1

  def badge(text: String): Html = {
    <span class="badge">{text}</span>
  }
</script>

<div class="counter">
  <h1>{props.label}</h1>
  {badge(internal.now().toString)}
  <button onclick={increment}>+1</button>
  <p>Doubled: {doubled}</p>
</div>

<style>
.counter { text-align: center; }
</style>
```

### 出力: `Counter.scala`（meltcが生成）

```scala
package generated

import org.scalajs.dom
import melt.runtime.{Var, Signal, Bind, Style}
import melt.runtime.extensions.given

object Counter:
  case class Props(label: String, count: Int = 0)

  private val _scopeId = "melt-a1b2c3"
  private val _css = """.melt-a1b2c3 .counter { text-align: center; }"""

  def create(props: Props): dom.Element =
    Style.inject(_scopeId, _css)

    // — state & handlers —
    val internal = Var(props.count)
    val doubled = internal.map(_ * 2)
    def increment(): Unit = internal += 1

    // — parts —
    def badge(text: String): dom.Element =
      val _span0 = dom.document.createElement("span").asInstanceOf[dom.html.Element]
      _span0.classList.add(_scopeId)
      _span0.classList.add("badge")
      _span0.appendChild(dom.document.createTextNode(text))
      _span0

    // — DOM construction —
    val _div1 = dom.document.createElement("div").asInstanceOf[dom.html.Element]
    _div1.classList.add(_scopeId)
    _div1.classList.add("counter")

    val _h12 = dom.document.createElement("h1").asInstanceOf[dom.html.Element]
    _h12.classList.add(_scopeId)
    Bind.text(props.label, _h12)
    _div1.appendChild(_h12)

    _div1.appendChild(badge(internal.now().toString))

    val _button3 = dom.document.createElement("button").asInstanceOf[dom.html.Element]
    _button3.classList.add(_scopeId)
    _button3.addEventListener("click", (_: dom.Event) => increment())
    _button3.appendChild(dom.document.createTextNode("+1"))
    _div1.appendChild(_button3)

    val _p4 = dom.document.createElement("p").asInstanceOf[dom.html.Element]
    _p4.classList.add(_scopeId)
    _p4.appendChild(dom.document.createTextNode("Doubled: "))
    Bind.text(doubled, _p4)
    _div1.appendChild(_p4)

    _div1
  end create

  def mount(target: dom.Element, props: Props): Unit =
    target.appendChild(create(props))

end Counter
```

---

## Appendix B: 実装上の注意点

Scala / Scala.js での実現可能性を調査した結果、実現不可能なものはゼロだが、以下の項目は実装時に注意が必要。

### B.1 テンプレートパーサーの複雑さ

meltc は HTML + Scala 式が混在するテンプレートをパースする必要がある。`{}` 内の Scala 式に入れ子の `{}`、文字列リテラル内の `{}`、HTML タグが含まれるため、正確にパースするにはブレースのネスト深さカウント + 文字列リテラル検出が必要。

完全な Scala パーサーは不要。Svelte も JavaScript の完全なパーサーは内蔵せず、同様のヒューリスティックで動作している。

### B.2 `onCleanup` のスコープ管理

`onCleanup` は `effect` や `Action` ブロック内で呼ばれるが、どのコンポーネントのライフサイクルに紐づくかを知る必要がある。

対策: グローバル変数（Scala.js はシングルスレッド）にクリーンアップリストを保持し、コンポーネント初期化時にセット、完了後に回収する。Svelte も同じ方式。

```scala
// 内部実装のイメージ
private var currentCleanups: List[() => Unit] = Nil

def onCleanup(f: () => Unit): Unit =
  currentCleanups = f :: currentCleanups
```

### B.3 `tick` / `queueMicrotask`

scala-js-dom に `queueMicrotask` が未定義の場合、以下のフォールバックを使う：

```scala
def tick(f: => Unit): Unit =
  js.Promise.resolve(()).`then`(_ => f)
```

### B.4 Web Animations API

`transition:` ディレクティブが Web Animations API（`element.animate()`）を使う場合、scala-js-dom にメソッドが存在しない可能性がある。

対策:
- scala-js-dom が提供している → そのまま使う
- 提供していない → Facade を1つ書く（`@js.native def animate(...): js.Dynamic = js.native`）
- CSS keyframes 方式で代替する（Web Animations API 不要）

### B.5 HtmlProps の属性バリデーション

meltc が「この属性は ButtonHtmlProps で許可されているか」を判定する方式には2つの選択肢がある：

- **方式A:** meltc が属性リストをハードコードする → 単純だが HtmlProps 追加時に meltc も更新必要
- **方式B:** meltc は属性チェックをせず、全て `HtmlAttrs` に入れて scalac に型チェックを委ねる → meltc の「型チェックしない」方針と一致

方式B を推奨。エラーメッセージの分かりやすさは scalac のエラーメッセージ改善で対処する。

### B.6 コンポーネント vs HTML 要素の判別

meltc はテンプレート内のタグがコンポーネント（`<Button>`）か HTML 要素（`<button>`）かを判別する必要がある。

ルール: **大文字始まりはコンポーネント、小文字始まりは HTML 要素。** React / Svelte と同じ規則。

### B.7 `Boundary` での JS エラーキャッチ

Scala.js で `try/catch` を使う場合、JavaScript の Error オブジェクトは `scala.scalajs.js.JavaScriptException` でラップされる。`Boundary` はこの両方をキャッチする必要がある。

```scala
try userCode()
catch
  case e: Exception => handleError(e)
  case js.JavaScriptException(e) => handleError(new RuntimeException(e.toString))
```
