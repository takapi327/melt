# Meltc 実装フェーズ

> 設計ドキュメントに基づく段階的実装計画
>
> Status: Draft
> Last Updated: 2026-04-05

---

## 概要

各フェーズは前のフェーズの成果物に依存する。フェーズごとに動作するデモ/テストが存在し、進捗が確認可能。

```
Phase 0  モノレポスケルトン
Phase 1  melt-runtime コア（Var / Signal）
Phase 2  meltc パーサー
Phase 3  meltc コード生成 + sbt プラグイン          → Hello World がブラウザで表示
Phase 4  リアクティブバインディング                  → Counter アプリが動作
Phase 5  コンポーネントシステム + CSS                → マルチコンポーネント Todo アプリ
Phase 6  テンプレート完全対応                        → 制御構文 + ディレクティブ
Phase 7  ライフサイクル & 状態管理                   → 非同期データ取得アプリ
Phase 8  高度な機能                                  → プロダクション品質のコンポーネント作成可能
Phase 9  トランジション & アニメーション
Phase 10 テストパッケージ
Phase 11 IDE サポート
Phase 12 フォームライブラリ（melt-forms）
Phase 13 ドキュメント & リリース
```

---

## Phase 0: モノレポスケルトン

**ゴール:** 全モジュールの空プロジェクトが `sbt compile` で通る。CI が動く。

### 成果物

```
melt/
├── build.sbt                          ← 全サブプロジェクト定義
├── project/
│   ├── build.properties               ← sbt.version=1.10.x
│   └── plugins.sbt                    ← ScalaJS, crossProject, scalafmt, scalafix
├── modules/
│   ├── meltc/
│   │   ├── shared/src/main/scala/melt/compiler/
│   │   │   └── MeltCompiler.scala     ← object MeltCompiler（空実装）
│   │   ├── jvm/src/
│   │   ├── js/src/
│   │   └── native/src/
│   ├── sbt-meltc/
│   │   └── src/main/scala/meltc/sbt/
│   │       └── MeltcPlugin.scala      ← object MeltcPlugin（空実装）
│   ├── runtime/
│   │   └── src/
│   │       ├── main/scala/melt/runtime/
│   │       │   └── Var.scala          ← class Var（空実装）
│   │       └── test/scala/melt/runtime/
│   │           └── VarSpec.scala      ← 空テスト
│   └── melt-testing/
│       └── src/main/scala/melt/testing/
│           └── MeltSuite.scala        ← 空実装
├── editors/
│   └── language-server/
│       └── src/main/scala/
│           └── MeltLanguageServer.scala ← 空実装
├── examples/
│   └── hello-world/
│       ├── build.sbt
│       └── src/main/components/
│           └── App.melt               ← 最小限の .melt ファイル
├── .github/
│   └── workflows/
│       └── ci.yml                     ← sbt test + scalafmt check
├── .scalafmt.conf
├── .scalafix.conf
├── LICENSE                            ← Apache 2.0 or MIT
└── README.md
```

### タスク

- [ ] `build.sbt` に全サブプロジェクトを定義（meltc crossProject, sbt-meltc, runtime, melt-testing, language-server）
- [ ] `project/plugins.sbt` にプラグイン追加
- [ ] 各モジュールに空の Scala ファイルを配置
- [ ] `sbt compile` が全モジュールで成功することを確認
- [ ] `sbt test` が通ることを確認（空テスト）
- [ ] GitHub Actions CI を設定（Java 17/21 マトリクス）
- [ ] `.scalafmt.conf` / `.scalafix.conf` を設定
- [ ] README.md にプロジェクト概要を記載

### 完了条件

- `sbt compile` が全モジュールで成功
- CI が green

---

## Phase 1: melt-runtime コア（Var / Signal）

**ゴール:** リアクティビティの基盤が動作し、ユニットテストが通る。

**対応するデザインドキュメント:** §5 リアクティビティ, §20.1-20.3

### タスク

