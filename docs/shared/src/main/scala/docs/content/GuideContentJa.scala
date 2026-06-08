/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package docs.content

object GuideContentJa:

  def content(slug: String, base: String): String = slug match
    case "introduction"     => introduction
    case "installation"     => installation(base)
    case "quick-start"      => quickStart(base)
    case "components"       => components(base)
    case "template-syntax"  => templateSyntax(base)
    case "reactivity"       => reactivity
    case "computed"         => computed
    case "effects"          => effects
    case "events"           => events(base)
    case "lifecycle"        => lifecycle
    case "control-flow"     => controlFlow
    case "special-elements" => specialElements
    case "transitions"      => transitions
    case "trusted-html"     => trustedHtml
    case "css"              => css
    case "testing"          => testing
    case "routing"          => routing(base)
    case "ssr"              => ssr
    case "ssg"              => ssg
    case "adapters"         => adapters
    case _                  => ""

  // ── Introduction ──────────────────────────────────────────────────────────

  private val introduction = """
    <p class="doc-lead">Melt は <strong>Scala.js 向けの Single File Component (SFC) フレームワーク</strong>です。
    Svelte にインスパイアされており、<code>.melt</code> ファイルにロジック・マークアップ・スタイルを記述すると、
    コンパイラが仮想 DOM なしの効率的な DOM 操作コードを生成します。</p>

    <h2>3 つのコアアイデア</h2>

    <p>Melt は以下の 3 つのシンプルな考え方を基盤としています。</p>
    <ul>
      <li><strong>コンパイラが処理する。</strong>
        リアクティビティはインポートするライブラリではなく、Melt コンパイラが生成コードに直接織り込みます。
        ランタイムのオーバーヘッドは最小限です。</li>
      <li><strong>Scala の型がテンプレートを検査する。</strong>
        <code>{}</code> 内の式はすべて本物の Scala コードです。scalac によるコンパイル時型チェックが
        テンプレートにも適用されるため、タイポや型の不一致はビルド時に検出されます。</li>
      <li><strong>1 ソースで 3 ターゲット。</strong>
        同じ <code>.melt</code> ファイルが SPA (Scala.js による DOM コード)、SSR (JVM での HTML 文字列)、
        SSG (静的 HTML ファイル) の 3 形式にコンパイルされます。</li>
    </ul>

    <h2>はじめての Melt コンポーネント</h2>

    <p>インタラクティブなカウンターを 15 行で実装した例です。</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>Counter.melt
      </div>
      <pre><code>&lt;script lang="scala"&gt;
  val count   = State(0)
  val doubled = count.map(_ * 2)
&lt;/script&gt;

&lt;div class="counter"&gt;
  &lt;h1&gt;{count}&lt;/h1&gt;
  &lt;p&gt;doubled: {doubled}&lt;/p&gt;
  &lt;button onclick={_ =&gt; count += 1}&gt;+&lt;/button&gt;
  &lt;button onclick={_ =&gt; count -= 1}&gt;-&lt;/button&gt;
&lt;/div&gt;

&lt;style&gt;
  h1 { font-size: 4rem; color: #d6526a; }
&lt;/style&gt;</code></pre>
    </div>

    <p>この 15 行には次の要素が含まれています。</p>
    <ul>
      <li>ミュータブルなリアクティブ値 (<code>State(0)</code>)</li>
      <li>自動更新される派生値 (<code>count.map(_ * 2)</code>)</li>
      <li>状態を変更するイベントハンドラ (<code>count += 1</code>)</li>
      <li>このコンポーネントにのみ適用されるスコープ付き CSS</li>
    </ul>

    <h2>コンパイルパイプライン</h2>

    <p>Melt コンパイラは <code>.melt</code> ファイルを次の手順で処理します。</p>
    <ol>
      <li><code>&lt;script&gt;</code>・テンプレート・<code>&lt;style&gt;</code> セクションをパース</li>
      <li>セマンティックチェック（型ヒント・a11y 警告・セキュリティチェック）を実行</li>
      <li>テンプレート AST を内部 IR に変換</li>
      <li>SPA または SSR エミッターで Scala コードを生成</li>
    </ol>

    <p>出力は通常の Scala オブジェクトです。scalac / Scala.js でそのままコンパイルできます。
    ブラウザには Melt ランタイムは存在せず、実際に使用するリアクティブプリミティブのみが含まれます。</p>

    <h2>SPA・SSR・SSG の違い</h2>

    <table class="api-table">
      <thead>
        <tr>
          <th>モード</th>
          <th>レンダリング場所</th>
          <th>用途</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td><strong>SPA</strong></td>
          <td>ブラウザ (Scala.js)</td>
          <td>インタラクティブなシングルページアプリ</td>
        </tr>
        <tr>
          <td><strong>SSR</strong></td>
          <td>JVM サーバー</td>
          <td>初期表示の高速化・SEO 改善</td>
        </tr>
        <tr>
          <td><strong>SSG</strong></td>
          <td>ビルド時 (JVM)</td>
          <td>CDN 配信・ゼロサーバーインフラ</td>
        </tr>
      </tbody>
    </table>

    <h2>Scala との統合</h2>

    <p>Melt はあくまで Scala の上に薄く乗ったレイヤーです。
    テンプレート内の式は scalac で型検査されるため、IDE の補完・リファクタリング・
    ナビゲーションがそのまま機能します。
    既存の Scala ライブラリや cats / ZIO などのエコシステムも制限なく使えます。</p>

    <div class="callout callout-tip">
      <div class="callout-title">今すぐ試してみましょう</div>
      <p>ブラウザ上でリアルタイムにコンパイルを確認できる
      <a href="/playground">Playground</a> を開いてみてください。インストール不要です。</p>
    </div>

    <h2>Melt が「ではない」もの</h2>
    <ul>
      <li>フルスタックのメタフレームワーク単体ではありません（その役割は <strong>MeltKit</strong> が担います）。</li>
      <li>仮想 DOM を採用していません。更新は細粒度かつターゲットを絞って行われます。</li>
      <li>React・Vue・その他の JS フレームワークを必要としません。</li>
    </ul>
  """

  // ── Installation ──────────────────────────────────────────────────────────

  private def installation(base: String) = s"""
    <p class="doc-lead">Melt は sbt プラグイン経由で既存のプロジェクトに組み込めます。
    このページではゼロから必要なセットアップを順を追って説明します。</p>

    <h2>前提条件</h2>
    <ul>
      <li><strong>sbt 1.9 以上</strong></li>
      <li><strong>Scala 3.3.7 以上</strong>（コンパイラモジュール用。MeltKit は Scala 3.8 以上が必要）</li>
      <li><strong>Node.js 18 以上</strong>（Vite の開発サーバーとバンドルに使用）</li>
      <li><strong>JDK 17 以上</strong></li>
    </ul>

    <h2>1 · sbt プラグインを追加する</h2>

    <p><code>project/plugins.sbt</code> を作成または編集します。</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>project/plugins.sbt
      </div>
      <pre><code>addSbtPlugin("io.github.takapi327" % "sbt-melt" % "0.1.0-SNAPSHOT")</code></pre>
    </div>

    <h2>2 · build.sbt を設定する</h2>

    <p>Scala.js モジュールでプラグインを有効化します。</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>build.sbt
      </div>
      <pre><code>// Scala.js フロントエンドモジュール
lazy val client = project
  .in(file("client"))
  .enablePlugins(ScalaJSPlugin, MeltPlugin)
  .settings(
    scalaVersion := "3.3.7"
  )</code></pre>
    </div>

    <h2>3 · ディレクトリ構成</h2>

    <p>プラグインは <code>src/main/scala</code>（または設定したソースディレクトリ）以下の
    <code>.melt</code> ファイルを自動検出します。生成された Scala ソースは
    <code>target/scala-3.x.x/src_managed/main/melt/</code> に配置され、
    通常の Scala ファイルと一緒にコンパイルされます。</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>典型的なレイアウト
      </div>
      <pre><code>my-project/
├── build.sbt
├── project/
│   └── plugins.sbt
└── client/
    └── src/main/scala/
        └── myapp/
            ├── Counter.melt      ← コンポーネント
            └── App.melt</code></pre>
    </div>

    <h2>4 · Vite の設定（任意）</h2>

    <p>Melt はどのバンドラーとも連携できますが、開発には Vite が最適です。
    プロジェクトルートに <code>vite.config.mjs</code> を作成します。</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>vite.config.mjs
      </div>
      <pre><code>import { defineConfig } from 'vite'

export default defineConfig({
  publicDir: 'public',
  build: {
    rollupOptions: {
      input: 'index.html',
    }
  }
})</code></pre>
    </div>

    <div class="callout callout-tip">
      <div class="callout-title">SNAPSHOT リリースについて</div>
      <p>Melt は現在活発に開発中です。依存関係が見つからない場合は、
      <code>project/repositories</code> または <code>build.sbt</code> に
      Sonatype スナップショットリポジトリを追加してください。</p>
    </div>

    <div class="callout callout-info">
      <div class="callout-title">次のステップ</div>
      <p>セットアップが完了したら <a href="$base/guide/quick-start">クイックスタート</a> で
      最初のコンポーネントを作成してみましょう。</p>
    </div>
  """

  // ── Quick Start ───────────────────────────────────────────────────────────

  private def quickStart(base: String) = s"""
    <p class="doc-lead">このガイドでは、カウンターコンポーネントを 5 分以内でゼロから動かすところまで解説します。</p>

    <div class="steps">
      <div class="step">
        <div class="step-num">1</div>
        <div class="step-body">
          <h3>プロジェクトを作成する</h3>
          <p>Melt のカウンターサンプルをベースにするか、
          プラグインを有効化した最小限の sbt プロジェクトを作成します
          （<a href="$base/guide/installation">インストール</a> を参照）。</p>
        </div>
      </div>

      <div class="step">
        <div class="step-num">2</div>
        <div class="step-body">
          <h3>最初のコンポーネントを書く</h3>
          <p><code>src/main/scala/Counter.melt</code> を作成します。</p>
          <div class="code-block">
            <div class="code-block-header">
              <span class="code-block-dot"></span>Counter.melt
            </div>
            <pre><code>&lt;script lang="scala"&gt;
  // Props: 外部から渡されるパラメータ (任意)
  case class Props(initialCount: Int = 0)

  // State: コンポーネント内部のリアクティブ状態
  val count = State(props.initialCount)
&lt;/script&gt;

&lt;!-- Template: HTML + Scala 式 --&gt;
&lt;div&gt;
  &lt;p&gt;カウント: {count}&lt;/p&gt;
  &lt;button onclick={_ =&gt; count += 1}&gt;増やす&lt;/button&gt;
  &lt;button onclick={_ =&gt; count.set(0)}&gt;リセット&lt;/button&gt;
&lt;/div&gt;

&lt;!-- Style: このコンポーネントにのみ適用されるスコープ付き CSS --&gt;
&lt;style&gt;
  p { font-size: 1.5rem; font-weight: bold; }
  button { margin-right: 8px; padding: 6px 16px; }
&lt;/style&gt;</code></pre>
          </div>
          <p>コンポーネントは <code>&lt;script&gt;</code>・テンプレート・<code>&lt;style&gt;</code> の
          3 セクションで構成されます。</p>
        </div>
      </div>

      <div class="step">
        <div class="step-num">3</div>
        <div class="step-body">
          <h3>コンポーネントをマウントする</h3>
          <p>Scala.js のエントリポイントを作成して DOM にコンポーネントをマウントします。</p>
          <div class="code-block">
            <div class="code-block-header">
              <span class="code-block-dot"></span>Main.scala
            </div>
            <pre><code>import org.scalajs.dom
import melt.runtime.Mount
import scala.scalajs.js.annotation.JSExportTopLevel

@JSExportTopLevel("main")
def main(): Unit =
  Mount.render(Counter(Counter.Props()), dom.document.getElementById("app"))</code></pre>
          </div>
        </div>
      </div>

      <div class="step">
        <div class="step-num">4</div>
        <div class="step-body">
          <h3>index.html を作成する</h3>
          <div class="code-block">
            <div class="code-block-header">
              <span class="code-block-dot"></span>index.html
            </div>
            <pre><code>&lt;!DOCTYPE html&gt;
&lt;html lang="ja"&gt;
  &lt;head&gt;&lt;meta charset="UTF-8" /&gt;&lt;title&gt;My App&lt;/title&gt;&lt;/head&gt;
  &lt;body&gt;
    &lt;div id="app"&gt;&lt;/div&gt;
    &lt;script type="module"&gt;
      import { main } from './target/.../main.js'
      main()
    &lt;/script&gt;
  &lt;/body&gt;
&lt;/html&gt;</code></pre>
          </div>
        </div>
      </div>

      <div class="step">
        <div class="step-num">5</div>
        <div class="step-body">
          <h3>実行する</h3>
          <pre><code># sbt でコンパイル
sbt client/fastLinkJS

# Vite で開発サーバーを起動
npx vite</code></pre>
          <p><code>http://localhost:5173</code> を開いてボタンをクリックしてみてください。
          ページのリロードなしにカウンターが即座に更新されます。</p>
        </div>
      </div>
    </div>

    <div class="callout callout-tip">
      <div class="callout-title">インストール不要で試したい場合</div>
      <p><a href="$base/playground">Playground</a> を使えばブラウザ上で Melt を
      直接試すことができます。セットアップは一切不要です。</p>
    </div>
  """

  // ── Components ────────────────────────────────────────────────────────────

  private def components(base: String) = s"""
    <p class="doc-lead">Melt コンポーネントは <code>.melt</code> ファイルで構成され、
    <code>&lt;script&gt;</code>・テンプレート・<code>&lt;style&gt;</code> の最大 3 つのセクションを持ちます。
    ロジック・マークアップ・スタイルを 1 ファイルにまとめることで、関心事が自然にまとまります。</p>

    <h2>ファイル構造</h2>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>Greeting.melt
      </div>
      <pre><code>&lt;!-- 1. Script セクション: Scala のロジック --&gt;
&lt;script lang="scala"&gt;
  case class Props(name: String = "World")
  val greeting = "こんにちは"
&lt;/script&gt;

&lt;!-- 2. Template: 式を埋め込んだ HTML --&gt;
&lt;p class="msg"&gt;{greeting}、{props.name}！&lt;/p&gt;

&lt;!-- 3. Style セクション: スコープ付き CSS --&gt;
&lt;style&gt;
  .msg { font-size: 1.25rem; color: #d6526a; }
&lt;/style&gt;</code></pre>
    </div>

    <h2>Props の定義</h2>

    <p>コンポーネントへの入力は <code>&lt;script&gt;</code> ブロック内に
    <code>case class Props</code> として定義します。
    デフォルト値を設定することで、すべての Props を省略可能にできます。</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>Button.melt
      </div>
      <pre><code>&lt;script lang="scala"&gt;
  case class Props(
    label:    String  = "クリック",
    disabled: Boolean = false
  )
&lt;/script&gt;

&lt;button disabled={props.disabled}&gt;{props.label}&lt;/button&gt;</code></pre>
    </div>

    <p>Props はスクリプトおよびテンプレートのどこからでも <code>props</code> 経由でアクセスできます。</p>
    <pre><code>props.label    // String
props.disabled // Boolean</code></pre>

    <h2>コンポーネントの利用</h2>

    <p>コンポーネントはインポートして HTML タグ（大文字始まり）として使います。</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>App.melt
      </div>
      <pre><code>&lt;script lang="scala"&gt;
  import myapp.Button
&lt;/script&gt;

&lt;div&gt;
  &lt;Button label="保存" /&gt;
  &lt;Button label="キャンセル" disabled={true} /&gt;
&lt;/div&gt;</code></pre>
    </div>

    <h2>children スロット</h2>

    <p>組み込みの <code>children</code> 値を使うと、ネストされたコンテンツをレンダリングできます。</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>Card.melt
      </div>
      <pre><code>&lt;div class="card"&gt;
  {children}
&lt;/div&gt;

&lt;style&gt;
  .card { padding: 24px; border: 1px solid #ccc; border-radius: 8px; }
&lt;/style&gt;</code></pre>
    </div>

    <pre><code>&lt;!-- 使用例 --&gt;
&lt;Card&gt;
  &lt;h2&gt;タイトル&lt;/h2&gt;
  &lt;p&gt;任意のコンテンツをここに。&lt;/p&gt;
&lt;/Card&gt;</code></pre>

    <h2>Props のデフォルト値</h2>

    <p>デフォルト値を設定した Props は省略して呼び出すことができます。
    コンポーネントの API を使いやすく保ちつつ、必要なときだけカスタマイズできる設計が可能です。</p>

    <pre><code>&lt;!-- label のみ指定し、disabled はデフォルト値 (false) を使用 --&gt;
&lt;Button label="送信" /&gt;

&lt;!-- 両方指定 --&gt;
&lt;Button label="削除" disabled={isDeleting} /&gt;</code></pre>

    <h2>スコープ付きスタイル</h2>

    <p>コンポーネントの <code>&lt;style&gt;</code> ブロックに書かれた CSS は、
    自動的にそのコンポーネントにスコープされます。
    コンパイラがレンダリングされた要素に一意の属性を付与するため、
    スタイルが子コンポーネントや兄弟要素に漏れることはありません。</p>

    <div class="callout callout-info">
      <div class="callout-title">グローバルスタイルについて</div>
      <p>スタイルをグローバルに適用したい場合は <a href="$base/guide/css">CSS ガイド</a> を参照してください。</p>
    </div>
  """

  // ── Template Syntax ───────────────────────────────────────────────────────

  private def templateSyntax(base: String) = s"""
    <p class="doc-lead">Melt のテンプレートは、Scala 式・ディレクティブ・イベントハンドラで拡張された標準 HTML です。
    <code>{}</code> 内はすべて Scala として評価されます。</p>

    <h2>テキスト補間</h2>

    <p>テンプレートの任意の場所で <code>{}</code> を使って Scala 式を埋め込めます。</p>
    <pre><code>&lt;p&gt;{count}&lt;/p&gt;
&lt;p&gt;{count.map(_ * 2)}&lt;/p&gt;
&lt;p&gt;{"こんにちは、" + props.name + "さん！"}&lt;/p&gt;</code></pre>

    <p><code>Signal[A]</code> や <code>State[A]</code> を返す式は自動的にサブスクライブされ、
    値が変わると DOM が更新されます。</p>

    <h2>属性バインディング</h2>

    <p>動的な属性値には <code>attr={expr}</code> を使います。</p>
    <pre><code>&lt;img src={imageUrl} alt={props.alt} /&gt;
&lt;input type="text" placeholder={hint} /&gt;
&lt;div id={"section-" + props.id}&gt;&lt;/div&gt;</code></pre>

    <h2>双方向バインディング</h2>

    <p><code>bind:value</code> は <code>State[String]</code> と input 要素を双方向に結びつけます。</p>
    <pre><code>&lt;script lang="scala"&gt;
  val name = State("")
&lt;/script&gt;

&lt;input type="text" bind:value={name} placeholder="お名前" /&gt;
&lt;p&gt;こんにちは、{name}さん！&lt;/p&gt;</code></pre>

    <table class="api-table">
      <thead><tr><th>ディレクティブ</th><th>対象</th><th>説明</th></tr></thead>
      <tbody>
        <tr><td><code>bind:value</code></td><td>text, textarea, select</td><td>文字列の双方向バインディング</td></tr>
        <tr><td><code>bind:checked</code></td><td>checkbox</td><td>Boolean の双方向バインディング</td></tr>
        <tr><td><code>bind:this</code></td><td>任意の要素</td><td>DOM 要素を <code>Ref</code> に格納する</td></tr>
      </tbody>
    </table>

    <h2>class ディレクティブ</h2>

    <p><code>class:name={signal}</code> で CSS クラスをリアクティブに切り替えます。</p>
    <pre><code>&lt;button class:active={isActive}&gt;クリック&lt;/button&gt;</code></pre>

    <p>静的な <code>class</code> と組み合わせることもできます。</p>
    <pre><code>&lt;div class="item" class:selected={isSelected} class:disabled={isDisabled}&gt;</code></pre>

    <h2>style ディレクティブ</h2>

    <p>個別の CSS プロパティをリアクティブに設定します。</p>
    <pre><code>&lt;div style:color={textColor} style:font-size={fontSize + "px"}&gt;&lt;/div&gt;</code></pre>

    <h2>イベントハンドラ</h2>

    <p><code>on&lt;event&gt;={handler}</code> で DOM イベントリスナーを設定します。</p>
    <pre><code>&lt;button onclick={_ =&gt; count += 1}&gt;+&lt;/button&gt;
&lt;input oninput={e =&gt; name.set(e.target.asInstanceOf[dom.html.Input].value)} /&gt;
&lt;form onsubmit={e =&gt; { e.preventDefault(); submit() }}&gt;&lt;/form&gt;</code></pre>

    <h2>スプレッド属性</h2>

    <p>属性のマップを要素に展開できます。</p>
    <pre><code>&lt;script lang="scala"&gt;
  val attrs = Map("role" -&gt; "button", "aria-label" -&gt; "閉じる")
&lt;/script&gt;

&lt;div {...attrs}&gt;&lt;/div&gt;</code></pre>

    <h2>要素参照</h2>

    <p><code>bind:this</code> で DOM 要素をキャプチャします。</p>
    <pre><code>&lt;script lang="scala"&gt;
  import melt.runtime.Ref
  val inputRef = Ref.empty[dom.html.Input]
&lt;/script&gt;

&lt;input bind:this={inputRef} /&gt;
&lt;button onclick={_ =&gt; inputRef.foreach(_.focus())}&gt;フォーカス&lt;/button&gt;</code></pre>

    <div class="callout callout-info">
      <div class="callout-title">コメントについて</div>
      <p>テンプレート内で HTML コメント <code>&lt;!-- ... --&gt;</code> は通常通り記述できます。
      コンパイラはコメントを無視してコード生成します。</p>
    </div>
  """

  // ── Reactivity ────────────────────────────────────────────────────────────

  private val reactivity = """
    <p class="doc-lead">Melt のリアクティビティは <code>State[A]</code>（ミュータブル）と
    <code>Signal[A]</code>（読み取り専用の派生値）という 2 つの核心型で構成されます。
    <code>State</code> が変わると、それを読んでいる UI の部分が自動更新されます。</p>

    <h2>State を作成する</h2>

    <p><code>State(initialValue)</code> でミュータブルなリアクティブ値を作成します。</p>
    <pre><code>val count   = State(0)              // State[Int]
val name    = State("Alice")        // State[String]
val items   = State(List[String]()) // State[List[String]]</code></pre>

    <p>現在の値は <code>.value</code> または暗黙変換で読み取れます。</p>
    <pre><code>val n: Int = count.value  // 明示的
val n: Int = count        // 暗黙変換も可能</code></pre>

    <h2>State を更新する</h2>

    <p><code>.set()</code>、<code>.update()</code>、または組み込み演算子を使います。</p>
    <pre><code>count.set(10)             // 値を置き換える
count.update(_ + 1)       // 変換関数で更新
count += 1                // Int/Long/Double 用のショートハンド
count -= 1
name += " Smith"          // 文字列の追記

// リスト操作
items.append("新しいアイテム")
items.prepend("先頭")
items.removeWhere(_.isEmpty)
items.clear()</code></pre>

    <h2>Signal — 読み取り専用の派生値</h2>

    <p><code>Signal[A]</code> は 1 つ以上の <code>State</code> から派生した読み取り専用のビューです。
    <code>.map()</code> で作成します。</p>
    <pre><code>val doubled: Signal[Int]     = count.map(_ * 2)
val upper:   Signal[String]  = name.map(_.toUpperCase)
val isEmpty: Signal[Boolean] = items.map(_.isEmpty)</code></pre>

    <p>Signal は元の値が変わると自動更新されます。テンプレートでは <code>State</code> と同じように使います。</p>
    <pre><code>&lt;p&gt;{count} × 2 = {doubled}&lt;/p&gt;</code></pre>

    <h2>DOM のリアクティブ更新</h2>

    <p>Melt テンプレートで <code>State</code> や <code>Signal</code> を読み取る式はすべてトラッキングされます。
    値が変わると、その式に対応する DOM ノードだけが更新されます。コンポーネント全体を再レンダリングすることはありません。</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>細粒度更新の例
      </div>
      <pre><code>&lt;script lang="scala"&gt;
  val count = State(0)
&lt;/script&gt;

&lt;!-- count が変わるとこのテキストノードだけ更新される --&gt;
&lt;p&gt;カウント: {count}&lt;/p&gt;

&lt;!-- この要素は静的なので一切再レンダリングしない --&gt;
&lt;p&gt;このテキストは変わりません。&lt;/p&gt;</code></pre>
    </div>

    <div class="callout callout-tip">
      <div class="callout-title">仮想 DOM なし</div>
      <p>Melt はツリーの差分計算を行いません。各リアクティブバインディングが独立したサブスクリプションです。
      1 つの値を変更すると、それに依存する DOM ノードだけが更新されます。</p>
    </div>

    <h2>.subscribe() によるサブスクリプション</h2>

    <p>テンプレート外から State/Signal の変化を監視したい場合は <code>.subscribe()</code> を使います。</p>
    <pre><code>val cancel = count.subscribe { newValue =&gt;
  println(s"count changed to $newValue")
}

// 監視を止めるときは cancel() を呼ぶ
cancel()</code></pre>
  """

  // ── Computed ──────────────────────────────────────────────────────────────

  private val computed = """
    <p class="doc-lead">算出値（Computed values）は依存関係が変わると自動更新される派生 <code>Signal</code> です。
    スクリプトセクションで宣言し、<code>State</code> と同じようにテンプレートで利用します。</p>

    <h2>.map() — 値を変換する</h2>

    <p>既存の Signal から新しい Signal を作成するには <code>.map()</code> を使います。</p>
    <pre><code>val count:   State[Int]     = State(0)
val doubled: Signal[Int]    = count.map(_ * 2)
val label:   Signal[String] = count.map(n =&gt; if n == 0 then "ゼロ" else n.toString)</code></pre>

    <pre><code>&lt;p&gt;{count} の2倍は {doubled}&lt;/p&gt;
&lt;p&gt;ラベル: {label}&lt;/p&gt;</code></pre>

    <h2>.flatMap() — 動的なソース切り替え</h2>

    <p>派生値が別の <code>Signal</code> に依存するときは <code>.flatMap()</code> を使います。</p>
    <pre><code>val mode   = State("spa")
val output = source.flatMap { code =&gt;
  mode.map { m =&gt;
    compile(code, m)  // source または mode が変わるたびに再実行される
  }
}</code></pre>

    <p>この場合、<code>source</code> か <code>mode</code> のどちらかが変わると
    <code>output</code> が自動更新されます。</p>

    <h2>.memo() — 重複更新を抑制する</h2>

    <p>計算した値が実際には変わっていないときにも下流の更新が走ることがあります。
    <code>.memo()</code> を使うと値が同じ場合は更新をスキップできます。</p>
    <pre><code>val isEven: Signal[Boolean] = count.memo(_ % 2 == 0)
// count が変わるたびではなく、Boolean 値が反転したときだけ下流を更新する</code></pre>

    <div class="callout callout-tip">
      <div class="callout-title">.memo() を使うべきタイミング</div>
      <p>マップ先の型が安価な等値チェックを持ち、かつ親が頻繁に変化する場合に有効です。
      例えば、整数カウンターから導出した Boolean フラグなどが典型例です。</p>
    </div>

    <h2>複数の Signal を組み合わせる</h2>

    <p><code>.map()</code> を連鎖させるか、<code>.flatMap()</code> を使って複数のリアクティブソースを結合します。</p>
    <pre><code>val firstName = State("田中")
val lastName  = State("太郎")
val fullName  = firstName.flatMap(f =&gt; lastName.map(l =&gt; s"$f $l"))</code></pre>

    <pre><code>&lt;p&gt;フルネーム: {fullName}&lt;/p&gt;</code></pre>

    <h2>算出値のベストプラクティス</h2>

    <ul>
      <li>重い計算は <code>.memo()</code> でキャッシュして無駄な再計算を防ぐ</li>
      <li>算出値は読み取り専用の <code>val</code> として宣言し、直接変更しない</li>
      <li>複数ステップの変換はチェーンで分かりやすく表現する</li>
    </ul>
  """

  // ── Effects ───────────────────────────────────────────────────────────────

  private val effects = """
    <p class="doc-lead"><em>エフェクト</em>は、リアクティブな依存関係が変化するたびに再実行される
    副作用を伴う処理です。ログ出力・ネットワークリクエスト・直接的な DOM 操作などに使います。</p>

    <h2>基本的なエフェクト</h2>

    <p>スクリプトセクション内で <code>Effect { ... }</code> を呼び出します。
    ブロックはマウント時に一度実行され、内部で読んだ <code>State</code> や <code>Signal</code> が
    変わるたびに再実行されます。</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>使用例
      </div>
      <pre><code>&lt;script lang="scala"&gt;
  import melt.runtime.Effect

  val query = State("")

  Effect {
    // `query` が変わるたびに実行される
    println(s"検索クエリ: ${query.value}")
    fetchResults(query.value)
  }
&lt;/script&gt;</code></pre>
    </div>

    <h2>クリーンアップ</h2>

    <p>コンポーネントがアンマウントされたり、エフェクトが再実行される前に
    サブスクリプションやタイマー、イベントリスナーを解除したい場合は
    <code>Cleanup.register</code> を使います。</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>クリーンアップを持つエフェクト
      </div>
      <pre><code>import melt.runtime.{ Effect, Cleanup }

val count = State(0)

Effect {
  val id = js.timers.setInterval(1000) { count += 1 }
  Cleanup.register(() =&gt; js.timers.clearInterval(id))
}</code></pre>
    </div>

    <h2>Untrack — 依存なし読み取り</h2>

    <p>エフェクト内で値を読みたいが依存関係として登録したくない場合は
    <code>Untrack { ... }</code> でラップします。</p>
    <pre><code>import melt.runtime.Untrack

Effect {
  val current = Untrack { count.value }  // サブスクライブせずに読む
  println(s"別の依存関係でトリガーされた。count は $current")
}</code></pre>

    <div class="callout callout-warn">
      <div class="callout-title">無限ループに注意</div>
      <p>同一エフェクト内で読み取った <code>State</code> に書き込むと無限更新ループが発生します。
      書き込みが必要な場合は <code>Untrack</code> で読み取りを保護してください。</p>
    </div>

    <h2>複数の依存関係</h2>

    <p>1 つのエフェクトで複数の State/Signal を読み取ると、そのどれが変わっても再実行されます。</p>
    <pre><code>val x = State(0)
val y = State(0)

Effect {
  // x または y が変わると再実行
  println(s"position: (${x.value}, ${y.value})")
  updatePosition(x.value, y.value)
}</code></pre>
  """

  // ── Events ────────────────────────────────────────────────────────────────

  private def events(base: String) = s"""
    <p class="doc-lead">Melt のイベントハンドラは、<code>on&lt;event&gt;={handler}</code> 構文で
    HTML 要素に直接取り付けるプレーンな Scala 関数です。</p>

    <h2>基本的なハンドラ</h2>

    <pre><code>&lt;button onclick={_ =&gt; count += 1}&gt;増やす&lt;/button&gt;
&lt;button onclick={_ =&gt; count.set(0)}&gt;リセット&lt;/button&gt;</code></pre>

    <p>ハンドラはネイティブの DOM イベントを引数として受け取ります。
    イベントが不要な場合は <code>_</code> で無視できます。</p>

    <h2>イベントオブジェクトへのアクセス</h2>

    <pre><code>&lt;input oninput={e =&gt; name.set(e.target.asInstanceOf[dom.html.Input].value)} /&gt;
&lt;form onsubmit={e =&gt; { e.preventDefault(); submit() }}&gt;&lt;/form&gt;</code></pre>

    <p><code>org.scalajs.dom</code> が提供する代表的なイベント型</p>
    <table class="api-table">
      <thead><tr><th>ハンドラ</th><th>イベント型</th><th>主な用途</th></tr></thead>
      <tbody>
        <tr><td><code>onclick</code></td><td><code>MouseEvent</code></td><td>ボタン・リンク</td></tr>
        <tr><td><code>oninput</code></td><td><code>InputEvent</code></td><td>テキスト入力の変化</td></tr>
        <tr><td><code>onchange</code></td><td><code>Event</code></td><td>select・チェックボックス</td></tr>
        <tr><td><code>onsubmit</code></td><td><code>SubmitEvent</code></td><td>フォーム送信</td></tr>
        <tr><td><code>onkeydown</code></td><td><code>KeyboardEvent</code></td><td>キーショートカット</td></tr>
        <tr><td><code>onfocus / onblur</code></td><td><code>FocusEvent</code></td><td>フォーカス管理</td></tr>
      </tbody>
    </table>

    <h2>bind:value ショートハンド</h2>

    <p><code>oninput</code> を手動で配線する代わりに <code>bind:value</code> を使えば、
    テキスト入力と <code>State[String]</code> の双方向同期が簡単に実現できます。</p>
    <pre><code>val text = State("")
// ...
&lt;input type="text" bind:value={text} /&gt;
&lt;p&gt;入力値: {text}&lt;/p&gt;</code></pre>

    <h2>Window・Body のグローバルイベント</h2>

    <p><code>&lt;melt:window&gt;</code> や <code>&lt;melt:body&gt;</code> 特殊要素でグローバルリスナーを設定します
    （<a href="$base/guide/special-elements">特殊要素</a> を参照）。</p>
    <pre><code>&lt;melt:window onkeydown={e =&gt; handleShortcut(e)} /&gt;</code></pre>

    <p>コンポーネントがアンマウントされると、リスナーは自動的に削除されます。</p>

    <div class="callout callout-tip">
      <div class="callout-title">カスタムイベント</div>
      <p>子コンポーネントから親へ通知を送りたい場合は、Props に
      コールバック関数 <code>onXxx: () =&gt; Unit</code> を定義するのが
      Melt での推奨パターンです。</p>
    </div>
  """

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  private val lifecycle = """
    <p class="doc-lead">Melt コンポーネントのライフサイクルはシンプルです。
    DOM に挿入されたとき（<em>マウント</em>）と削除されたとき（<em>デストロイ</em>）の
    2 つのタイミングにフックできます。</p>

    <h2>OnMount</h2>

    <p><code>OnMount { ... }</code> 内のコードは、コンポーネントの DOM がドキュメントに
    挿入された後に一度だけ実行されます。DOM のサイズ計測やキャンバスへの描画など、
    実際に DOM が存在しないとできない処理をここに書きます。</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>使用例
      </div>
      <pre><code>&lt;script lang="scala"&gt;
  import melt.runtime.OnMount
  import melt.runtime.Ref

  val canvasRef = Ref.empty[dom.html.Canvas]

  OnMount {
    canvasRef.foreach { canvas =&gt;
      // canvas は DOM に存在する — 安全に計測・描画できる
      val ctx = canvas.getContext("2d")
      // ...
    }
  }
&lt;/script&gt;

&lt;canvas bind:this={canvasRef}&gt;&lt;/canvas&gt;</code></pre>
    </div>

    <div class="callout callout-info">
      <div class="callout-title">JVM (SSR) での注意</div>
      <p><code>OnMount</code> は JVM 上では no-op です。ブラウザでのみ実行されます。</p>
    </div>

    <h2>Cleanup — デストロイ時のクリーンアップ</h2>

    <p><code>Cleanup.register</code> でティアダウンコールバックを登録できます。
    コンポーネントが DOM から削除されたときに実行されます。</p>

    <pre><code>import melt.runtime.{ OnMount, Cleanup }

OnMount {
  val subscription = eventBus.subscribe(handler)
  Cleanup.register(() =&gt; subscription.cancel())
}</code></pre>

    <h2>Effect 内のクリーンアップ</h2>

    <p><code>Effect</code> ブロック内で <code>Cleanup.register</code> を使うと、
    エフェクトが再実行される直前と、コンポーネントのデストロイ時に呼ばれます。</p>

    <pre><code>import melt.runtime.{ Effect, Cleanup }

val id = State[Option[Int]](None)

Effect {
  id.value.foreach { currentId =&gt;
    val ws = new WebSocket(s"wss://api.example.com/feed/$currentId")
    Cleanup.register(() =&gt; ws.close())
  }
}</code></pre>

    <h2>Lifecycle.destroyTree()</h2>

    <p>コンポーネントツリーを手動でデストロイしたい場合は
    <code>Lifecycle.destroyTree(root)</code> を呼び出します。
    通常は Melt が内部的に管理するため、明示的に呼ぶ機会は少ないです。</p>

    <div class="callout callout-warn">
      <div class="callout-title">注意事項</div>
      <p>OnMount は非同期処理の完了を待ちません。非同期処理が必要な場合は
      OnMount 内で Future や Promise を扱い、完了後に State を更新してください。</p>
    </div>
  """

  // ── Control Flow ──────────────────────────────────────────────────────────

  private val controlFlow = """
    <p class="doc-lead">Melt テンプレートの制御フローは Scala 式を直接使います。
    特殊な <code>#if</code> や <code>#each</code> ディレクティブはありません。
    <code>{}</code> の中に Scala を書き、その中に HTML 要素を埋め込む形式です。</p>

    <h2>条件付きレンダリング</h2>

    <p>Scala の <code>if</code> 式を使います。リアクティブにするには <code>Signal</code> を <code>.map()</code> します。</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>リアクティブな条件分岐
      </div>
      <pre><code>&lt;script lang="scala"&gt;
  val loggedIn = State(false)
&lt;/script&gt;

{loggedIn.map { logged =&gt;
  if logged then
    &lt;p&gt;ようこそ！&lt;/p&gt;
  else
    &lt;a href="/login"&gt;ログイン&lt;/a&gt;
}}</code></pre>
    </div>

    <div class="callout callout-info">
      <div class="callout-title">.map() が必要な理由</div>
      <p>テンプレート式で <code>loggedIn.value</code> を直接読むと、その時点の値を一度だけ読み取るだけで
      その後の変化には追従しません。<code>.map()</code> でラップすることで、
      値が変わるたびに DOM が自動更新されるリアクティブなサブスクリプションになります。</p>
    </div>

    <h2>リスト描画</h2>

    <p>Scala の <code>.map()</code> を <code>State[List[_]]</code> や
    <code>Signal[List[_]]</code> に使ってリストをレンダリングします。</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>リスト描画
      </div>
      <pre><code>&lt;script lang="scala"&gt;
  case class Item(id: Int, name: String)
  val items = State(List(Item(1, "りんご"), Item(2, "バナナ")))
&lt;/script&gt;

&lt;ul&gt;
  {items.map(_.map((item: Item) =&gt;
    &lt;li&gt;{item.id}: {item.name}&lt;/li&gt;
  ))}
&lt;/ul&gt;</code></pre>
    </div>

    <h2>キー付きリスト</h2>

    <p>リスト要素に <code>key</code> 属性を付けると、Melt は要素の追加・削除・並べ替え時に
    既存 DOM ノードを再利用して効率よく更新できます。</p>

    <pre><code>{items.map(_.map((item: Item) =&gt;
  &lt;li key={item.id.toString}&gt;{item.name}&lt;/li&gt;
))}</code></pre>

    <h2>melt:key ブロック</h2>

    <p>キー式が変わるとサブツリーを完全に破棄・再作成したい場合は
    <code>&lt;melt:key&gt;</code> 要素を使います。
    コンポーネントの状態をリセットするのに便利です。</p>

    <pre><code>&lt;melt:key this={selectedId}&gt;
  &lt;DetailPanel id={selectedId} /&gt;
&lt;/melt:key&gt;</code></pre>

    <p><code>selectedId</code> が変わるたびに <code>DetailPanel</code> が
    アンマウントされ、初期状態でマウントし直されます。</p>

    <h2>空の状態を扱う</h2>

    <p>リストが空の場合のフォールバック表示も簡単に書けます。</p>
    <pre><code>{items.map { list =&gt;
  if list.isEmpty then
    &lt;p class="empty"&gt;アイテムがありません。&lt;/p&gt;
  else
    &lt;ul&gt;{list.map((item: Item) =&gt; &lt;li&gt;{item.name}&lt;/li&gt;)}&lt;/ul&gt;
}}</code></pre>
  """

  // ── Special Elements ──────────────────────────────────────────────────────

  private val specialElements = """
    <p class="doc-lead">Melt は <code>melt:</code> 名前空間の下に、標準 HTML を超えた
    よくあるパターン向けの特殊組み込み要素を提供しています。</p>

    <h2>&lt;melt:head&gt;</h2>

    <p>任意のコンポーネントからページの <code>&lt;head&gt;</code> にコンテンツを差し込めます。
    タイトルやメタタグの動的設定に使います。</p>
    <pre><code>&lt;melt:head&gt;
  &lt;title&gt;{pageTitle}&lt;/title&gt;
  &lt;meta name="description" content={description} /&gt;
  &lt;link rel="canonical" href={canonicalUrl} /&gt;
&lt;/melt:head&gt;</code></pre>

    <h2>&lt;melt:window&gt; / &lt;melt:body&gt;</h2>

    <p><code>addEventListener</code> を手動で呼ばずにグローバルイベントリスナーを設定できます。
    コンポーネントがアンマウントされると自動的に削除されます。</p>
    <pre><code>&lt;melt:window
  onkeydown={e =&gt; handleKey(e)}
  onresize={_ =&gt; recalcLayout()}
/&gt;

&lt;melt:body
  onmousedown={e =&gt; trackClick(e)}
/&gt;</code></pre>

    <h2>&lt;melt:boundary&gt;</h2>

    <p>サブツリーをエラーバウンダリでラップして、レンダリングエラーをキャッチし
    フォールバック UI を表示します。非同期コンポーネントの pending/failed 状態にも対応します。</p>
    <pre><code>&lt;melt:boundary&gt;
  &lt;melt:pending&gt;
    &lt;p&gt;読み込み中...&lt;/p&gt;
  &lt;/melt:pending&gt;
  &lt;melt:failed let:error&gt;
    &lt;p&gt;エラー: {error.message}&lt;/p&gt;
  &lt;/melt:failed&gt;
  &lt;AsyncComponent /&gt;
&lt;/melt:boundary&gt;</code></pre>

    <h2>&lt;melt:element&gt;</h2>

    <p>実行時に動的なタグ名をレンダリングします。
    見出しレベル (<code>h1</code>〜<code>h6</code>) の動的変更などに便利です。</p>
    <pre><code>&lt;script lang="scala"&gt;
  val tag = State("div")
&lt;/script&gt;

&lt;melt:element this={tag} class="wrapper"&gt;
  {children}
&lt;/melt:element&gt;</code></pre>

    <h2>&lt;melt:document&gt;</h2>

    <p>ドキュメントレベルのイベントリスナーを設定します。
    <code>&lt;melt:window&gt;</code> と似ていますが、<code>document</code> オブジェクトに設定されます。</p>
    <pre><code>&lt;melt:document
  onvisibilitychange={_ =&gt; handleVisibilityChange()}
/&gt;</code></pre>

    <h2>スニペットと render</h2>

    <p>再利用可能なテンプレートフラグメントを <code>{#snippet}</code> で定義し、
    <code>{@render}</code> で呼び出せます。</p>
    <pre><code>{#snippet badge(label: String)}
  &lt;span class="badge"&gt;{label}&lt;/span&gt;
{/snippet}

&lt;div&gt;
  {@render badge("新着")}
  {@render badge("人気")}
&lt;/div&gt;</code></pre>

    <table class="api-table">
      <thead><tr><th>要素</th><th>マウント先</th><th>主な用途</th></tr></thead>
      <tbody>
        <tr><td><code>&lt;melt:head&gt;</code></td><td><code>document.head</code></td><td>タイトル・メタタグ</td></tr>
        <tr><td><code>&lt;melt:window&gt;</code></td><td><code>window</code></td><td>グローバルイベント</td></tr>
        <tr><td><code>&lt;melt:body&gt;</code></td><td><code>document.body</code></td><td>ボディイベント</td></tr>
        <tr><td><code>&lt;melt:document&gt;</code></td><td><code>document</code></td><td>ドキュメントイベント</td></tr>
        <tr><td><code>&lt;melt:boundary&gt;</code></td><td>インライン</td><td>エラー・非同期境界</td></tr>
        <tr><td><code>&lt;melt:element&gt;</code></td><td>インライン</td><td>動的タグ名</td></tr>
      </tbody>
    </table>
  """

  // ── Transitions ───────────────────────────────────────────────────────────

  private val transitions = """
    <p class="doc-lead">Melt は値の変化をなめらかにアニメーションする
    <code>Tween</code>・<code>Spring</code> と、CSS ベースのトランジションをサポートします。</p>

    <h2>Tween — 数値を時間補間する</h2>

    <p>数値をスムーズに変化させるには <code>Tween</code> を使います。
    値が変わると、設定した時間をかけて目標値に向かってアニメーションします。</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>Tween の例
      </div>
      <pre><code>&lt;script lang="scala"&gt;
  import melt.runtime.transition.Tween

  val target  = State(0.0)
  val display = Tween(target, duration = 400) // 400ms かけてアニメーション
&lt;/script&gt;

&lt;div style:opacity={display}&gt;コンテンツ&lt;/div&gt;
&lt;button onclick={_ =&gt; target.set(1.0)}&gt;フェードイン&lt;/button&gt;
&lt;button onclick={_ =&gt; target.set(0.0)}&gt;フェードアウト&lt;/button&gt;</code></pre>
    </div>

    <h2>Spring — 物理ベースのアニメーション</h2>

    <p>自然な動きを実現したい場合は物理ベースのバネモデル <code>Spring</code> を使います。</p>
    <pre><code>import melt.runtime.transition.Spring

val x      = State(0.0)
val smooth = Spring(x, stiffness = 0.15, damping = 0.8)</code></pre>

    <table class="api-table">
      <thead><tr><th>オプション</th><th>デフォルト</th><th>説明</th></tr></thead>
      <tbody>
        <tr><td><code>stiffness</code></td><td>0.15</td><td>バネの硬さ — 目標値に近づく速さ</td></tr>
        <tr><td><code>damping</code></td><td>0.8</td><td>減衰係数 — 振動の収まる速さ (1.0 = 振動なし)</td></tr>
        <tr><td><code>precision</code></td><td>0.001</td><td>動きが止まるとみなす距離</td></tr>
      </tbody>
    </table>

    <h2>CSS トランジション</h2>

    <p>クラスベースのトランジションには <code>class:</code> ディレクティブと
    CSS の <code>transition</code> プロパティを組み合わせます。</p>
    <pre><code>&lt;!-- テンプレート --&gt;
&lt;div class="panel" class:open={isOpen}&gt;
  {children}
&lt;/div&gt;

&lt;!-- スタイル --&gt;
&lt;style&gt;
  .panel {
    max-height: 0;
    overflow: hidden;
    transition: max-height 0.3s ease;
  }
  .panel.open { max-height: 500px; }
&lt;/style&gt;</code></pre>

    <h2>in: / out: で入退場を個別指定</h2>

    <p>要素の表示・非表示に異なるトランジションを指定したい場合は、
    <code>in:</code> と <code>out:</code> ディレクティブを使います。</p>
    <pre><code>&lt;div in:fade out:slide&gt;
  コンテンツ
&lt;/div&gt;</code></pre>

    <div class="callout callout-tip">
      <div class="callout-title">パフォーマンスのヒント</div>
      <p>アニメーションには <code>opacity</code> や <code>transform</code> のような
      GPU でアクセラレーションされるプロパティを優先して使うと、
      スムーズな 60fps アニメーションが実現しやすくなります。</p>
    </div>
  """

  // ── Trusted HTML ──────────────────────────────────────────────────────────

  private val trustedHtml = """
    <p class="doc-lead">Melt はデフォルトですべての動的コンテンツをエスケープして XSS 攻撃を防ぎます。
    生の HTML を挿入する必要がある場合は、コンテンツを確認済みであることを示す
    <code>TrustedHtml</code> でラップします。</p>

    <h2>なぜデフォルトでエスケープするのか</h2>

    <p>次の例を見てください。</p>
    <pre><code>val userInput = "&lt;script&gt;alert('xss')&lt;/script&gt;"
// ...
&lt;p&gt;{userInput}&lt;/p&gt;
// レンダリング結果: &lt;p&gt;&amp;lt;script&amp;gt;...&lt;/p&gt;  ← 安全</code></pre>

    <p>テンプレートコンパイラは動的な文字列値に自動的に <code>Escape.html</code> を適用します。
    生の HTML を誤ってレンダリングすることはできない設計になっています。</p>

    <h2>TrustedHtml.unsafe</h2>

    <p>自分がコントロールしている HTML — 静的文字列や信頼済みの CMS コンテンツ —
    には <code>TrustedHtml.unsafe</code> を使います。</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>使用例
      </div>
      <pre><code>&lt;script lang="scala"&gt;
  import melt.runtime.TrustedHtml

  // 信頼できるコンテンツにのみ使用！
  val richContent = TrustedHtml.unsafe("&lt;strong&gt;太字&lt;/strong&gt;テキスト")
&lt;/script&gt;

&lt;div bind:html={richContent}&gt;&lt;/div&gt;</code></pre>
    </div>

    <div class="callout callout-warn">
      <div class="callout-title">ユーザー入力には絶対に使わない</div>
      <p>ユーザーが入力した信頼できないコンテンツを <code>TrustedHtml.unsafe</code> に
      渡さないでください。サニタイザーライブラリで処理してからラップしてください。</p>
    </div>

    <h2>TrustedHtml.sanitize</h2>

    <p>ユーザー生成コンテンツには、サニタイザー関数を受け取る <code>TrustedHtml.sanitize</code> を使います。</p>
    <pre><code>import melt.runtime.TrustedHtml

val safeHtml = TrustedHtml.sanitize(
  userMarkdown,
  html =&gt; mySanitizer.clean(html)  // 独自のサニタイザー
)</code></pre>

    <h2>TrustedUrl</h2>

    <p>Melt は <code>href</code> や <code>src</code> などの URL 属性も検証します。
    動的な値には <code>TrustedUrl</code> を使います。</p>
    <pre><code>import melt.runtime.TrustedUrl

val link = TrustedUrl.unsafe("https://example.com")
// ...
&lt;a href={link}&gt;サイトを訪問する&lt;/a&gt;</code></pre>

    <p>ラップなしで渡すと、危険なプロトコル（<code>javascript:</code>、
    <code>vbscript:</code>、<code>data:text/html</code>）はコンパイル時にブロックされます。</p>

    <h2>セキュリティチェック一覧</h2>

    <table class="api-table">
      <thead><tr><th>対象</th><th>レベル</th><th>説明</th></tr></thead>
      <tbody>
        <tr><td><code>&lt;iframe srcdoc={...}&gt;</code></td><td>エラー</td><td>動的 srcdoc は禁止</td></tr>
        <tr><td><code>&lt;iframe src={...}&gt;</code></td><td>警告</td><td>信頼済み URL を使用すること</td></tr>
        <tr><td><code>&lt;a target="_blank"&gt;</code></td><td>警告</td><td><code>rel="noopener"</code> を推奨</td></tr>
        <tr><td>文字列の動的出力</td><td>自動エスケープ</td><td><code>Escape.html</code> が適用される</td></tr>
      </tbody>
    </table>
  """

  // ── CSS ───────────────────────────────────────────────────────────────────

  private val css = """
    <p class="doc-lead">Melt の CSS はデフォルトでコンポーネントにスコープされます。
    グローバルスタイル・CSS カスタムプロパティ・SCSS も利用できます。</p>

    <h2>スコープ付きスタイル</h2>

    <p>コンポーネントの <code>&lt;style&gt;</code> ブロックに書かれた CSS は自動的にスコープされます。
    コンパイラが各要素に一意の属性を付与し、すべてのルールにプレフィックスを追加します。</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>Card.melt
      </div>
      <pre><code>&lt;div class="card"&gt;&lt;p&gt;コンテンツ&lt;/p&gt;&lt;/div&gt;

&lt;style&gt;
  /* このコンポーネントの要素にのみ適用される */
  .card { border: 1px solid #ccc; }
  p     { color: grey; }
&lt;/style&gt;</code></pre>
    </div>

    <p>生成される HTML（簡略化）</p>
    <pre><code>&lt;div class="card" data-melt-abc123&gt;
  &lt;p data-melt-abc123&gt;コンテンツ&lt;/p&gt;
&lt;/div&gt;</code></pre>

    <h2>:global() でグローバルスタイル</h2>

    <p>スコープを外して特定のルールをグローバルに適用したい場合は <code>:global()</code> を使います。</p>
    <pre><code>&lt;style&gt;
  /* このコンポーネント内のすべての a タグ (子コンポーネントを含む) */
  :global(a) { color: #d6526a; }

  /* スコープされたセレクタとの組み合わせ */
  .container :global(.external-lib) { margin: 0; }
&lt;/style&gt;</code></pre>

    <h2>動的スタイル</h2>

    <p><code>style:</code> ディレクティブでリアクティブなインラインスタイルを設定します。</p>
    <pre><code>&lt;script lang="scala"&gt;
  val hue = State(200)
&lt;/script&gt;

&lt;div style:background-color={"hsl(" + hue + ", 60%, 50%)"}&gt;&lt;/div&gt;
&lt;input type="range" bind:value={hue} min="0" max="360" /&gt;</code></pre>

    <h2>CSS カスタムプロパティ（変数）</h2>

    <p>CSS 変数を使ってリアクティブな値を CSS に渡せます。</p>
    <pre><code>&lt;script lang="scala"&gt;
  val progress = State(0.0)
&lt;/script&gt;

&lt;div class="bar" style:--progress={progress + "%"}&gt;&lt;/div&gt;

&lt;style&gt;
  .bar::before {
    width: var(--progress);
    background: var(--accent);
    transition: width 0.3s ease;
  }
&lt;/style&gt;</code></pre>

    <h2>SCSS サポート</h2>

    <p>style ブロックに <code>lang="scss"</code> を追加し、sbt の設定で SCSS プリプロセッサを有効化します。</p>
    <pre><code>&lt;style lang="scss"&gt;
  $primary: #d6526a;

  .card {
    &amp;:hover { background: lighten($primary, 40%); }
    &amp;__title { color: $primary; }
    &amp;__body { font-size: 0.9rem; }
  }
&lt;/style&gt;</code></pre>

    <div class="callout callout-info">
      <div class="callout-title">SCSS には Dart Sass が必要です</div>
      <p><code>melt-compiler-sass</code> モジュールが Dart Sass をラップしています。
      JVM のクラスパスに追加し、sbt の設定に <code>meltStylePreprocessor := Some(SassPreprocessor)</code> を
      追記してください。</p>
    </div>

    <h2>CSS Nesting</h2>

    <p>Melt の CSS パーサーは CSS Nesting 仕様をサポートしています。
    SCSS なしでも入れ子のルールが書けます。</p>
    <pre><code>&lt;style&gt;
  .menu {
    list-style: none;

    &amp; li {
      padding: 8px 16px;

      &amp;:hover { background: #f5f5f5; }
    }
  }
&lt;/style&gt;</code></pre>
  """

  // ── Testing ───────────────────────────────────────────────────────────────

  private val testing = """
    <p class="doc-lead">Melt は <code>melt-testkit</code> モジュールを提供しており、
    シミュレートされた DOM 環境でコンポーネントをマウントしてレンダリング結果をアサートできます。</p>

    <h2>セットアップ</h2>

    <p>テスト設定に依存関係を追加します。</p>
    <pre><code>libraryDependencies += "io.github.takapi327" %%% "melt-testkit" % "0.1.0-SNAPSHOT" % Test</code></pre>

    <h2>テストを書く</h2>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>CounterSpec.scala
      </div>
      <pre><code>import melt.testkit.*

class CounterSpec extends MeltSuite:

  test("カウンターはゼロから始まる") {
    val env = MeltEnv.render(Counter(Counter.Props()))
    assertEquals(env.text("h1"), "0")
  }

  test("増やすボタンでカウントが増える") {
    val env = MeltEnv.render(Counter(Counter.Props()))
    env.click("button:first-child")
    assertEquals(env.text("h1"), "1")
  }

  test("リセットでゼロに戻る") {
    val env = MeltEnv.render(Counter(Counter.Props()))
    env.click("button:first-child")
    env.click("button:last-child")
    assertEquals(env.text("h1"), "0")
  }</code></pre>
    </div>

    <h2>MeltEnv API</h2>

    <table class="api-table">
      <thead><tr><th>メソッド</th><th>説明</th></tr></thead>
      <tbody>
        <tr><td><code>MeltEnv.render(component)</code></td><td>コンポーネントをマウントしてテスト環境を返す</td></tr>
        <tr><td><code>env.text(selector)</code></td><td>マッチした要素のテキストコンテンツを取得</td></tr>
        <tr><td><code>env.click(selector)</code></td><td>マッチした要素のクリックをシミュレート</td></tr>
        <tr><td><code>env.input(selector, value)</code></td><td>input に値を入力</td></tr>
        <tr><td><code>env.query(selector)</code></td><td>要素を検索 (<code>Option[Element]</code>)</td></tr>
        <tr><td><code>env.queryAll(selector)</code></td><td>マッチするすべての要素を返す</td></tr>
      </tbody>
    </table>

    <h2>リアクティブな状態のアサーション</h2>

    <p>State を直接変更してレンダリング結果を確認することもできます。</p>
    <pre><code>test("Props の変化が反映される") {
  val env   = MeltEnv.render(Greeting(Greeting.Props(name = "Alice")))
  assertEquals(env.text("p"), "こんにちは、Alice！")
}</code></pre>

    <h2>イベントのシミュレーション</h2>

    <pre><code>test("テキスト入力が State を更新する") {
  val env = MeltEnv.render(NameInput(NameInput.Props()))
  env.input("input", "Bob")
  assertEquals(env.text(".preview"), "Bob")
}</code></pre>

    <div class="callout callout-tip">
      <div class="callout-title">テストは JVM で実行される</div>
      <p>testkit は JVM 上で動作するため、ブラウザなしで高速にテストできます。
      DOM 操作のシミュレーションは testkit が内部的に処理します。</p>
    </div>
  """

  // ── Routing ───────────────────────────────────────────────────────────────

  private def routing(base: String) = s"""
    <p class="doc-lead">MeltKit はフルスタックの Melt アプリケーション向けに
    型安全なルーティング DSL を提供します。ルートは Scala で宣言され、
    コンパイル時に検査され、サーバー (SSR) またはクライアント (SPA) でレンダリングされます。</p>

    <h2>セットアップ</h2>

    <p>JVM モジュールに MeltKit を追加します。</p>
    <pre><code>// build.sbt
libraryDependencies += "io.github.takapi327" %% "meltkit-adapter-http4s" % "0.1.0-SNAPSHOT"</code></pre>

    <h2>ルートを定義する</h2>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>Main.scala
      </div>
      <pre><code>import meltkit.*
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

val app = MeltKit[Future]()

// 静的ルート
app.get("") { ctx =&gt;
  Future.successful(ctx.render(Home(Home.Props())))
}

// パスパラメータを持つ動的ルート
private val id = param[String]("id")

app.get("users" / id) { ctx =&gt;
  val userId = ctx.params.id
  Future.successful(ctx.render(UserPage(UserPage.Props(id = userId))))
}</code></pre>
    </div>

    <h2>パスパラメータ</h2>

    <p><code>param[T]("name")</code> でパラメータを宣言し、<code>/</code> で連結します。</p>
    <pre><code>private val lang    = param[String]("lang")
private val section = param[String]("section")

app.get(lang / "guide" / section) { ctx =&gt;
  val l = ctx.params.lang
  val s = ctx.params.section
  Future.successful(ctx.render(GuidePage(GuidePage.Props(lang = l, slug = s))))
}</code></pre>

    <p>パラメータの型は Scala の型システムで検査されます。
    <code>param[Int]("page")</code> と宣言すれば、
    <code>ctx.params.page</code> は <code>Int</code> として型安全に取得できます。</p>

    <h2>ctx でレスポンスを構築する</h2>

    <table class="api-table">
      <thead><tr><th>メソッド</th><th>説明</th></tr></thead>
      <tbody>
        <tr><td><code>ctx.render(component)</code></td><td>コンポーネントを HTML にレンダリングしてレスポンスを返す</td></tr>
        <tr><td><code>ctx.html(content)</code></td><td>生の HTML 文字列でレスポンスを返す</td></tr>
        <tr><td><code>ctx.params</code></td><td>パスパラメータへのアクセス</td></tr>
        <tr><td><code>ctx.query</code></td><td>クエリパラメータへのアクセス</td></tr>
        <tr><td><code>ctx.locals</code></td><td>リクエストスコープのストレージ</td></tr>
      </tbody>
    </table>

    <h2>PageOptions</h2>

    <p>ルートごとに SSR・CSR・プリレンダリングを制御します。</p>
    <pre><code>val opts = PageOptions(
  ssr       = true,                    // サーバーでレンダリング
  csr       = true,                    // クライアントで hydration
  prerender = PrerenderOption.On,      // ビルド時に生成
  entries   = List("/en/guide/introduction", "/ja/guide/introduction")
)

app.get(lang / "guide" / slug, opts) { ctx =&gt; ... }</code></pre>

    <div class="callout callout-info">
      <div class="callout-title">詳細は SSR/SSG ガイドを参照</div>
      <p>SSR の仕組みについては <a href="$base/guide/ssr">サーバーサイドレンダリング</a>、
      静的ページ生成については <a href="$base/guide/ssg">静的サイト生成</a> を参照してください。</p>
    </div>
  """

  // ── SSR ───────────────────────────────────────────────────────────────────

  private val ssr = """
    <p class="doc-lead">サーバーサイドレンダリング (SSR) は Melt コンポーネントを JVM でレンダリングして
    HTML をブラウザに送ります。その後クライアント側でハイドレーションが行われ、
    静的な HTML に対してイベントリスナーが設定されてインタラクティブになります。</p>

    <h2>仕組み</h2>

    <ol>
      <li>サーバーがリクエストを受け取る</li>
      <li>MeltKit が JVM 上でマッチするコンポーネントを HTML 文字列にレンダリング</li>
      <li>ハイドレーションマーカーを埋め込んだ HTML をブラウザに送信</li>
      <li>ブラウザで Scala.js バンドルがハイドレーション:
          既存の DOM ノードを再利用してリアクティビティを設定</li>
    </ol>

    <h2>SSR を有効にする</h2>

    <p><code>sbt-meltkit</code> プラグインを使ってコードジェネレーションモードを設定します。</p>
    <pre><code>// build.sbt (サーバーモジュール)
lazy val server = project
  .enablePlugins(MeltkitPlugin)
  .settings(meltMode := "ssr")

// build.sbt (クライアントモジュール — ハイドレーション用)
lazy val client = project
  .enablePlugins(MeltkitPlugin)
  .settings(meltMode := "spa")</code></pre>

    <h2>ルート設定</h2>

    <pre><code>app.get("blog" / slug, PageOptions(ssr = true, csr = true)) { ctx =&gt;
  fetchPost(ctx.params.slug).map { post =&gt;
    ctx.render(BlogPost(BlogPost.Props(post = post)))
  }
}</code></pre>

    <h2>Props のシリアライズ</h2>

    <p>ハイドレーションを機能させるために、Props はサーバーで JSON にシリアライズされ、
    クライアントでデシリアライズされます。ケースクラスに対しては自動的にコーデックが導出されます。</p>
    <pre><code>case class Props(title: String, count: Int)
// シンプルな型のケースクラスはコーデックが自動導出される</code></pre>

    <h2>Vite の設定</h2>

    <p>SSR + ハイドレーションのプロダクションビルドでは、Rollup のデフォルト設定が
    named export (<code>hydrate</code>) を削除してしまうことがあります。
    <code>vite.config.mjs</code> に以下を追加してください。</p>
    <pre><code>// vite.config.mjs
export default defineConfig({
  build: {
    rollupOptions: {
      preserveEntrySignatures: 'exports-only'  // hydrate を保持
    }
  }
})</code></pre>

    <div class="callout callout-tip">
      <div class="callout-title">部分ハイドレーション</div>
      <p><code>csr = false</code> を設定すると、クライアント側の JavaScript を
      一切使わない純粋な静的 HTML としてコンポーネントをレンダリングできます。</p>
    </div>

    <h2>SSR と SPA の違い</h2>

    <table class="api-table">
      <thead><tr><th>項目</th><th>SPA</th><th>SSR</th></tr></thead>
      <tbody>
        <tr><td>初期表示</td><td>JS 実行後</td><td>即座（HTML 直接）</td></tr>
        <tr><td>SEO</td><td>困難</td><td>有利</td></tr>
        <tr><td>インタラクティブ</td><td>即座</td><td>ハイドレーション後</td></tr>
        <tr><td>サーバー要件</td><td>静的サーバー可</td><td>JVM/Node.js サーバー必要</td></tr>
      </tbody>
    </table>
  """

  // ── SSG ───────────────────────────────────────────────────────────────────

  private val ssg = """
    <p class="doc-lead">静的サイト生成 (SSG) はビルド時にすべてのページをプリレンダリングして
    プレーンな HTML ファイルのディレクトリを出力します。
    サーバーインフラなしで CDN から配信できます。</p>

    <h2>プリレンダリングを有効にする</h2>

    <p>ルートに <code>prerender = PrerenderOption.On</code> を設定し、
    生成するすべての URL エントリを指定します。</p>

    <pre><code>private val langs  = List("en", "ja")
private val guides = List("introduction", "installation", ...)
private val On     = PageOptions(prerender = PrerenderOption.On)

app.get(
  lang / "guide" / slug,
  On.copy(entries = for l &lt;- langs; g &lt;- guides yield s"/$l/guide/$g")
) { ctx =&gt; ... }</code></pre>

    <h2>ジェネレーターを実行する</h2>

    <p><code>SsgGenerator.run</code> を呼ぶ <code>generate</code> メイン関数を作成します。</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>Main.scala
      </div>
      <pre><code>import meltkit.ssg.*

@main def generate(): Unit =
  val config = ServerConfig(
    outputDir = Some("dist"),
    publicDir = Some("public"),
    assetsDir = Some("../dist/assets"),
    manifest  = ViteManifest.fromFile("../dist/.vite/manifest.json"),
    template  = Template.fromResource("index.html")
  )
  SsgGenerator.run(app, config)</code></pre>
    </div>

    <p>sbt から実行します。</p>
    <pre><code>sbt "server/runMain generate"</code></pre>

    <h2>出力ディレクトリ構成</h2>
    <pre><code>dist/
├── index.html
├── en/
│   ├── guide/
│   │   ├── introduction/index.html
│   │   └── ...
│   └── ...
└── assets/
    ├── main.js
    └── main.css</code></pre>

    <h2>デプロイ方法</h2>

    <p>生成された <code>dist/</code> ディレクトリをそのまま任意の静的ホスティングサービスに
    アップロードするだけです。</p>

    <ul>
      <li><strong>GitHub Pages:</strong> <code>dist/</code> ブランチを公開</li>
      <li><strong>Netlify / Vercel:</strong> <code>dist</code> を publish directory に指定</li>
      <li><strong>Cloudflare Pages:</strong> <code>dist</code> ディレクトリを直接デプロイ</li>
      <li><strong>S3 + CloudFront:</strong> <code>dist/</code> を S3 にアップロードして CloudFront で配信</li>
    </ul>

    <div class="callout callout-tip">
      <div class="callout-title">動的ページも静的生成できる</div>
      <p><code>entries</code> に動的ページのすべての URL を列挙することで、
      パスパラメータを持つルートも静的生成できます。
      例えばブログ記事のスラッグ一覧をデータベースから取得して渡せます。</p>
    </div>
  """

  // ── Adapters ──────────────────────────────────────────────────────────────

  private val adapters = """
    <p class="doc-lead">MeltKit アダプターはアプリを特定のランタイム環境に接続します。
    デプロイターゲットに合ったアダプターを選択してください。</p>

    <h2>http4s アダプター（JVM）</h2>

    <p><code>meltkit-adapter-http4s</code> モジュールは MeltKit を http4s と統合し、
    JVM でのプロダクション運用を可能にします。</p>
    <pre><code>libraryDependencies += "io.github.takapi327" %% "meltkit-adapter-http4s" % "0.1.0-SNAPSHOT"</code></pre>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>http4s のセットアップ
      </div>
      <pre><code>import meltkit.adapter.http4s.Http4sAdapter
import org.http4s.ember.server.EmberServerBuilder
import cats.effect.IO

val routes = Http4sAdapter.toRoutes[IO](app, config)

EmberServerBuilder.default[IO]
  .withHttpApp(routes.orNotFound)
  .build
  .useForever</code></pre>
    </div>

    <h2>Node.js アダプター</h2>

    <p>Node.js にデプロイする場合は <code>meltkit-adapter-node</code> を使います。
    SSR と SSG の両方に対応しています。</p>
    <pre><code>libraryDependencies += "io.github.takapi327" %%% "meltkit-adapter-node" % "0.1.0-SNAPSHOT"</code></pre>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>Node.js のセットアップ
      </div>
      <pre><code>import meltkit.adapter.node.NodeAdapter

@main def server(): Unit =
  NodeAdapter.listen(app, config, port = 3000)</code></pre>
    </div>

    <h2>ブラウザ SPA アダプター</h2>

    <p>サーバーなしの純粋なクライアントサイド SPA には
    <code>meltkit-adapter-browser</code> を使います。
    クライアントサイドルーティングと履歴管理を担当します。</p>
    <pre><code>libraryDependencies += "io.github.takapi327" %%% "meltkit-adapter-browser" % "0.1.0-SNAPSHOT"</code></pre>

    <pre><code>import meltkit.adapter.browser.BrowserAdapter

@JSExportTopLevel("main")
def main(): Unit =
  BrowserAdapter.start(app)</code></pre>

    <h2>アダプター比較</h2>

    <table class="api-table">
      <thead>
        <tr>
          <th>アダプター</th><th>プラットフォーム</th><th>SSR</th><th>SSG</th><th>SPA</th>
        </tr>
      </thead>
      <tbody>
        <tr><td><code>meltkit-adapter-http4s</code></td><td>JVM</td><td>✓</td><td>✓</td><td>Vite 経由</td></tr>
        <tr><td><code>meltkit-adapter-node</code></td><td>Node.js</td><td>✓</td><td>✓</td><td>Vite 経由</td></tr>
        <tr><td><code>meltkit-adapter-browser</code></td><td>ブラウザ</td><td>—</td><td>—</td><td>✓</td></tr>
      </tbody>
    </table>

    <h2>アダプターの選び方</h2>

    <ul>
      <li>既に <strong>http4s</strong> を使っている → <code>meltkit-adapter-http4s</code></li>
      <li><strong>Node.js</strong> エコシステムで動かしたい → <code>meltkit-adapter-node</code></li>
      <li>サーバーなしの <strong>SPA</strong> を作りたい → <code>meltkit-adapter-browser</code></li>
      <li><strong>SSG</strong> のみで CDN 配信したい → http4s か Node.js のどちらでも可能</li>
    </ul>

    <div class="callout callout-tip">
      <div class="callout-title">複数アダプターの組み合わせ</div>
      <p>開発時は Node.js アダプターで素早く起動し、
      プロダクションでは http4s にデプロイするような
      環境別の切り替えも可能です。</p>
    </div>
  """
