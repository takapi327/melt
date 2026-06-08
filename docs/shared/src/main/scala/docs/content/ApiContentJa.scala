/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package docs.content

object ApiContentJa:

  def content(slug: String, base: String): String = slug match

    case "template-syntax" =>
      """
<h1 class="doc-title">テンプレート構文</h1>
<p class="doc-lead">Melt のテンプレートは HTML ファーストで、Scala 式を <code>{}</code> で埋め込みます。このページでは、コンパイラが理解するすべての構文を解説します。</p>

<h2>テキスト補間</h2>
<p>任意の Scala 式を <code>{}</code> で囲むと、エスケープされたテキストとして描画されます。</p>
<pre class="code-block"><code>&lt;p&gt;こんにちは、{name}さん！&lt;/p&gt;
&lt;span&gt;{count * 2}&lt;/span&gt;
&lt;div&gt;{if loggedIn then "ようこそ" else "ログインしてください"}&lt;/div&gt;</code></pre>
<div class="callout callout-tip"><strong>Tip:</strong> 補間された文字列はすべて自動的に HTML エスケープされます。生の HTML を描画するには <code>bind:innerHTML</code> を使用してください。</div>

<h2>属性バインディング</h2>
<p>値を <code>{}</code> で囲むと属性を動的に設定できます。</p>
<pre class="code-block"><code>&lt;a href={url}&gt;リンク&lt;/a&gt;
&lt;input type="text" value={name} placeholder={hint}/&gt;
&lt;img src={avatarUrl} alt={altText}/&gt;</code></pre>

<h2>Boolean 属性</h2>
<p><code>true</code>/<code>false</code> を渡して HTML の Boolean 属性を切り替えます。</p>
<pre class="code-block"><code>&lt;input disabled={!isEnabled}/&gt;
&lt;button hidden={!showButton}&gt;クリック&lt;/button&gt;</code></pre>

<h2>class: ディレクティブ</h2>
<p><code>class:name={条件}</code> で CSS クラスを条件付きで切り替えます。</p>
<pre class="code-block"><code>&lt;div class="card" class:active={isSelected}&gt;...&lt;/div&gt;
&lt;button class:loading={isPending} class:disabled={!canSubmit}&gt;送信&lt;/button&gt;</code></pre>

<h2>style: ディレクティブ</h2>
<p><code>style:property={値}</code> で CSS プロパティをリアクティブに設定します。</p>
<pre class="code-block"><code>&lt;div style:color={textColor} style:font-size="{fontSize}px"&gt;...&lt;/div&gt;</code></pre>

<h2>イベントハンドラ</h2>
<p><code>on:event</code> または省略形の <code>onevent</code> でイベントリスナーを追加します。</p>
<pre class="code-block"><code>&lt;button onclick={_ =&gt; count += 1}&gt;+&lt;/button&gt;
&lt;input oninput={e =&gt; name.set(e.target.value)}/&gt;
&lt;form onsubmit={e =&gt; { e.preventDefault(); handleSubmit() }}&gt;...&lt;/form&gt;</code></pre>

<h2>bind: ディレクティブ</h2>
<p>フォーム要素を <code>State</code> 変数に双方向バインドします。</p>
<pre class="code-block"><code>&lt;input bind:value={name}/&gt;
&lt;input type="checkbox" bind:checked={agreed}/&gt;
&lt;select bind:value={selected}&gt;
  &lt;option value="a"&gt;A&lt;/option&gt;
  &lt;option value="b"&gt;B&lt;/option&gt;
&lt;/select&gt;</code></pre>

<h2>bind:innerHTML — 生 HTML の挿入</h2>
<p><code>TrustedHtml</code> 値をエスケープなしの HTML として描画します。XSS を防ぐため、明示的な信頼注釈が必要です。</p>
<pre class="code-block"><code>// 静的な信頼済みコンテンツ
&lt;div bind:innerHTML={TrustedHtml.unsafe("&lt;strong&gt;Hello&lt;/strong&gt;")}/&gt;

// リアクティブな信頼済みコンテンツ
val html = State(TrustedHtml.unsafe("&lt;em&gt;初期値&lt;/em&gt;"))
&lt;div bind:innerHTML={html}/&gt;</code></pre>

<h2>制御フロー</h2>

<h3>if / else if / else</h3>
<pre class="code-block"><code>{if count &gt; 10 then
  &lt;p&gt;大きな数です！&lt;/p&gt;
else if count &gt; 0 then
  &lt;p&gt;小さな数です&lt;/p&gt;
else
  &lt;p&gt;ゼロです&lt;/p&gt;
}</code></pre>

<h3>リスト描画</h3>
<p><code>.map()</code> でコレクションを繰り返し処理します。安定した差分更新のために <code>key</code> 属性を追加してください。</p>
<pre class="code-block"><code>{items.map(item =&gt;
  &lt;li key={item.id}&gt;{item.name}&lt;/li&gt;
)}</code></pre>

<h2>コンポーネント</h2>
<p>他の <code>.melt</code> コンポーネントを HTML タグとしてインポートして使用します。コンポーネント名は大文字で始めます。</p>
<pre class="code-block"><code>&lt;Button label="クリック" onclick={_ =&gt; doSomething()}/&gt;
&lt;Card title="マイカード"&gt;
  &lt;p&gt;スロットコンテンツ&lt;/p&gt;
&lt;/Card&gt;</code></pre>

<h2>特殊要素</h2>
<table class="api-table">
  <thead><tr><th>要素</th><th>説明</th></tr></thead>
  <tbody>
    <tr><td><code>&lt;Head&gt;</code></td><td>子要素をドキュメントの <code>&lt;head&gt;</code> に描画</td></tr>
    <tr><td><code>&lt;Window&gt;</code></td><td><code>window</code> にイベントリスナーを追加</td></tr>
    <tr><td><code>&lt;Body&gt;</code></td><td><code>document.body</code> にイベントリスナーを追加</td></tr>
    <tr><td><code>&lt;Document&gt;</code></td><td><code>document</code> にイベントリスナーを追加</td></tr>
    <tr><td><code>&lt;Boundary&gt;</code></td><td>エラーバウンダリ — 描画エラーをキャッチ</td></tr>
    <tr><td><code>&lt;Await&gt;</code></td><td><code>Future</code> を解決してローディング/成功/エラー状態を描画</td></tr>
  </tbody>
</table>
"""

    case "runtime" =>
      """
<h1 class="doc-title">Runtime API</h1>
<p class="doc-lead">Melt のランタイムはリアクティブプリミティブ、ライフサイクルフック、HTML エスケープユーティリティを提供します。共有 API はすべて Scala.js (ブラウザ) と JVM (SSR) で動作します。</p>

<h2>State[A]</h2>
<p>ミュータブルなリアクティブ変数です。Scala.js ではミューテーションがすべてのサブスクライバーに通知されます。JVM (SSR) では静的な値を保持するだけで、初期描画に使用されます。</p>

<table class="api-table">
  <thead><tr><th>メンバー</th><th>説明</th></tr></thead>
  <tbody>
    <tr><td><code>State(初期値)</code></td><td>初期値で新しい state を作成</td></tr>
    <tr><td><code>.value</code></td><td>追跡なしで現在値を読み取る</td></tr>
    <tr><td><code>.set(v)</code></td><td>値を置き換えてサブスクライバーに通知</td></tr>
    <tr><td><code>.update(f)</code></td><td><code>A =&gt; A</code> 関数で更新</td></tr>
    <tr><td><code>.map(f)</code></td><td>状態変化時に更新される <code>Signal[B]</code> を導出</td></tr>
    <tr><td><code>.memo(f)</code></td><td><code>map</code> と同様だが、導出値が変化した時のみ emit</td></tr>
    <tr><td><code>.flatMap(f)</code></td><td>動的ソース切り替えに対応したシグナル導出</td></tr>
    <tr><td><code>.subscribe(f)</code></td><td>値の変化を購読。購読解除関数を返す</td></tr>
    <tr><td><code>.signal</code></td><td>この state の読み取り専用ビュー</td></tr>
  </tbody>
</table>

<pre class="code-block"><code>val count   = State(0)
val doubled = count.map(_ * 2)
val isEven  = count.memo(_ % 2 == 0)

count.set(5)        // doubled = 10, isEven = false
count.update(_ + 1) // count = 6, doubled = 12, isEven = true</code></pre>

<div class="callout callout-tip"><strong>Tip:</strong> <code>State[A]</code> には <code>A</code> への暗黙変換があるため、<code>.value</code> を呼ばずに直接式の中で使えます。</div>

<h2>Signal[A]</h2>
<p>読み取り専用のリアクティブ値。通常は <code>State</code> インスタンスから <code>.map</code> または <code>.memo</code> で導出されます。</p>

<table class="api-table">
  <thead><tr><th>メンバー</th><th>説明</th></tr></thead>
  <tbody>
    <tr><td><code>Signal.pure(v)</code></td><td>常に <code>v</code> を保持するフローズンシグナル</td></tr>
    <tr><td><code>.value</code></td><td>現在値を読み取る</td></tr>
    <tr><td><code>.map(f)</code></td><td>新しいシグナルを導出</td></tr>
    <tr><td><code>.memo(f)</code></td><td>重複 emit を抑制するシグナルを導出</td></tr>
    <tr><td><code>.subscribe(f)</code></td><td>変化を購読。購読解除関数を返す</td></tr>
  </tbody>
</table>

<h2>StateExtensions (演算子シュガー)</h2>
<p><code>State</code> 変数に対する数値・コレクション操作です。</p>
<pre class="code-block"><code>val n    = State(0)
val list = State(List.empty[String])

n += 1         // State[Int] をインクリメント
n -= 1         // デクリメント
list :+= "a"   // リストに追加</code></pre>

<h2>onMount</h2>
<p>コンポーネントが DOM にマウントされた後に実行するコールバックを登録します。クリーンアップ関数を返してアンマウント時に実行できます。</p>
<pre class="code-block"><code>import melt.runtime.onMount

onMount { ctx =&gt;
  val timer = setInterval(() =&gt; tick(), 1000)
  ctx.onCleanup(() =&gt; clearInterval(timer))
}</code></pre>
<div class="callout callout-info"><strong>注意:</strong> <code>onMount</code> コールバックは Scala.js でのみ実行されます。JVM では何もしないので、SSR コードでは副作用は発生しません。</div>

<h2>Batch</h2>
<p>複数の状態変更をグループ化し、ブロック完了後に一度だけサブスクライバーに通知します。</p>
<pre class="code-block"><code>import melt.runtime.Batch

Batch {
  x.set(1)
  y.set(2)
  z.set(3)
} // 3つの値が更新された後、サブスクライバーに一度だけ通知</code></pre>

<h2>TrustedHtml</h2>
<p>文字列を生の HTML として安全に描画するためのラッパー型です。偶発的な XSS を防ぐため、<code>bind:innerHTML</code> には必須です。</p>

<table class="api-table">
  <thead><tr><th>メンバー</th><th>説明</th></tr></thead>
  <tbody>
    <tr><td><code>TrustedHtml.unsafe(html)</code></td><td>開発者制御の静的マークアップを信頼する</td></tr>
    <tr><td><code>TrustedHtml.sanitize(html, fn)</code></td><td>カスタムサニタイザーでユーザー入力をサニタイズ</td></tr>
    <tr><td><code>.value</code></td><td>基になる HTML 文字列</td></tr>
  </tbody>
</table>

<h2>Escape</h2>
<p>HTML、属性、URL、CSS のエスケープユーティリティです。生成コードが自動的に呼び出すため、通常は直接使う必要はありません。</p>

<table class="api-table">
  <thead><tr><th>関数</th><th>用途</th></tr></thead>
  <tbody>
    <tr><td><code>Escape.html(v)</code></td><td>HTML テキストコンテンツ用エスケープ</td></tr>
    <tr><td><code>Escape.attr(v)</code></td><td>HTML 属性値用エスケープ</td></tr>
    <tr><td><code>Escape.url(v)</code></td><td>URL の検証とエスケープ (<code>javascript:</code> をブロック)</td></tr>
    <tr><td><code>Escape.cssValue(v)</code></td><td>CSS プロパティ値のエスケープ</td></tr>
  </tbody>
</table>
"""

    case "meltkit" =>
      """
<h1 class="doc-title">MeltKit</h1>
<p class="doc-lead">MeltKit は Melt のサーバーフレームワークです。型安全なルーティング DSL とリクエスト/レスポンスモデルを提供し、Node.js と JVM (http4s 経由) の両方で動作します。SSR、API ルート、静的ファイル配信を処理します。</p>

<div class="callout callout-info"><strong>Scala 3.8 以上が必要です</strong> — MeltKit は型安全なパスパラメータに <code>NamedTuple</code> を使用しています。Scala 3.8 以降が必要です。</div>

<h2>インストール</h2>
<pre class="code-block"><code>// build.sbt
libraryDependencies += "io.github.takapi327" %%% "meltkit" % "0.1.0"

// アダプターを選択:
libraryDependencies += "io.github.takapi327" %%% "meltkit-adapter-browser" % "0.1.0"  // Scala.js
libraryDependencies += "io.github.takapi327" %%% "meltkit-adapter-http4s"  % "0.1.0"  // JVM / Node</code></pre>

<h2>ルート定義</h2>
<pre class="code-block"><code>import meltkit.*

val app = MeltKit[IO]:
  get("") { ctx =&gt;
    ctx.html(AppPage(AppPage.Props()))
  }

  val lang = param[String]("lang")
  get(lang) { ctx =&gt;
    val l = ctx.params.lang
    ctx.html(AppPage(AppPage.Props(lang = l)))
  }

  val slug = param[String]("slug")
  get(lang / "guide" / slug) { ctx =&gt;
    ctx.html(GuidePage(GuidePage.Props(lang = ctx.params.lang, slug = ctx.params.slug)))
  }</code></pre>

<h2>パスパラメータ</h2>
<p><code>param[T]("name")</code> で型付きパスパラメータを定義します。パラメータは <code>/</code> で組み合わせて <code>PathSpec</code> を構築します。</p>

<table class="api-table">
  <thead><tr><th>式</th><th>マッチするパス</th><th>パラメータ型</th></tr></thead>
  <tbody>
    <tr><td><code>"users"</code></td><td><code>/users</code></td><td><code>()</code></td></tr>
    <tr><td><code>param[Int]("id")</code></td><td><code>/:id</code></td><td><code>(id: Int)</code></td></tr>
    <tr><td><code>"users" / param[Int]("id")</code></td><td><code>/users/:id</code></td><td><code>(id: Int)</code></td></tr>
    <tr><td><code>param[String]("lang") / "guide" / param[String]("slug")</code></td><td><code>/:lang/guide/:slug</code></td><td><code>(lang: String, slug: String)</code></td></tr>
  </tbody>
</table>

<h2>MeltContext</h2>
<p>すべてのルートハンドラーは <code>MeltContext[F, P, B, R]</code> を受け取ります。型パラメータはルート定義から自動推論されます。</p>

<table class="api-table">
  <thead><tr><th>メンバー</th><th>説明</th></tr></thead>
  <tbody>
    <tr><td><code>ctx.params</code></td><td>NamedTuple として型付けされたパスパラメータ</td></tr>
    <tr><td><code>ctx.request</code></td><td>HTTP リクエスト</td></tr>
    <tr><td><code>ctx.html(component)</code></td><td>SSR コンポーネントを描画して HTML レスポンスを返す</td></tr>
    <tr><td><code>ctx.json(value)</code></td><td>JSON レスポンスを返す</td></tr>
    <tr><td><code>ctx.text(str)</code></td><td>プレーンテキストレスポンスを返す</td></tr>
    <tr><td><code>ctx.redirect(url)</code></td><td>リダイレクトレスポンスを返す</td></tr>
    <tr><td><code>ctx.locals</code></td><td>リクエストスコープの型安全ストレージ</td></tr>
  </tbody>
</table>

<h2>HTTP メソッド</h2>
<pre class="code-block"><code>val app = MeltKit[IO]:
  get("api/users")    { ctx =&gt; ctx.json(getUsers()) }
  post("api/users")   { ctx =&gt; ctx.json(createUser(ctx.body)) }
  put("api/users/1")  { ctx =&gt; ctx.json(updateUser(ctx.body)) }
  delete("api/users/1") { ctx =&gt; ctx.json(deleteUser()) }</code></pre>

<h2>ミドルウェア (use)</h2>
<pre class="code-block"><code>val app = MeltKit[IO]:
  use { (ctx, next) =&gt;
    println(s"リクエスト: ${ctx.request.method} ${ctx.request.path}")
    next(ctx)
  }

  get("protected") { ctx =&gt;
    if ctx.locals.get(AuthKey).isEmpty
    then ctx.redirect("/login")
    else ctx.html(ProtectedPage())
  }</code></pre>

<h2>Template (HTML シェル)</h2>
<p><code>Template</code> クラスは <code>index.html</code> をラップし、プレースホルダーマーカーを通じて SSR コンテンツを注入します。</p>

<table class="api-table">
  <thead><tr><th>プレースホルダー</th><th>置換内容</th></tr></thead>
  <tbody>
    <tr><td><code>%melt.head%</code></td><td>SSR で描画された <code>&lt;Head&gt;</code> コンテンツ</td></tr>
    <tr><td><code>%melt.body%</code></td><td>SSR で描画されたボディ HTML</td></tr>
    <tr><td><code>%melt.title%</code></td><td><code>&lt;Head&gt;&lt;title&gt;...&lt;/title&gt;&lt;/Head&gt;</code> からのページタイトル</td></tr>
    <tr><td><code>%melt.lang%</code></td><td>言語属性値</td></tr>
    <tr><td><code>%melt.nonce%</code></td><td>インラインスクリプト用 CSP nonce</td></tr>
  </tbody>
</table>

<h2>Locals</h2>
<p>リクエストスコープの型安全なストレージ (型付きコンテキストマップに似ています)。</p>
<pre class="code-block"><code>val UserKey = LocalKey[User]("user")

// ミドルウェア内:
ctx.locals.set(UserKey, currentUser)

// ルートハンドラー内:
val user = ctx.locals.get(UserKey) // Option[User]</code></pre>
"""

    case "meltkit-ssg" =>
      """
<h1 class="doc-title">静的サイト生成</h1>
<p class="doc-lead">Melt は SSR に使用するのと同じ MeltKit ルーティングセットアップを使って、ビルド時に静的 HTML ファイルを生成できます。ドキュメントサイト、ブログ、ランディングページを任意の静的ホストにデプロイするのに最適です。</p>

<h2>SSG の仕組み</h2>
<p>SSG パイプラインは MeltKit のルートツリーを走査し、各ページを JVM SSR レンダラーで描画して HTML ファイルに書き出します。結果は任意の CDN や Web サーバーで配信できる純粋な HTML ファイルのフォルダです。</p>

<div class="callout callout-info"><strong>同じコード、異なる出力:</strong> <code>.melt</code> コンポーネントと MeltKit ルートは SSR と SSG モードで変更不要です。<code>sbt-meltkit</code> プラグインが <code>meltMode</code> 設定でモードを切り替えます。</div>

<h2>セットアップ</h2>
<div class="steps">
  <div class="step">
    <div class="step-num">1</div>
    <div class="step-body">
      <strong><code>meltMode</code> を <code>ssg</code> に設定</strong>
      <pre class="code-block"><code>// build.sbt
.settings(
  meltMode := MeltMode.SSG
)</code></pre>
    </div>
  </div>
  <div class="step">
    <div class="step-num">2</div>
    <div class="step-body">
      <strong>生成するページ一覧を定義</strong>
      <pre class="code-block"><code>.settings(
  meltkitSsgPages := List(
    SsgPage("/"),
    SsgPage("/en/guide/introduction"),
    SsgPage("/en/guide/installation"),
    SsgPage("/ja/guide/introduction")
  )
)</code></pre>
    </div>
  </div>
  <div class="step">
    <div class="step-num">3</div>
    <div class="step-body">
      <strong>ジェネレーターを実行</strong>
      <pre class="code-block"><code>sbt myProject/meltkitSsgGenerate</code></pre>
    </div>
  </div>
</div>

<h2>動的ページ</h2>
<p>CMS やマークダウンファイルなど多数のページがあるサイトでは、ページ一覧をプログラムで生成します。</p>
<pre class="code-block"><code>meltkitSsgPages := {
  val langs  = List("en", "ja")
  val guides = List("introduction", "installation", "quick-start")
  for lang &lt;- langs; slug &lt;- guides
  yield SsgPage(s"/$lang/guide/$slug")
}</code></pre>

<h2>出力ディレクトリ</h2>
<pre class="code-block"><code>meltkitSsgOutputDir := baseDirectory.value / "dist"</code></pre>

<h2>クライアントサイドハイドレーション</h2>
<p>SSG ページはデフォルトでは静的 HTML です。インタラクティブ性を追加するには、Vite でビルドした Scala.js バンドルを含めてハイドレーションを設定します。</p>
<pre class="code-block"><code>// index.html
&lt;script type="module"&gt;
  import("./app.js").then(m =&gt; m.hydrate?.())
&lt;/script&gt;</code></pre>

<h2>デプロイ</h2>
<p>SSG 出力ディレクトリを任意の静的ファイルホストにコピーします: GitHub Pages、Netlify、Vercel、Cloudflare Pages など。</p>
<pre class="code-block"><code># GitHub Pages へのデプロイ例
sbt meltkitSsgGenerate
cp -r target/ssg/* docs/</code></pre>
"""

    case "compiler" =>
      """
<h1 class="doc-title">コンパイラ API</h1>
<p class="doc-lead">Melt コンパイラは <code>.melt</code> ソースファイルを Scala コードに変換します。このページでは、カスタムツール、ビルドシステム、言語サーバーへのコンパイラ統合のためのパブリック API を説明します。</p>

<h2>MeltCompiler</h2>
<p>主要なエントリポイントです。<code>.melt</code> ファイルのテキストを受け取り、生成された Scala コードと警告・エラーを返します。</p>
<pre class="code-block"><code>import melt.MeltCompiler
import melt.MeltCompiler.Config

val source = &quot;&quot;&quot;
  &lt;script lang="scala"&gt;
  case class Props(name: String = "world")
  &lt;/script&gt;
  &lt;p&gt;Hello, {props.name}!&lt;/p&gt;
&quot;&quot;&quot;.stripMargin

val config = Config(
  componentName = "Greeting",
  pkg           = "components",
  mode          = "spa"   // "spa" | "ssr" | "auto"
)

val result = MeltCompiler.compile(source, config)
result.warnings.foreach(w =&gt; println("[warn] " + w.message))
println(result.code)</code></pre>

<h2>Config</h2>
<table class="api-table">
  <thead><tr><th>フィールド</th><th>型</th><th>デフォルト</th><th>説明</th></tr></thead>
  <tbody>
    <tr><td><code>componentName</code></td><td><code>String</code></td><td>必須</td><td>生成コンポーネントの Scala クラス名</td></tr>
    <tr><td><code>pkg</code></td><td><code>String</code></td><td><code>"components"</code></td><td>生成ファイルの Scala パッケージ</td></tr>
    <tr><td><code>mode</code></td><td><code>String</code></td><td><code>"auto"</code></td><td>コード生成モード: <code>spa</code>, <code>ssr</code>, <code>auto</code></td></tr>
    <tr><td><code>hydration</code></td><td><code>Boolean</code></td><td><code>false</code></td><td><code>@JSExportTopLevel("hydrate")</code> エクスポートを emit</td></tr>
    <tr><td><code>hydrationRoot</code></td><td><code>Option[String]</code></td><td><code>None</code></td><td>フルページハイドレーションのルートコンポーネント名</td></tr>
    <tr><td><code>stylePreprocessor</code></td><td><code>Option[String]</code></td><td><code>None</code></td><td><code>StylePreprocessor</code> 実装のクラス名</td></tr>
  </tbody>
</table>

<h2>コンパイルパイプライン</h2>
<div class="steps">
  <div class="step">
    <div class="step-num">1</div>
    <div class="step-body"><strong>パース</strong> — ファイルを script/template/style セクションに分割し、各セクションをパース</div>
  </div>
  <div class="step">
    <div class="step-num">2</div>
    <div class="step-body"><strong>セマンティクス検査</strong> — 属性名、タグ名、バインディングコンテキスト、不正な式を検証</div>
  </div>
  <div class="step">
    <div class="step-num">3</div>
    <div class="step-body"><strong>セキュリティ・A11y 検査</strong> — 安全でない iframe、フォーム、<code>rel="noopener"</code> の欠如などを警告</div>
  </div>
  <div class="step">
    <div class="step-num">4</div>
    <div class="step-body"><strong>CSS プリプロセス</strong> — 設定されていれば SCSS をコンパイルし、CSS をコンポーネントにスコープ</div>
  </div>
  <div class="step">
    <div class="step-num">5</div>
    <div class="step-body"><strong>AST → IR</strong> — <code>TemplateNode</code> AST を中間 <code>IrNode</code> 表現に変換。<code>StaticHoistPass</code> で静的サブツリーをホイスト</div>
  </div>
  <div class="step">
    <div class="step-num">6</div>
    <div class="step-body"><strong>コード生成</strong> — <code>SpaEmitter</code> (Scala.js DOM 操作) または <code>SsrEmitter</code> (文字列ビルダー) が最終 <code>.scala</code> ソースを生成</div>
  </div>
</div>

<h2>MeltWarning</h2>
<pre class="code-block"><code>case class MeltWarning(
  message:  String,
  severity: Severity,  // Error | Warning | Info
  line:     Option[Int],
  column:   Option[Int]
)</code></pre>

<h2>AST 型</h2>
<p>コンパイラは AST を <code>melt.ast.MeltAst</code> で公開しています。カスタム分析や変換のために走査できます。</p>
<pre class="code-block"><code>import melt.ast.MeltAst.*

// TemplateNode の種類:
// Element, Text, Expression, Component, InlineTemplate,
// Head, Window, Body, Document, DynamicElement,
// Boundary, KeyBlock, SnippetDef, RenderCall

// Attr の種類:
// Static, Dynamic, Directive, EventHandler,
// BooleanAttr, Spread, Shorthand</code></pre>
"""

    case "sbt-plugin" =>
      """
<h1 class="doc-title">sbt プラグイン</h1>
<p class="doc-lead"><code>sbt-melt</code> プラグインはプロジェクト内の <code>.melt</code> ファイルを監視し、ビルドのたびに自動的に Scala ソースにコンパイルします。<code>sbt-meltkit</code> プラグインはこれを MeltKit サーバー統合で拡張します。</p>

<h2>インストール</h2>
<pre class="code-block"><code>// project/plugins.sbt
addSbtPlugin("io.github.takapi327" % "sbt-melt"    % "0.1.0")
addSbtPlugin("io.github.takapi327" % "sbt-meltkit" % "0.1.0")</code></pre>

<h2>sbt-melt</h2>
<p><code>.melt</code> ファイルを含む任意の sbt プロジェクトでプラグインを有効にします。</p>
<pre class="code-block"><code>// build.sbt
lazy val client = (project in file("client"))
  .enablePlugins(ScalaJSPlugin, MeltPlugin)
  .settings(
    meltPackage := "components",
    // オプション: コード生成モードを明示的に設定
    meltCodegenMode := "spa"  // "spa" | "ssr" | "auto"
  )</code></pre>

<h3>MeltPlugin 設定</h3>
<table class="api-table">
  <thead><tr><th>キー</th><th>型</th><th>デフォルト</th><th>説明</th></tr></thead>
  <tbody>
    <tr><td><code>meltPackage</code></td><td><code>String</code></td><td><code>"components"</code></td><td>すべての生成ファイルの Scala パッケージ</td></tr>
    <tr><td><code>meltSourceDirectories</code></td><td><code>Seq[File]</code></td><td>unmanagedSourceDirectories</td><td><code>.melt</code> ファイルをスキャンするディレクトリ</td></tr>
    <tr><td><code>meltOutputDirectory</code></td><td><code>File</code></td><td><code>target/src_managed/melt</code></td><td>生成 <code>.scala</code> ファイルの出力先</td></tr>
    <tr><td><code>meltCodegenMode</code></td><td><code>String</code></td><td><code>"auto"</code></td><td>コード生成モード。<code>auto</code> は Scala.js なら <code>spa</code>、それ以外は <code>ssr</code></td></tr>
    <tr><td><code>meltHydration</code></td><td><code>Boolean</code></td><td><code>false</code></td><td>全コンポーネントに <code>@JSExportTopLevel("hydrate")</code> を emit</td></tr>
    <tr><td><code>meltHydrationRoot</code></td><td><code>Option[String]</code></td><td><code>None</code></td><td>フルページハイドレーションのルートコンポーネント名</td></tr>
    <tr><td><code>meltCompilerClasspath</code></td><td><code>Seq[File]</code></td><td>自動解決</td><td>コンパイラクラスパスの上書き (モノレポ向け)</td></tr>
    <tr><td><code>meltStylePreprocessor</code></td><td><code>Option[String]</code></td><td><code>None</code></td><td><code>StylePreprocessor</code> のクラス名</td></tr>
    <tr><td><code>meltGenerate</code></td><td><code>Task[Seq[File]]</code></td><td>—</td><td>すべての <code>.melt</code> ファイルをコンパイル</td></tr>
  </tbody>
</table>

<h2>sbt-meltkit</h2>
<p><code>sbt-melt</code> を MeltKit サーバー統合で拡張します: ルート生成、Vite マニフェスト処理、SSG など。</p>
<pre class="code-block"><code>lazy val server = (project in file("server"))
  .enablePlugins(MeltkitPlugin)
  .settings(
    meltMode := MeltMode.SSR,
    meltkitViteDistDir := (client / baseDirectory).value / "dist"
  )</code></pre>

<h3>MeltkitPlugin 設定</h3>
<table class="api-table">
  <thead><tr><th>キー</th><th>型</th><th>デフォルト</th><th>説明</th></tr></thead>
  <tbody>
    <tr><td><code>meltMode</code></td><td><code>MeltMode</code></td><td><code>Auto</code></td><td><code>SPA</code>, <code>SSR</code>, <code>SSG</code>, <code>Auto</code> のいずれか</td></tr>
    <tr><td><code>meltkitViteDistDir</code></td><td><code>File</code></td><td>—</td><td>Vite ビルド出力ディレクトリのパス</td></tr>
    <tr><td><code>meltkitViteManifestPath</code></td><td><code>File</code></td><td><code>dist/.vite/manifest.json</code></td><td>Vite マニフェストファイルのパス</td></tr>
    <tr><td><code>meltkitSsgPages</code></td><td><code>List[SsgPage]</code></td><td><code>Nil</code></td><td>SSG で生成するページのリスト</td></tr>
    <tr><td><code>meltkitSsgOutputDir</code></td><td><code>File</code></td><td><code>target/ssg</code></td><td>SSG 出力ディレクトリ</td></tr>
    <tr><td><code>meltkitSsgGenerate</code></td><td><code>Task[Unit]</code></td><td>—</td><td>すべての静的ページを生成</td></tr>
  </tbody>
</table>

<h2>モノレポセットアップ</h2>
<p>コンパイラがプロジェクト依存関係のモノレポでは、<code>publishLocal</code> をスキップするために <code>meltCompilerClasspath</code> を上書きします。</p>
<pre class="code-block"><code>// build.sbt — コンパイル済みクラスを直接参照
meltCompilerClasspath := (codegenJVM / Compile / fullClasspath).value.files</code></pre>

<h2>インクリメンタルコンパイル</h2>
<p>プラグインは最後のビルド以降に変更された <code>.melt</code> ファイルのみ再コンパイルします。生成された <code>.scala</code> ファイルは管理ソースとして追跡され、通常の Scala コンパイルパイプラインに自動的に組み込まれます。</p>
"""

    case _ => s"""<h1 class="doc-title">${ ApiContent.titleForLang(slug, "ja") }</h1><p>このページのドキュメントは準備中です。</p>"""