- [ ] `Var[A]` 実装 — `now()`, `set()`, `update()`, `subscribe()`
- [ ] `Signal[A]` 実装 — `now()`, `map()`, `flatMap()`, `subscribe()`
- [ ] `Var` から `Signal` への暗黙変換（`Var.map` → `Signal`）
- [ ] `for` 式のサポート（`map` + `flatMap`）
- [ ] 演算子 extension — `+=`, `-=`, `*=`, `toggle()` （§20.2）
- [ ] コレクション extension — `append`, `prepend`, `removeWhere` 等（§20.3）
- [ ] 全 API のユニットテスト

> **⚠️ 注意 — `map` / `flatMap` で追加したサブスクライバーの解除**
>
> `map` / `flatMap` は内部でサブスクライバーを登録するが、外側からそのサブスクリプションを解除する手段がない。
> Phase 4 (DOM バインディング) でコンポーネント破棄時のクリーンアップが必要になる際、
> 派生 Signal が保持するサブスクライバーが残り続けてメモリリークが発生しうる。
> Phase 4 実装時に `onCleanup` / スコープ付きエフェクト等と組み合わせた回収設計が必要。

### 完了条件

```scala
// このコードがテストで動作する
val count = Var(0)
val doubled = count.map(_ * 2)
assert(doubled.now() == 0)
count += 1
assert(doubled.now() == 2)

val name = Var("Alice")
val greeting = for
  n <- name
  d <- doubled
yield s"$n: $d"
assert(greeting.now() == "Alice: 2")
```

---

## Phase 2: meltc パーサー

**ゴール:** `.melt` ファイルを AST に分解できる。

**対応するデザインドキュメント:** §2 コンパイルパイプライン, §3 ファイル構造, Appendix B.1

### タスク

- [ ] `.melt` ファイルの3セクション分割（`<script lang="scala">`, テンプレート, `<style>`）
- [ ] `<script>` タグの判別（`lang="scala"` あり → Scala セクション / なし → HTML）
- [ ] `props="Props"` 属性のパース
- [ ] テンプレート内の `{expression}` 検出（ブレースネスト対応、文字列リテラル内の `{}` 除外）
- [ ] HTML タグのパース（要素名、属性、子要素）
- [ ] ディレクティブ属性の検出（`bind:value`, `class:name`, `onclick` 等）
- [ ] AST データ構造の定義
- [ ] パーサーのユニットテスト（正常系 + エッジケース）

### AST 構造（概念）

```scala
case class MeltFile(
  script: Option[ScriptSection],
  template: List[TemplateNode],
  style: Option[StyleSection]
)

case class ScriptSection(
  code: String,
  propsType: Option[String]  // props="Props" の値
)

enum TemplateNode:
  case Element(tag: String, attrs: List[Attr], children: List[TemplateNode])
  case Text(content: String)
  case Expression(code: String)       // {count}
  case Component(name: String, attrs: List[Attr], children: List[TemplateNode])

enum Attr:
  case Static(name: String, value: String)            // class="foo"
  case Dynamic(name: String, expr: String)             // class={expr}
  case Directive(kind: String, name: String, expr: Option[String])  // bind:value={name}
  case EventHandler(event: String, expr: String)       // onclick={handler}
```

### 完了条件

- Counter.melt（Appendix A の入力）を正しく AST に変換できる
- テンプレート内のネストした `{}` を正しく処理できる

---

## Phase 3: meltc コード生成 + sbt プラグイン

**ゴール:** 静的な "Hello World" がブラウザに表示される。

**対応するデザインドキュメント:** §2, §19, Appendix A, プロジェクト構成 §3.1-3.2

### タスク

- [ ] AST → Scala コード生成（静的 HTML のみ）
  - `Element` → `dom.document.createElement` + `appendChild`
  - `Text` → `dom.document.createTextNode`
  - 静的属性 → `setAttribute`
  - スコープ ID 生成 + `classList.add`
- [ ] `object ComponentName { def create(props: Props): dom.Element }` の構造を生成
- [ ] `Mount.apply` の実装（melt-runtime）
- [ ] `Style.inject` の実装（melt-runtime）
- [ ] sbt-meltc プラグイン実装（§3.2 の設計通り）
  - `.melt` ファイル検出
  - `meltcGenerate` タスク
  - `sourceGenerators` 連携
- [ ] examples/hello-world でブラウザ表示確認

### 完了条件

```html
<!-- App.melt -->
<script lang="scala">
</script>

<div>
  <h1>Hello, Melt!</h1>
  <p>Static content works.</p>
</div>

<style>
h1 { color: #ff3e00; }
</style>
```

↓ `sbt fastLinkJS` → ブラウザで "Hello, Melt!" が表示される

---

## Phase 4: リアクティブバインディング

**ゴール:** インタラクティブな Counter アプリが動作する。

**対応するデザインドキュメント:** §5, §8, §9.1, §20.4

### タスク

- [ ] `Bind.text` 実装 — Signal/Var のテキストバインディング
- [ ] `Bind.attr` 実装 — リアクティブ属性バインディング
- [ ] コード生成: `{expression}` → `Bind.text(expr, parent)`
- [ ] コード生成: `onclick={handler}` → `addEventListener("click", handler)`
- [ ] イベント型の自動推論（要素名 + イベント名 → `dom.MouseEvent` 等）
- [ ] `bind:value` 実装（`Bind.inputValue` — 文字列のみ）
- [ ] コード生成: `bind:value={var}` → `Bind.inputValue(input, var)`
- [ ] `Option` / `Boolean` の属性自動処理（`Bind.optionalAttr`, `Bind.booleanAttr`）
- [ ] コンポーネント破棄時のサブスクリプション解除（`onCleanup` / スコープ付きエフェクト）

> **⚠️ 注意 — Phase 1 で積み残したサブスクリプション解除問題**
>
> `map` / `flatMap` で追加したサブスクライバーは外側から解除する手段がない。
> コンポーネント破棄時にこれらのサブスクライバーが残り続けるとメモリリークになる。
> `onCleanup` をスコープ付きで管理する仕組み（グローバルクリーンアップリスト方式、§21.3 参照）を
> この Phase で必ず実装し、`map` / `flatMap` 派生 Signal もその対象に含めること。

### 完了条件

```html
<!-- Counter.melt -->
<script lang="scala">
  val count = Var(0)
  val name = Var("")
</script>

<div>
  <p>Count: {count}</p>
  <button onclick={_ => count += 1}>+1</button>
  <input bind:value={name} placeholder="Your name" />
  <p>Hello, {name}!</p>
</div>
```

↓ ブラウザで Counter が動作し、入力がリアルタイム反映される

---

## Phase 5: コンポーネントシステム + CSS

**ゴール:** 複数コンポーネントで構成される Todo アプリが動作する。

**対応するデザインドキュメント:** §4, §10, §12

### タスク

- [ ] Props のコード生成（case class 定義 → `object Component { case class Props(...) }`)
- [ ] コンポーネント参照のコード生成（大文字タグ → `Component.create(props)`）
- [ ] Props の渡し方（静的属性 + 動的属性 → Props コンストラクタ引数）
- [ ] `children` の処理（タグ内テンプレート → `() => Element` 関数）
- [ ] パーツ（Html 関数）のコード生成（`def f(): Html` → `def f(): dom.Element`）
- [ ] スコープ付き CSS のコード生成（セレクタにスコープ ID 付与）
- [ ] `styled` 属性のコード生成（親スコープ ID を子ルート要素に追加）
- [ ] CSS カスタムプロパティの透過（スコープの影響を受けないことを確認）

### 完了条件

```
examples/todo-app/
├── src/main/components/
│   ├── App.melt          ← TodoInput + TodoList を使う
│   ├── TodoInput.melt    ← Props(onAdd: String => Unit)
│   └── TodoList.melt     ← Props(items: Var[List[Todo]])
```

↓ マルチコンポーネントの Todo アプリがブラウザで動作する

---

## Phase 6: テンプレート完全対応

**ゴール:** 全てのテンプレート制御構文とディレクティブが動作する。

**対応するデザインドキュメント:** §9.2-9.5, §11, §13.1-13.2, §13.6

### タスク

- [ ] テンプレート内 `if/else` の処理（`Bind.showIf` or 条件分岐コード生成）
- [ ] テンプレート内 `match` の処理
- [ ] リスト描画（`Bind.list`）— `items.map(f)` のコード生成
- [ ] キー付きリスト（`Bind.each`）— `items.keyed(_.id).map(f)` の差分更新
- [ ] `keyed` extension の実装（melt-runtime）
- [ ] `bind:checked` 実装（`Bind.inputChecked`）
- [ ] `bind:group` 実装（`Bind.radioGroup`, `Bind.checkboxGroup`）
- [ ] 数値 `bind:value` の自動型変換（`Bind.inputInt`, `Bind.inputDouble`）
- [ ] `bind:this` のコード生成（`Ref` クラス + `ref.set(element)`）
- [ ] `Ref` クラスの実装（melt-runtime）
- [ ] `class:name={expr}` のコード生成（`Bind.classToggle`）
- [ ] `Bind.classToggle` の実装（melt-runtime）
- [ ] `style:property={expr}` のコード生成（`Bind.style`）
- [ ] `Bind.style` の実装（melt-runtime）

### 完了条件

- `if/else`, `match`, `.map`, `.keyed` がテンプレート内で動作する
- 全 bind ディレクティブ + `class:` + `style:` が動作する
- フォーム要素（テキスト入力、チェックボックス、ラジオボタン、セレクト）が完全に動作する

---

## Phase 7: ライフサイクル & 状態管理

**ゴール:** 非同期データ取得、リソース管理、状態共有が動作する。

**対応するデザインドキュメント:** §6, §7, §14.5-14.6, §18.2-18.3, §20.5-20.8, §20.16-20.17

### タスク

- [ ] `managed` 実装（acquire + release ペア、コンポーネント破棄時に release 実行）
- [ ] `onCleanup` 実装（グローバル変数方式 — Appendix B.2）
- [ ] `effect` 実装（明示的依存指定 + subscribe）
- [ ] `asyncState` 実装（loading / value / error の管理）
- [ ] `asyncState.derived` 実装（Signal 連動再 fetch + 前回キャンセル）
- [ ] `MeltEffect[Future]` 実装（デフォルト）
- [ ] `Context` 実装（`Context.create` / `provide` / `inject`）
- [ ] `batch` 実装（Signal のバッチ更新）
- [ ] `memo` 実装（参照等価性チェック付きメモ化）
- [ ] `untrack` 実装
- [ ] `tick` 実装（`js.Promise.resolve` フォールバック — Appendix B.3）
- [ ] meltc: `effect` / `managed` を含むコード生成の動作確認

### 完了条件

```html
<!-- 非同期データ取得 + Context が動作する -->
<script lang="scala">
  val users = asyncState { fetch("/api/users").map(parseUsers) }
</script>

{users.value match
  case Some(list) => <ul>{list.map(u => <li>{u.name}</li>)}</ul>
  case None       => {if users.loading then <p>Loading...</p>
                      else <p>Error</p>}}
```

---

## Phase 8: 高度な機能

**ゴール:** プロダクション品質のコンポーネントが作成可能。

**対応するデザインドキュメント:** §4.1 HtmlProps, §13.3, §14.1-14.4, §17

### タスク

- [ ] `HtmlProps` 階層実装（`HtmlProps`, `ButtonHtmlProps`, `InputHtmlProps` 等）
- [ ] `HtmlAttrs` 実装 + `{...props.html}` のコード生成
- [ ] `use:` アクションのコード生成（関数呼び出し + cleanup 登録）
- [ ] `Action` ヘルパー実装（melt-runtime）
- [ ] 組み込みアクション実装（`autoFocus`, `clickOutside`, `trapFocus`）
- [ ] リアクティブ引数の `use:` 対応（effect + onCleanup パターン生成）
- [ ] `Boundary` コンポーネント実装（`try/catch` + DOM 差し替え — Appendix B.7）
- [ ] `Window` API 実装（`on`, `scrollY`, `innerWidth` 等）
- [ ] `Document` API 実装（`title`, `on`）
- [ ] `html()` 実装（`Bind.html` + XSS 警告）
- [ ] meltc: a11y 警告の実装（`img alt` 漏れ、`label` 漏れ等）
- [ ] meltc: エラーメッセージのソースマップ（`.melt` の行番号に戻す）

### 完了条件

- `HtmlProps` で型安全な属性透過が動作する
- `use:clickOutside` 等の組み込みアクションが動作する
- `Boundary` でエラーキャッチとフォールバック表示が動作する
- a11y 警告がコンパイル時に出力される

---

## Phase 9: トランジション & アニメーション

**ゴール:** 要素の出入り + リスト並べ替えにアニメーションが付く。

**対応するデザインドキュメント:** §13.4-13.5, §20.13, Appendix B.4

### タスク

- [ ] `Transition` trait + `TransitionConfig` 実装
- [ ] 組み込みトランジション実装（`fade`, `fly`, `slide`, `scale`）
- [ ] `transition:` のコード生成（要素追加/削除時のアニメーション実行）
- [ ] `in:` / `out:` のコード生成（入り/出り別アニメーション）
- [ ] Web Animations API Facade（必要に応じて — Appendix B.4）
- [ ] `animate:flip` 実装（keyed リストの並べ替えアニメーション）
- [ ] `animate:` のコード生成
- [ ] カスタムトランジション関数のサポート

### 完了条件

```html
{if visible then
  <div transition:fade={{ duration = 300 }}>Fades in and out</div>
else <span></span>}
```

↓ 要素が滑らかにフェードイン/アウトする

---

## Phase 10: テストパッケージ

**ゴール:** ユーザーが自分のコンポーネントをテストできる。

**対応するデザインドキュメント:** §16, §20.18

### タスク

- [ ] `MeltSuite` ベースクラス実装（DOM 環境セットアップ + クリーンアップ）
- [ ] `MountedComponent` 実装（`text`, `attr`, `click`, `input`, `exists`, `findAll`, `unmount`）
- [ ] `mount()` ヘルパー実装
- [ ] devtools 実装（`debugSignalGraph`, `debugEffectCount`）
- [ ] example プロジェクトのテストを melt-testing で記述
- [ ] テストの実行方法をドキュメント化

### 完了条件

```scala
class CounterSpec extends MeltSuite:
  test("increments on click") {
    val c = mount(Counter.create(Counter.Props("Test", 0)))
    c.click("button")
    assertEquals(c.text("p"), "Count: 1")
  }
```

↓ `sbt test` で上記テストが通る

---

## Phase 11: IDE サポート

**ゴール:** VS Code / Neovim でシンタックスハイライト + 基本的な補完が動作する。

**対応するデザインドキュメント:** プロジェクト構成 §7

### タスク

- [ ] TextMate grammar 作成（VS Code 用 — `.melt` ファイルのシンタックスハイライト）
- [ ] VS Code 拡張の雛形（TextMate grammar バンドル + LSP クライアント）
- [ ] Tree-sitter grammar 作成（Neovim 用）
- [ ] melt-language-server: セクション分割 + `<script>` 部分を Metals に委譲
- [ ] melt-language-server: `<style>` 部分を CSS LS に委譲
- [ ] VS Code 拡張から LSP 接続

### 完了条件

- VS Code で `.melt` ファイルが `<script>`, テンプレート, `<style>` の3色でハイライトされる
- `<script>` 内で Scala の補完が動作する（Metals 経由）

---

## Phase 12: フォームライブラリ（melt-forms）

**ゴール:** Iron ベースの型安全なフォームライブラリが動作する。

**対応するデザインドキュメント:** meltc-forms-design.md 全体

### タスク

- [ ] `melt-forms` モジュール追加（`build.sbt`、Iron 依存）
- [ ] `FormField[A, C]` 実装（value, error, touched, dirty, showError）
- [ ] `Form.create[S]` のマクロ実装（`Mirror.ProductOf` で case class フィールド列挙）
- [ ] Iron 制約からエラーメッセージ自動抽出
- [ ] `form.field("name")` のコンパイル時フィールド名検証（マクロ）
- [ ] `ValidateOn` 実装（Change / Blur / Submit）
- [ ] `form.submit` 実装（全フィールドバリデーション + ハンドラ呼び出し）
- [ ] 非同期バリデーション対応
- [ ] TextField / NumberField 等のサンプルコンポーネント
- [ ] ユニットテスト

### 完了条件

```scala
case class UserForm(
  name: String :| (Not[Blank] & MinLength[2]),
  email: String :| Email,
)
val form = Form.create[UserForm]
// → form.field("name").value, form.field("name").error が動作
// → form.submit(data => ...) でバリデーション済みデータが渡される
```

---

## Phase 13: ドキュメント & リリース

**ゴール:** 公開リリース。ユーザーが Melt を使い始められる。

### タスク

- [ ] ドキュメントサイト構築（mdoc or Docusaurus）
- [ ] Getting Started ガイド
- [ ] API リファレンス（melt-runtime, melt-forms）
- [ ] チュートリアル（Counter → Todo → Dashboard の段階的ガイド）
- [ ] example プロジェクト充実（hello-world, counter, todo, dashboard, form）
- [ ] Giter8 テンプレート（`sbt new io.github.takapi327/melt-vite.g8`）
- [ ] Maven Central publish 設定（sbt-ci-release）
- [ ] npm publish 設定（将来の CLI 用）
- [ ] CHANGELOG 作成
- [ ] v0.1.0 リリース

### 完了条件

```bash
# ユーザーがこれだけで始められる
sbt new io.github.takapi327/melt-vite.g8
cd my-app
sbt ~fastLinkJS   # Terminal 1
npm run dev        # Terminal 2
# → ブラウザで Melt アプリが表示される
```

---

## フェーズ間の依存関係

```
Phase 0 ──→ Phase 1 ──→ Phase 2 ──→ Phase 3 ──→ Phase 4 ──→ Phase 5 ──→ Phase 6
                                                                              │
             Phase 7 ←──────────────────────────────────────────────────────┘
               │
               ├──→ Phase 8
               │
               ├──→ Phase 9
               │
               └──→ Phase 10

             Phase 11（IDE）は Phase 3 以降いつでも開始可能
             Phase 12（Forms）は Phase 6 以降いつでも開始可能
             Phase 13 は全フェーズ完了後
```

---

## 工数の概算

| Phase | 内容 | 概算工数 |
|---|---|---|
| 0 | スケルトン | 1-2日 |
| 1 | melt-runtime コア | 1-2週間 |
| 2 | meltc パーサー | 2-3週間 |
| 3 | コード生成 + sbt プラグイン | 2-3週間 |
| 4 | リアクティブバインディング | 1-2週間 |
| 5 | コンポーネントシステム + CSS | 2-3週間 |
| 6 | テンプレート完全対応 | 2-3週間 |
| 7 | ライフサイクル & 状態管理 | 2-3週間 |
| 8 | 高度な機能 | 3-4週間 |
| 9 | トランジション | 2-3週間 |
| 10 | テストパッケージ | 1-2週間 |
| 11 | IDE サポート | 4-8週間 |
| 12 | フォームライブラリ | 2-4週間 |
| 13 | ドキュメント & リリース | 2-4週間 |
| | **合計** | **約 6-10ヶ月**（1人の場合） |

---

## マイルストーン

| マイルストーン | Phase | 意味 |
|---|---|---|
| **M1: First Render** | Phase 3 完了 | 静的 HTML がブラウザに表示される |
| **M2: Interactive** | Phase 4 完了 | ユーザー操作に反応するアプリが動作する |
| **M3: Real App** | Phase 6 完了 | 実用的なアプリが構築可能 |
| **M4: Production Ready** | Phase 8 完了 | プロダクション品質のコンポーネント作成可能 |
| **M5: Developer Ready** | Phase 10 完了 | テスト・デバッグ環境が整う |
| **M6: Public Release** | Phase 13 完了 | v0.1.0 公開 |
