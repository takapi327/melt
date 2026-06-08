/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package docs.content

import melt.runtime.TrustedHtml

object GuideContent:

  private val order = List(
    "introduction",
    "installation",
    "quick-start",
    "components",
    "template-syntax",
    "reactivity",
    "computed",
    "effects",
    "events",
    "lifecycle",
    "control-flow",
    "special-elements",
    "transitions",
    "trusted-html",
    "css",
    "testing",
    "routing",
    "ssr",
    "ssg",
    "adapters"
  )

  private val titles = Map(
    "introduction"     -> "What is Melt?",
    "installation"     -> "Installation",
    "quick-start"      -> "Quick Start",
    "components"       -> "Components",
    "template-syntax"  -> "Template Syntax",
    "reactivity"       -> "Reactivity",
    "computed"         -> "Computed Values",
    "effects"          -> "Effects",
    "events"           -> "Events",
    "lifecycle"        -> "Lifecycle",
    "control-flow"     -> "Control Flow",
    "special-elements" -> "Special Elements",
    "transitions"      -> "Transitions",
    "trusted-html"     -> "Trusted HTML",
    "css"              -> "CSS",
    "testing"          -> "Testing",
    "routing"          -> "Routing",
    "ssr"              -> "Server-Side Rendering",
    "ssg"              -> "Static Site Generation",
    "adapters"         -> "Adapters"
  )

  def title(slug: String): String = titles.getOrElse(slug, slug)

  private val titlesJa = Map(
    "introduction"     -> "Melt とは？",
    "installation"     -> "インストール",
    "quick-start"      -> "クイックスタート",
    "components"       -> "コンポーネント",
    "template-syntax"  -> "テンプレート構文",
    "reactivity"       -> "リアクティビティ",
    "computed"         -> "算出値",
    "effects"          -> "エフェクト",
    "events"           -> "イベント",
    "lifecycle"        -> "ライフサイクル",
    "control-flow"     -> "制御フロー",
    "special-elements" -> "特殊要素",
    "transitions"      -> "トランジション",
    "trusted-html"     -> "Trusted HTML",
    "css"              -> "CSS",
    "testing"          -> "テスト",
    "routing"          -> "ルーティング",
    "ssr"              -> "サーバーサイドレンダリング",
    "ssg"              -> "静的サイト生成",
    "adapters"         -> "アダプター"
  )

  def titleForLang(slug: String, lang: String): String =
    if lang == "ja" then titlesJa.getOrElse(slug, titles.getOrElse(slug, slug))
    else titles.getOrElse(slug, slug)

  def get(slug: String, basePath: String, lang: String): TrustedHtml =
    val base = s"$basePath/$lang"
    val idx  = order.indexOf(slug)
    val prev = if idx > 0 then Some(order(idx - 1)) else None
    val next = if idx >= 0 && idx < order.length - 1 then Some(order(idx + 1)) else None
    val html = (if lang == "ja" then GuideContentJa.content(slug, base) else content(slug, base)) +
      nav(prev, next, base, lang)
    TrustedHtml.unsafe(html)

  private def nav(prev: Option[String], next: Option[String], base: String, lang: String = "en"): String =
    val (prevLabel, nextLabel) = if lang == "ja" then ("← 前のページ", "次のページ →") else ("← Previous", "Next →")
    val prevLink               = prev
      .map(s => s"""<a class="doc-nav-prev" href="$base/guide/$s">
           <span class="doc-nav-dir">$prevLabel</span>
           <span class="doc-nav-title">${ titleForLang(s, lang) }</span>
         </a>""")
      .getOrElse("<span></span>")
    val nextLink = next
      .map(s => s"""<a class="doc-nav-next" href="$base/guide/$s">
           <span class="doc-nav-dir">$nextLabel</span>
           <span class="doc-nav-title">${ titleForLang(s, lang) }</span>
         </a>""")
      .getOrElse("<span></span>")
    s"""<div class="doc-nav">$prevLink$nextLink</div>"""

  private def content(slug: String, base: String): String = slug match
    case "introduction"     => introduction(base)
    case "installation"     => installation(base)
    case "quick-start"      => quickStart(base)
    case "components"       => components(base)
    case "template-syntax"  => templateSyntax(base)
    case "reactivity"       => reactivity(base)
    case "computed"         => computed(base)
    case "effects"          => effects(base)
    case "events"           => events(base)
    case "lifecycle"        => lifecycle(base)
    case "control-flow"     => controlFlow(base)
    case "special-elements" => specialElements(base)
    case "transitions"      => transitions(base)
    case "trusted-html"     => trustedHtml(base)
    case "css"              => css(base)
    case "testing"          => testing(base)
    case "routing"          => routing(base)
    case "ssr"              => ssr(base)
    case "ssg"              => ssg(base)
    case "adapters"         => adapters(base)
    case s                  => s"<p>Page <code>$s</code> coming soon.</p>"

  // ── Introduction ──────────────────────────────────────────────────────────

  private def introduction(base: String) = """
    <p>Melt is a <strong>Single File Component (SFC) framework for Scala.js</strong>
    inspired by Svelte. You write your logic, markup, and styles in a single
    <code>.melt</code> file, and the compiler turns it into efficient, direct DOM
    code — no virtual DOM, no runtime framework overhead.</p>

    <h2>Key ideas</h2>

    <p>Melt is built around three simple ideas:</p>
    <ul>
      <li><strong>The compiler does the work.</strong> Reactivity isn't a library you
      import — it's woven into the generated code by the Melt compiler.</li>
      <li><strong>Scala types check your templates.</strong> Every expression inside
      <code>{}</code> braces is real Scala, checked by scalac at compile time.</li>
      <li><strong>One source, three targets.</strong> The same <code>.melt</code> file
      compiles to SPA (Scala.js DOM code), SSR (JVM HTML string), or SSG (static
      HTML files).</li>
    </ul>

    <h2>A first look</h2>

    <p>Here is a complete interactive counter in Melt:</p>

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

    <p>In 15 lines you have:</p>
    <ul>
      <li>A mutable reactive cell (<code>State(0)</code>)</li>
      <li>A derived value that updates automatically (<code>count.map(_ * 2)</code>)</li>
      <li>Event handlers that mutate state (<code>count += 1</code>)</li>
      <li>Scoped CSS that only applies to this component</li>
    </ul>

    <h2>How it compiles</h2>

    <p>The Melt compiler reads your <code>.melt</code> file through this pipeline:</p>
    <ol>
      <li>Parse the <code>&lt;script&gt;</code>, template, and <code>&lt;style&gt;</code> sections</li>
      <li>Run semantic checks (type hints, a11y warnings, security checks)</li>
      <li>Lower the template AST to an internal IR</li>
      <li>Generate Scala code via the SPA or SSR emitter</li>
    </ol>

    <p>The output is a plain Scala object you compile with scalac/Scala.js as normal.
    There is no Melt runtime in the browser — just the tiny reactive primitives you
    actually use.</p>

    <div class="callout callout-tip">
      <div class="callout-title">Try it now</div>
      <p>Open the <a href="{base}/playground">Playground</a> to see live compilation
      in your browser.</p>
    </div>

    <h2>What Melt is not</h2>
    <ul>
      <li>It is not a full-stack meta-framework by itself (that role belongs to
      <strong>MeltKit</strong>, covered in the Server section).</li>
      <li>It does not ship a virtual DOM — updates are fine-grained and targeted.</li>
      <li>It does not require React, Vue, or any JS framework.</li>
    </ul>
  """.replace("{base}", base)

  // ── Installation ──────────────────────────────────────────────────────────

  private def installation(base: String) = s"""
    <p>Melt integrates into your existing sbt project via an sbt plugin. This page
    walks you through the required setup from scratch.</p>

    <h2>Prerequisites</h2>
    <ul>
      <li><strong>sbt 1.9+</strong></li>
      <li><strong>Scala 3.3.7+</strong> (for the compiler module; Scala 3.8+ for MeltKit)</li>
      <li><strong>Node.js 18+</strong> (for Vite dev server and bundling)</li>
      <li><strong>JDK 17+</strong></li>
    </ul>

    <h2>1 · Add the sbt plugin</h2>

    <p>Create or edit <code>project/plugins.sbt</code>:</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>project/plugins.sbt
      </div>
      <pre><code>addSbtPlugin("io.github.takapi327" % "sbt-meltc" % "0.1.0-SNAPSHOT")</code></pre>
    </div>

    <h2>2 · Configure build.sbt</h2>

    <p>Enable the plugin and add the Melt runtime dependency to your Scala.js module:</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>build.sbt
      </div>
      <pre><code>// Scala.js frontend module
lazy val client = project
  .in(file("client"))
  .enablePlugins(ScalaJSPlugin, MeltcPlugin)
  .settings(
    scalaVersion := "3.3.7",
    libraryDependencies += "io.github.takapi327" %%% "melt-runtime" % "0.1.0-SNAPSHOT"
  )</code></pre>
    </div>

    <h2>3 · Project structure</h2>

    <p>The plugin expects <code>.melt</code> files under
    <code>src/main/scala</code> (or any configured source directory). Generated
    Scala sources are placed in <code>target/scala-3.x.x/src_managed/main/melt/</code>
    and compiled automatically.</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>Typical layout
      </div>
      <pre><code>my-project/
├── build.sbt
├── project/
│   └── plugins.sbt
└── client/
    └── src/main/scala/
        └── myapp/
            ├── Counter.melt      ← your component
            └── App.melt</code></pre>
    </div>

    <h2>4 · Vite setup (optional)</h2>

    <p>Melt works with any bundler, but Vite is the recommended choice for development.
    Add a <code>vite.config.mjs</code> at your project root:</p>

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
      <div class="callout-title">SNAPSHOT releases</div>
      <p>Melt is currently in active development. Add the Sonatype snapshots
      resolver to <code>project/repositories</code> or <code>build.sbt</code>
      if dependencies are not found.</p>
    </div>
  """

  // ── Quick Start ───────────────────────────────────────────────────────────

  private def quickStart(base: String) = s"""
    <p>This guide builds a reactive counter from zero to running in under 5 minutes.</p>

    <div class="steps">
      <div class="step">
        <div class="step-num">1</div>
        <div class="step-body">
          <h3>Create the project</h3>
          <p>Start from the Melt counter example or create a minimal sbt project
          with the plugin enabled (see <a href="$base/guide/installation">Installation</a>).</p>
        </div>
      </div>

      <div class="step">
        <div class="step-num">2</div>
        <div class="step-body">
          <h3>Write your first component</h3>
          <p>Create <code>src/main/scala/Counter.melt</code>:</p>
          <div class="code-block">
            <div class="code-block-header">
              <span class="code-block-dot"></span>Counter.melt
            </div>
            <pre><code>&lt;script lang="scala"&gt;
  val count = State(0)
&lt;/script&gt;

&lt;div&gt;
  &lt;p&gt;Count: {count}&lt;/p&gt;
  &lt;button onclick={_ =&gt; count += 1}&gt;Increment&lt;/button&gt;
  &lt;button onclick={_ =&gt; count.set(0)}&gt;Reset&lt;/button&gt;
&lt;/div&gt;</code></pre>
          </div>
        </div>
      </div>

      <div class="step">
        <div class="step-num">3</div>
        <div class="step-body">
          <h3>Mount the component</h3>
          <p>Create a Scala.js entry point that mounts the component into the DOM:</p>
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
          <h3>Create index.html</h3>
          <div class="code-block">
            <div class="code-block-header">
              <span class="code-block-dot"></span>index.html
            </div>
            <pre><code>&lt;!DOCTYPE html&gt;
&lt;html lang="en"&gt;
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
          <h3>Run it</h3>
          <pre><code># Compile with sbt
sbt client/fastLinkJS

# Serve with Vite (or any static server)
npx vite</code></pre>
          <p>Open <code>http://localhost:5173</code> and click the button — the counter
          updates instantly without a page reload.</p>
        </div>
      </div>
    </div>

    <div class="callout callout-tip">
      <div class="callout-title">No-setup option</div>
      <p>Use the <a href="$base/playground">Playground</a> to experiment with Melt
      directly in the browser — no install required.</p>
    </div>
  """

  // ── Components ────────────────────────────────────────────────────────────

  private def components(base: String) = s"""
    <p>A Melt component is a <code>.melt</code> file with up to three sections:
    <code>&lt;script&gt;</code>, the template, and <code>&lt;style&gt;</code>.
    Together they describe the logic, markup, and appearance of a UI piece.</p>

    <h2>File structure</h2>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>Greeting.melt
      </div>
      <pre><code>&lt;!-- 1. Script section: Scala logic --&gt;
&lt;script lang="scala"&gt;
  case class Props(name: String = "World")
  val greeting = "Hello"
&lt;/script&gt;

&lt;!-- 2. Template: HTML with embedded expressions --&gt;
&lt;p class="msg"&gt;{greeting}, {props.name}!&lt;/p&gt;

&lt;!-- 3. Style section: scoped CSS --&gt;
&lt;style&gt;
  .msg { font-size: 1.25rem; color: #d6526a; }
&lt;/style&gt;</code></pre>
    </div>

    <h2>Props</h2>

    <p>Define component inputs with a <code>case class Props</code> inside the
    <code>&lt;script&gt;</code> block. Default values make all props optional:</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>Button.melt
      </div>
      <pre><code>&lt;script lang="scala"&gt;
  case class Props(
    label:    String  = "Click me",
    disabled: Boolean = false
  )
&lt;/script&gt;

&lt;button disabled={props.disabled}&gt;{props.label}&lt;/button&gt;</code></pre>
    </div>

    <p>Access props anywhere in the script and template via <code>props</code>:</p>
    <pre><code>props.label    // String
props.disabled // Boolean</code></pre>

    <h2>Using a component</h2>

    <p>Import and use components like HTML elements with a capital first letter:</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>App.melt
      </div>
      <pre><code>&lt;script lang="scala"&gt;
  import myapp.Button
&lt;/script&gt;

&lt;div&gt;
  &lt;Button label="Save" /&gt;
  &lt;Button label="Cancel" disabled={true} /&gt;
&lt;/div&gt;</code></pre>
    </div>

    <h2>Children (slot)</h2>

    <p>Use the built-in <code>children</code> value to render nested content:</p>

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

    <pre><code>&lt;!-- Usage --&gt;
&lt;Card&gt;
  &lt;h2&gt;Title&lt;/h2&gt;
  &lt;p&gt;Any content here.&lt;/p&gt;
&lt;/Card&gt;</code></pre>

    <h2>Scoped styles</h2>

    <p>CSS written in a component's <code>&lt;style&gt;</code> block is automatically
    scoped to that component. A unique attribute is added to rendered elements so
    styles never leak to children or siblings.</p>

    <div class="callout callout-info">
      <div class="callout-title">Note</div>
      <p>To apply styles globally, see the <a href="$base/guide/css">CSS guide</a>.</p>
    </div>
  """

  // ── Template Syntax ───────────────────────────────────────────────────────

  private def templateSyntax(base: String) = s"""
    <p>The Melt template is standard HTML enriched with Scala expressions,
    directives, and event handlers. Everything inside <code>{"{}"}</code> is
    evaluated as Scala.</p>

    <h2>Expressions</h2>

    <p>Embed any Scala expression inside <code>{"{}"}</code> in your template:</p>
    <pre><code>&lt;p&gt;{count}&lt;/p&gt;
&lt;p&gt;{count.map(_ * 2)}&lt;/p&gt;
&lt;p&gt;{"Hello, " + props.name + "!"}&lt;/p&gt;</code></pre>

    <p>Expressions that evaluate to a <code>Signal[A]</code> or <code>State[A]</code>
    are automatically subscribed — the DOM updates whenever the value changes.</p>

    <h2>Attribute binding</h2>

    <p>Use <code>attr={expr}</code> for dynamic attribute values:</p>
    <pre><code>&lt;img src={imageUrl} alt={props.alt} /&gt;
&lt;input type="text" placeholder={hint} /&gt;</code></pre>

    <h2>Two-way binding</h2>

    <p><code>bind:value</code> creates a two-way link between a <code>State[String]</code>
    and an input element:</p>
    <pre><code>&lt;script lang="scala"&gt;
  val name = State("")
&lt;/script&gt;

&lt;input type="text" bind:value={name} placeholder="Your name" /&gt;
&lt;p&gt;Hello, {name}!&lt;/p&gt;</code></pre>

    <table class="api-table">
      <thead><tr><th>Directive</th><th>Targets</th><th>Description</th></tr></thead>
      <tbody>
        <tr><td><code>bind:value</code></td><td>text, textarea, select</td><td>Two-way string binding</td></tr>
        <tr><td><code>bind:checked</code></td><td>checkbox</td><td>Two-way boolean binding</td></tr>
        <tr><td><code>bind:this</code></td><td>any element</td><td>Captures the DOM element into a <code>Ref</code></td></tr>
      </tbody>
    </table>

    <h2>Class directives</h2>

    <p>Toggle CSS classes reactively with <code>class:name={signal}</code>:</p>
    <pre><code>&lt;button class:active={isActive}&gt;Click me&lt;/button&gt;</code></pre>

    <p>Multiple class directives can be combined with a static <code>class</code>:</p>
    <pre><code>&lt;div class="item" class:selected={isSelected} class:disabled={isDisabled}&gt;</code></pre>

    <h2>Style directives</h2>

    <p>Set individual CSS properties reactively:</p>
    <pre><code>&lt;div style:color={textColor} style:font-size={"${ "{fontSize}" }px"}&gt;</code></pre>

    <h2>Event handlers</h2>

    <p>Use <code>on&lt;event&gt;={handler}</code> to attach DOM event listeners:</p>
    <pre><code>&lt;button onclick={_ =&gt; count += 1}&gt;+&lt;/button&gt;
&lt;input oninput={e =&gt; name.set(e.target.value)} /&gt;
&lt;form onsubmit={e =&gt; { e.preventDefault(); submit() }}&gt;</code></pre>

    <h2>Spread attributes</h2>

    <p>Spread a map of attributes onto an element:</p>
    <pre><code>&lt;script lang="scala"&gt;
  val attrs = Map("role" -&gt; "button", "aria-label" -&gt; "Close")
&lt;/script&gt;

&lt;div {...attrs}&gt;&lt;/div&gt;</code></pre>

    <h2>Element references</h2>

    <p>Capture a DOM element with <code>bind:this</code>:</p>
    <pre><code>&lt;script lang="scala"&gt;
  import melt.runtime.Ref
  val inputRef = Ref.empty[dom.html.Input]
&lt;/script&gt;

&lt;input bind:this={inputRef} /&gt;
&lt;button onclick={_ =&gt; inputRef.foreach(_.focus())}&gt;Focus&lt;/button&gt;</code></pre>
  """.replace("{\"{}\"}", "\"{}\"")

  // ── Reactivity ────────────────────────────────────────────────────────────

  private def reactivity(base: String) = s"""
    <p>Melt's reactivity is built on two core types: <code>State[A]</code> (mutable)
    and <code>Signal[A]</code> (read-only derived). When a <code>State</code> changes,
    every part of the UI that reads it updates automatically.</p>

    <h2>State</h2>

    <p>Create a mutable reactive value with <code>State(initialValue)</code>:</p>
    <pre><code>val count   = State(0)          // State[Int]
val name    = State("Alice")    // State[String]
val items   = State(List[String]()) // State[List[String]]</code></pre>

    <p>Read the current value with <code>.value</code> or by implicit conversion:</p>
    <pre><code>val n: Int = count.value  // explicit
val n: Int = count        // implicit conversion also works</code></pre>

    <h2>Mutating state</h2>

    <p>Use <code>.set()</code>, <code>.update()</code>, or the built-in operators:</p>
    <pre><code>count.set(10)             // replace
count.update(_ + 1)       // transform
count += 1                // shorthand for Int/Long/Double
count -= 1
name += " Smith"          // string append

// List operations
items.append("new item")
items.prepend("first")
items.removeWhere(_.isEmpty)
items.clear()</code></pre>

    <h2>Signal</h2>

    <p>A <code>Signal[A]</code> is a read-only view derived from one or more
    <code>State</code> values. Derive one with <code>.map()</code>:</p>
    <pre><code>val doubled: Signal[Int]    = count.map(_ * 2)
val upper:   Signal[String] = name.map(_.toUpperCase)
val isEmpty: Signal[Boolean] = items.map(_.isEmpty)</code></pre>

    <p>Signals update automatically whenever their source changes. Use them
    in templates the same way as <code>State</code>:</p>
    <pre><code>&lt;p&gt;{count} × 2 = {doubled}&lt;/p&gt;</code></pre>

    <h2>Reactive updates in the DOM</h2>

    <p>Any expression in a Melt template that reads a <code>State</code> or
    <code>Signal</code> is tracked. When the value changes, only that part of
    the DOM is updated — not the whole component.</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>Example: fine-grained updates
      </div>
      <pre><code>&lt;script lang="scala"&gt;
  val count = State(0)
&lt;/script&gt;

&lt;!-- Only this text node re-renders when count changes --&gt;
&lt;p&gt;Count: {count}&lt;/p&gt;

&lt;!-- This element is static and never re-renders --&gt;
&lt;p&gt;This text never changes.&lt;/p&gt;</code></pre>
    </div>

    <div class="callout callout-tip">
      <div class="callout-title">No virtual DOM</div>
      <p>Melt does not diff trees. Each reactive binding is its own independent
      subscription. Changing one value updates exactly the DOM nodes that depend
      on it.</p>
    </div>
  """

  // ── Computed ──────────────────────────────────────────────────────────────

  private def computed(base: String) = s"""
    <p>Computed values are derived <code>Signal</code>s that update automatically
    when their dependencies change. They are declared in the script section and
    used in the template just like <code>State</code>.</p>

    <h2>.map() — transform a value</h2>

    <p>Use <code>.map()</code> to create a new signal from an existing one:</p>
    <pre><code>val count:   State[Int]   = State(0)
val doubled: Signal[Int]  = count.map(_ * 2)
val label:   Signal[String] = count.map(n =&gt; if n == 0 then "zero" else s"$$n")</code></pre>

    <pre><code>&lt;p&gt;{count} doubled is {doubled}&lt;/p&gt;
&lt;p&gt;Label: {label}&lt;/p&gt;</code></pre>

    <h2>.flatMap() — dynamic sources</h2>

    <p>Use <code>.flatMap()</code> when the derived value depends on another
    <code>Signal</code>:</p>
    <pre><code>val mode   = State("spa")
val output = source.flatMap { code =&gt;
  mode.map { m =&gt;
    compile(code, m)  // re-runs when either source OR mode changes
  }
}</code></pre>

    <h2>.memo() — deduplicate updates</h2>

    <p>Use <code>.memo()</code> to skip downstream updates when the computed value
    has not actually changed:</p>
    <pre><code>val isEven: Signal[Boolean] = count.memo(_ % 2 == 0)
// Only triggers updates when the boolean flips, not on every count change</code></pre>

    <div class="callout callout-tip">
      <div class="callout-title">When to use .memo()</div>
      <p>Use <code>.memo()</code> when the mapped type has a cheap equality check
      but the parent changes frequently — for example, a boolean derived from an
      integer counter.</p>
    </div>

    <h2>Combining multiple signals</h2>

    <p>Chain <code>.map()</code> calls or use <code>.flatMap()</code> to combine
    several reactive sources:</p>
    <pre><code>val firstName = State("Alice")
val lastName  = State("Smith")
val fullName  = firstName.flatMap(f =&gt; lastName.map(l =&gt; s"$$f $$l"))</code></pre>
  """

  // ── Effects ───────────────────────────────────────────────────────────────

  private def effects(base: String) = s"""
    <p>An <em>effect</em> is a side-effectful computation that runs whenever its
    reactive dependencies change. Use effects for things like logging, network
    requests, and direct DOM manipulation.</p>

    <h2>Basic effect</h2>

    <p>Call <code>Effect { ... }</code> inside the script section. The block runs
    once on mount and again whenever any <code>State</code> or <code>Signal</code>
    read inside it changes:</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>Example
      </div>
      <pre><code>&lt;script lang="scala"&gt;
  import melt.runtime.Effect

  val query = State("")

  Effect {
    // Runs when `query` changes
    println(s"Searching for: $${query.value}")
    fetchResults(query.value)
  }
&lt;/script&gt;</code></pre>
    </div>

    <h2>Cleanup</h2>

    <p>Return a cleanup function from an effect to cancel subscriptions, timers,
    or event listeners when the component unmounts or before the next run:</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>Effect with cleanup
      </div>
      <pre><code>import melt.runtime.{ Effect, Cleanup }

val intervalId = State(0)

Effect {
  val id = js.timers.setInterval(1000) { count += 1 }
  Cleanup.register(() =&gt; js.timers.clearInterval(id))
}</code></pre>
    </div>

    <h2>Untracked reads</h2>

    <p>Sometimes you want to read a value inside an effect without making it a
    dependency. Wrap the read in <code>Untrack { ... }</code>:</p>
    <pre><code>import melt.runtime.Untrack

Effect {
  val current = Untrack { count.value }  // read without subscribing
  println(s"Triggered by other dep; count is $$current")
}</code></pre>

    <div class="callout callout-warn">
      <div class="callout-title">Avoid infinite loops</div>
      <p>Do not write to a <code>State</code> that you also read inside the same
      effect without using <code>Untrack</code> — it creates an infinite update loop.</p>
    </div>
  """

  // ── Events ────────────────────────────────────────────────────────────────

  private def events(base: String) = s"""
    <p>Event handlers in Melt are plain Scala functions attached directly to
    HTML elements with <code>on&lt;event&gt;={handler}</code> syntax.</p>

    <h2>Basic handlers</h2>

    <pre><code>&lt;button onclick={_ =&gt; count += 1}&gt;Increment&lt;/button&gt;
&lt;button onclick={_ =&gt; count.set(0)}&gt;Reset&lt;/button&gt;</code></pre>

    <p>The handler receives the native DOM event as its argument. Use <code>_</code>
    to ignore it when you don't need it.</p>

    <h2>Accessing the event object</h2>

    <pre><code>&lt;input oninput={e =&gt; name.set(e.target.asInstanceOf[dom.html.Input].value)} /&gt;
&lt;form onsubmit={e =&gt; { e.preventDefault(); submit() }}&gt;&lt;/form&gt;</code></pre>

    <p>Common event types from <code>org.scalajs.dom</code>:</p>
    <table class="api-table">
      <thead><tr><th>Handler</th><th>Event type</th><th>Common use</th></tr></thead>
      <tbody>
        <tr><td><code>onclick</code></td><td><code>MouseEvent</code></td><td>Buttons, links</td></tr>
        <tr><td><code>oninput</code></td><td><code>InputEvent</code></td><td>Text input changes</td></tr>
        <tr><td><code>onchange</code></td><td><code>Event</code></td><td>Select, checkbox</td></tr>
        <tr><td><code>onsubmit</code></td><td><code>SubmitEvent</code></td><td>Form submission</td></tr>
        <tr><td><code>onkeydown</code></td><td><code>KeyboardEvent</code></td><td>Key shortcuts</td></tr>
        <tr><td><code>onfocus / onblur</code></td><td><code>FocusEvent</code></td><td>Focus management</td></tr>
      </tbody>
    </table>

    <h2>bind:value shorthand</h2>

    <p>Instead of wiring <code>oninput</code> manually, use <code>bind:value</code>
    for a two-way sync between a text input and a <code>State[String]</code>:</p>
    <pre><code>val text = State("")
// ...
&lt;input type="text" bind:value={text} /&gt;</code></pre>

    <h2>Window and body events</h2>

    <p>Attach global listeners using <code>&lt;melt:window&gt;</code> and
    <code>&lt;melt:body&gt;</code> special elements
    (see <a href="$base/guide/special-elements">Special Elements</a>):</p>
    <pre><code>&lt;melt:window onkeydown={e =&gt; handleShortcut(e)} /&gt;</code></pre>
  """

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  private def lifecycle(base: String) = s"""
    <p>Melt components have a simple lifecycle: <em>mount</em> when inserted into
    the DOM and <em>destroy</em> when removed. You hook into these with
    <code>OnMount</code> and <code>Cleanup</code>.</p>

    <h2>OnMount</h2>

    <p>Code in <code>OnMount { ... }</code> runs once, after the component's DOM
    has been inserted into the document:</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>Example
      </div>
      <pre><code>&lt;script lang="scala"&gt;
  import melt.runtime.OnMount
  import melt.runtime.Ref

  val canvasRef = Ref.empty[dom.html.Canvas]

  OnMount {
    canvasRef.foreach { canvas =&gt;
      // canvas is now in the DOM — safe to measure or draw
      val ctx = canvas.getContext("2d")
      // ...
    }
  }
&lt;/script&gt;

&lt;canvas bind:this={canvasRef}&gt;&lt;/canvas&gt;</code></pre>
    </div>

    <div class="callout callout-info">
      <div class="callout-title">JVM (SSR) note</div>
      <p><code>OnMount</code> is a no-op on the JVM. It only runs in the browser.</p>
    </div>

    <h2>Cleanup on destroy</h2>

    <p>Register teardown callbacks with <code>Cleanup.register</code>. They run
    when the component is removed from the DOM:</p>

    <pre><code>import melt.runtime.{ OnMount, Cleanup }

OnMount {
  val subscription = eventBus.subscribe(handler)
  Cleanup.register(() =&gt; subscription.cancel())
}</code></pre>

    <h2>Effect cleanup</h2>

    <p><code>Cleanup.register</code> inside an <code>Effect</code> block runs
    before each re-execution of the effect, and once more on component destroy:</p>

    <pre><code>import melt.runtime.{ Effect, Cleanup }

val id = State[Option[Int]](None)

Effect {
  id.value.foreach { currentId =&gt;
    val ws = new WebSocket(s"wss://api.example.com/feed/$$currentId")
    Cleanup.register(() =&gt; ws.close())
  }
}</code></pre>
  """

  // ── Control Flow ──────────────────────────────────────────────────────────

  private def controlFlow(base: String) = s"""
    <p>Control flow in Melt templates uses Scala expressions directly — there
    are no special <code>#if</code> or <code>#each</code> directives. You write
    Scala inside <code>{"{}"}</code> and embed HTML elements within it.</p>

    <h2>Conditional rendering</h2>

    <p>Use a Scala <code>if</code> expression. Map over a <code>Signal</code> to
    make it reactive:</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>Reactive conditional
      </div>
      <pre><code>&lt;script lang="scala"&gt;
  val loggedIn = State(false)
&lt;/script&gt;

{loggedIn.map { logged =&gt;
  if logged then
    &lt;p&gt;Welcome back!&lt;/p&gt;
  else
    &lt;a href="/login"&gt;Sign in&lt;/a&gt;
}}</code></pre>
    </div>

    <div class="callout callout-info">
      <div class="callout-title">Why .map()?</div>
      <p>Accessing <code>loggedIn.value</code> directly in a template expression
      reads the value once but does not subscribe to future changes. Wrapping
      with <code>.map()</code> creates a reactive subscription that updates the
      DOM automatically.</p>
    </div>

    <h2>List rendering</h2>

    <p>Render a list with Scala's <code>.map()</code> on a
    <code>State[List[_]]</code> or <code>Signal[List[_]]</code>:</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>List rendering
      </div>
      <pre><code>&lt;script lang="scala"&gt;
  case class Item(id: Int, name: String)
  val items = State(List(Item(1, "Apple"), Item(2, "Banana")))
&lt;/script&gt;

&lt;ul&gt;
  {items.map(_.map((item: Item) =&gt;
    &lt;li&gt;{item.id}: {item.name}&lt;/li&gt;
  ))}
&lt;/ul&gt;</code></pre>
    </div>

    <h2>Key block</h2>

    <p>Force Melt to destroy and re-create a subtree when a key expression changes
    using the <code>&lt;melt:key&gt;</code> element. Useful for resetting component
    state:</p>

    <pre><code>&lt;melt:key this={selectedId}&gt;
  &lt;DetailPanel id={selectedId} /&gt;
&lt;/melt:key&gt;</code></pre>

    <p>Every time <code>selectedId</code> changes, <code>DetailPanel</code> is
    fully unmounted and remounted with fresh state.</p>

    <h2>Empty state</h2>

    <p>Handle empty lists gracefully:</p>
    <pre><code>{items.map { list =&gt;
  if list.isEmpty then
    &lt;p class="empty"&gt;No items yet.&lt;/p&gt;
  else
    &lt;ul&gt;{list.map((item: Item) =&gt; &lt;li&gt;{item.name}&lt;/li&gt;)}&lt;/ul&gt;
}}</code></pre>
  """.replace("{\"{}\"}", "\"{}\"")

  // ── Special Elements ──────────────────────────────────────────────────────

  private def specialElements(base: String) = s"""
    <p>Melt provides special built-in elements under the <code>melt:</code> namespace
    for common patterns that go beyond standard HTML.</p>

    <h2>&lt;melt:head&gt;</h2>

    <p>Insert content into the <code>&lt;head&gt;</code> of the page from any
    component:</p>
    <pre><code>&lt;melt:head&gt;
  &lt;title&gt;{pageTitle}&lt;/title&gt;
  &lt;meta name="description" content={description} /&gt;
&lt;/melt:head&gt;</code></pre>

    <h2>&lt;melt:window&gt; / &lt;melt:body&gt;</h2>

    <p>Attach global event listeners without manually calling
    <code>addEventListener</code>:</p>
    <pre><code>&lt;melt:window
  onkeydown={e =&gt; handleKey(e)}
  onresize={_ =&gt; recalcLayout()}
/&gt;

&lt;melt:body
  onmousedown={e =&gt; trackClick(e)}
/&gt;</code></pre>

    <p>Listeners are automatically removed when the component unmounts.</p>

    <h2>&lt;melt:boundary&gt;</h2>

    <p>Wrap a subtree in an error boundary that catches rendering errors and
    shows a fallback UI:</p>
    <pre><code>&lt;melt:boundary&gt;
  &lt;melt:pending&gt;
    &lt;p&gt;Loading...&lt;/p&gt;
  &lt;/melt:pending&gt;
  &lt;melt:failed let:error&gt;
    &lt;p&gt;Error: {error.message}&lt;/p&gt;
  &lt;/melt:failed&gt;
  &lt;AsyncComponent /&gt;
&lt;/melt:boundary&gt;</code></pre>

    <h2>&lt;melt:element&gt;</h2>

    <p>Render a dynamic tag name at runtime:</p>
    <pre><code>&lt;script lang="scala"&gt;
  val tag = State("div")
&lt;/script&gt;

&lt;melt:element this={tag} class="wrapper"&gt;
  {children}
&lt;/melt:element&gt;</code></pre>

    <h2>Snippets and render</h2>

    <p>Define reusable template fragments with <code>{"{#snippet}"}</code> and
    call them with <code>{"{@render}"}</code>:</p>
    <pre><code>&lt;script lang="scala"&gt;
  // snippets are defined in the template section below
&lt;/script&gt;

{#snippet badge(label: String)}
  &lt;span class="badge"&gt;{label}&lt;/span&gt;
{/snippet}

&lt;div&gt;
  {@render badge("New")}
  {@render badge("Hot")}
&lt;/div&gt;</code></pre>
  """.replace("{\"#snippet\"}", "\"{#snippet}\"").replace("{\"@render\"}", "\"{@render}\"")

  // ── Transitions ───────────────────────────────────────────────────────────

  private def transitions(base: String) = s"""
    <p>Melt provides a reactive animation API for smooth value changes:
    <code>Tween</code>, <code>Spring</code>, and CSS-based transitions.</p>

    <h2>Tween</h2>

    <p>Smoothly interpolate a numeric value over time:</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>Tween example
      </div>
      <pre><code>&lt;script lang="scala"&gt;
  import melt.runtime.transition.Tween

  val target  = State(0.0)
  val display = Tween(target, duration = 400)
&lt;/script&gt;

&lt;div style:opacity={display}&gt;Content&lt;/div&gt;
&lt;button onclick={_ =&gt; target.set(1.0)}&gt;Fade in&lt;/button&gt;</code></pre>
    </div>

    <h2>Spring</h2>

    <p>Use a physics-based spring for natural-feeling motion:</p>
    <pre><code>import melt.runtime.transition.Spring

val x      = State(0.0)
val smooth = Spring(x, stiffness = 0.15, damping = 0.8)</code></pre>

    <table class="api-table">
      <thead><tr><th>Option</th><th>Default</th><th>Description</th></tr></thead>
      <tbody>
        <tr><td><code>stiffness</code></td><td>0.15</td><td>How fast the spring moves toward the target</td></tr>
        <tr><td><code>damping</code></td><td>0.8</td><td>How quickly oscillations decay (1.0 = no oscillation)</td></tr>
        <tr><td><code>precision</code></td><td>0.001</td><td>Distance at which motion stops</td></tr>
      </tbody>
    </table>

    <h2>CSS transitions</h2>

    <p>For class-based transitions, pair <code>class:</code> directives with
    CSS <code>transition</code> properties:</p>
    <pre><code>&lt;!-- Template --&gt;
&lt;div class="panel" class:open={isOpen}&gt;
  {children}
&lt;/div&gt;

&lt;!-- Style --&gt;
&lt;style&gt;
  .panel {
    max-height: 0;
    overflow: hidden;
    transition: max-height 0.3s ease;
  }
  .panel.open { max-height: 500px; }
&lt;/style&gt;</code></pre>
  """

  // ── Trusted HTML ──────────────────────────────────────────────────────────

  private def trustedHtml(base: String) = s"""
    <p>Melt escapes all dynamic content by default to prevent XSS attacks.
    When you need to inject raw HTML, wrap it in <code>TrustedHtml</code> to
    signal that you have reviewed the content.</p>

    <h2>Why escaped by default?</h2>

    <p>Consider this example:</p>
    <pre><code>val userInput = "&lt;script&gt;alert('xss')&lt;/script&gt;"
// ...
&lt;p&gt;{userInput}&lt;/p&gt;
// Renders: &lt;p&gt;&amp;lt;script&amp;gt;...&lt;/p&gt;  ← SAFE</code></pre>

    <p>The template compiler automatically calls <code>Escape.html</code> on
    dynamic string values. You cannot accidentally render raw HTML.</p>

    <h2>TrustedHtml.unsafe</h2>

    <p>Use <code>TrustedHtml.unsafe</code> for HTML you control — static strings
    or content from a trusted CMS:</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>Example
      </div>
      <pre><code>&lt;script lang="scala"&gt;
  import melt.runtime.TrustedHtml

  // Only use for content you trust!
  val richContent = TrustedHtml.unsafe("&lt;strong&gt;Bold&lt;/strong&gt; text")
&lt;/script&gt;

&lt;div bind:html={richContent}&gt;&lt;/div&gt;</code></pre>
    </div>

    <div class="callout callout-warn">
      <div class="callout-title">Never use with user input</div>
      <p>Never pass untrusted user-supplied content to <code>TrustedHtml.unsafe</code>.
      Use a sanitizer library first, then wrap the sanitized result.</p>
    </div>

    <h2>TrustedHtml.sanitize</h2>

    <p>For user-generated content, provide a sanitizer function:</p>
    <pre><code>import melt.runtime.TrustedHtml

val safeHtml = TrustedHtml.sanitize(
  userMarkdown,
  html =&gt; mySanitizer.clean(html)  // your sanitizer here
)</code></pre>

    <h2>TrustedUrl</h2>

    <p>Melt also validates <code>href</code> and <code>src</code> attributes that
    accept URLs. Use <code>TrustedUrl</code> for dynamic values:</p>
    <pre><code>import melt.runtime.TrustedUrl

val link = TrustedUrl.unsafe("https://example.com")
// ...
&lt;a href={link}&gt;Visit&lt;/a&gt;</code></pre>

    <p>Without wrapping, dangerous protocols (<code>javascript:</code>,
    <code>vbscript:</code>, <code>data:text/html</code>) are blocked at compile time.</p>
  """

  // ── CSS ───────────────────────────────────────────────────────────────────

  private def css(base: String) = s"""
    <p>CSS in Melt is scoped to the component by default. You can also use global
    styles, CSS custom properties, and optionally SCSS.</p>

    <h2>Scoped styles</h2>

    <p>Any CSS written inside a component's <code>&lt;style&gt;</code> block is
    automatically scoped. The compiler adds a unique attribute to each element,
    and prefixes every rule to match:</p>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>Card.melt
      </div>
      <pre><code>&lt;div class="card"&gt;&lt;p&gt;Content&lt;/p&gt;&lt;/div&gt;

&lt;style&gt;
  /* Only applies to elements in THIS component */
  .card { border: 1px solid #ccc; }
  p     { color: grey; }
&lt;/style&gt;</code></pre>
    </div>

    <p>Generated HTML (simplified):</p>
    <pre><code>&lt;div class="card" data-melt-abc123&gt;
  &lt;p data-melt-abc123&gt;Content&lt;/p&gt;
&lt;/div&gt;</code></pre>

    <h2>Dynamic styles</h2>

    <p>Use the <code>style:property</code> directive for reactive inline styles:</p>
    <pre><code>&lt;script lang="scala"&gt;
  val hue = State(200)
&lt;/script&gt;

&lt;div style:background-color={"hsl(" + hue + ", 60%, 50%)"}&gt;&lt;/div&gt;
&lt;input type="range" bind:value={hue} min="0" max="360" /&gt;</code></pre>

    <h2>CSS custom properties</h2>

    <p>Pass reactive values to CSS via custom properties:</p>
    <pre><code>&lt;script lang="scala"&gt;
  val progress = State(0.0)
&lt;/script&gt;

&lt;div class="bar" style:--progress={progress + "%"}&gt;&lt;/div&gt;

&lt;style&gt;
  .bar::before {
    width: var(--progress);
    background: var(--accent);
  }
&lt;/style&gt;</code></pre>

    <h2>SCSS support</h2>

    <p>Add <code>lang="scss"</code> to the style block and enable the SCSS
    preprocessor in your sbt config:</p>
    <pre><code>&lt;style lang="scss"&gt;
  $$primary: #d6526a;

  .card {
    &amp;:hover { background: lighten($$primary, 40%); }
    &amp;__title { color: $$primary; }
  }
&lt;/style&gt;</code></pre>

    <div class="callout callout-info">
      <div class="callout-title">SCSS requires Dart Sass</div>
      <p>The <code>melt-compiler-sass</code> module wraps Dart Sass. Add it to
      your JVM classpath and set <code>meltcPreprocessor := "scss"</code> in
      your sbt config.</p>
    </div>
  """

  // ── Testing ───────────────────────────────────────────────────────────────

  private def testing(base: String) = s"""
    <p>Melt ships a <code>melt-testkit</code> module that lets you mount components
    in a simulated DOM environment and assert on the rendered output.</p>

    <h2>Setup</h2>

    <p>Add the dependency to your test configuration:</p>
    <pre><code>libraryDependencies += "io.github.takapi327" %%% "melt-testkit" % "0.1.0-SNAPSHOT" % Test</code></pre>

    <h2>Writing a test</h2>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>CounterSpec.scala
      </div>
      <pre><code>import melt.testkit.*

class CounterSpec extends MeltSuite:

  test("counter starts at zero") {
    val env = MeltEnv.render(Counter(Counter.Props()))
    assertEquals(env.text(".h1"), "0")
  }

  test("increment button updates count") {
    val env = MeltEnv.render(Counter(Counter.Props()))
    env.click("button:first-child")
    assertEquals(env.text("h1"), "1")
  }

  test("reset returns to zero") {
    val env = MeltEnv.render(Counter(Counter.Props()))
    env.click("button:first-child")
    env.click("button:last-child")
    assertEquals(env.text("h1"), "0")
  }</code></pre>
    </div>

    <h2>MeltEnv API</h2>

    <table class="api-table">
      <thead><tr><th>Method</th><th>Description</th></tr></thead>
      <tbody>
        <tr><td><code>MeltEnv.render(component)</code></td><td>Mount a component and return a test environment</td></tr>
        <tr><td><code>env.text(selector)</code></td><td>Get the text content of a matched element</td></tr>
        <tr><td><code>env.click(selector)</code></td><td>Simulate a click on a matched element</td></tr>
        <tr><td><code>env.input(selector, value)</code></td><td>Type a value into an input</td></tr>
        <tr><td><code>env.query(selector)</code></td><td>Find an element (<code>Option[Element]</code>)</td></tr>
        <tr><td><code>env.queryAll(selector)</code></td><td>Find all matching elements</td></tr>
      </tbody>
    </table>
  """

  // ── Routing ───────────────────────────────────────────────────────────────

  private def routing(base: String) = s"""
    <p>MeltKit provides a type-safe routing DSL for full-stack Melt applications.
    Routes are declared in Scala, checked at compile time, and rendered on the
    server (SSR) or client (SPA).</p>

    <h2>Setup</h2>

    <p>Add MeltKit to your JVM module:</p>
    <pre><code>// build.sbt
libraryDependencies += "io.github.takapi327" %% "meltkit-adapter-http4s" % "0.1.0-SNAPSHOT"</code></pre>

    <h2>Defining routes</h2>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>Main.scala
      </div>
      <pre><code>import meltkit.*
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

val app = MeltKit[Future]()

// Static route
app.get("") { ctx =&gt;
  Future.successful(ctx.render(Home(Home.Props())))
}

// Dynamic route with a path parameter
private val id = param[String]("id")

app.get("users" / id) { ctx =&gt;
  val userId = ctx.params.id
  Future.successful(ctx.render(UserPage(UserPage.Props(id = userId))))
}</code></pre>
    </div>

    <h2>Path parameters</h2>

    <p>Declare parameters with <code>param[T]("name")</code> and combine them
    with <code>/</code>:</p>
    <pre><code>private val lang    = param[String]("lang")
private val section = param[String]("section")

app.get(lang / "guide" / section) { ctx =&gt;
  val l = ctx.params.lang
  val s = ctx.params.section
  Future.successful(ctx.render(GuidePage(GuidePage.Props(lang = l, slug = s))))
}</code></pre>

    <h2>PageOptions</h2>

    <p>Control SSR, CSR, and prerendering per route:</p>
    <pre><code>val opts = PageOptions(
  ssr       = true,         // render on the server
  csr       = true,         // hydrate on the client
  prerender = PrerenderOption.On,  // generate at build time
  entries   = List("/en/guide/introduction", "/ja/guide/introduction")
)

app.get(lang / "guide" / slug, opts) { ctx =&gt; ... }</code></pre>
  """

  // ── SSR ───────────────────────────────────────────────────────────────────

  private def ssr(base: String) = s"""
    <p>Server-Side Rendering (SSR) renders Melt components on the JVM and sends
    HTML to the browser. The client then <em>hydrates</em> the static HTML —
    attaching event listeners and making it interactive without re-rendering.</p>

    <h2>How it works</h2>

    <ol>
      <li>The server receives a request.</li>
      <li>MeltKit renders the matching component to an HTML string on the JVM.</li>
      <li>The HTML is sent with hydration markers embedded.</li>
      <li>In the browser, the Scala.js bundle hydrates the DOM: existing nodes are
      reused and reactivity is attached.</li>
    </ol>

    <h2>Enabling SSR</h2>

    <p>Use the <code>sbt-meltkit</code> plugin and set the codegen mode:</p>
    <pre><code>// build.sbt (server module)
lazy val server = project
  .enablePlugins(MeltkitPlugin)
  .settings(meltMode := "ssr")

// build.sbt (client module — for hydration)
lazy val client = project
  .enablePlugins(MeltkitPlugin)
  .settings(meltMode := "spa")</code></pre>

    <h2>Route configuration</h2>

    <pre><code>app.get("blog" / slug, PageOptions(ssr = true, csr = true)) { ctx =&gt;
  fetchPost(ctx.params.slug).map { post =&gt;
    ctx.render(BlogPost(BlogPost.Props(post = post)))
  }
}</code></pre>

    <h2>Props serialization</h2>

    <p>For hydration to work, props are serialized to JSON by the server and
    deserialized by the client. Derive a <code>PropsCodec</code> automatically:</p>
    <pre><code>case class Props(title: String, count: Int)
// Codec is derived automatically for case classes with simple types</code></pre>

    <div class="callout callout-tip">
      <div class="callout-title">Partial hydration</div>
      <p>Set <code>csr = false</code> to render a component as pure static HTML
      with no client-side JavaScript at all.</p>
    </div>
  """

  // ── SSG ───────────────────────────────────────────────────────────────────

  private def ssg(base: String) = s"""
    <p>Static Site Generation (SSG) pre-renders all pages at build time and
    outputs a directory of plain HTML files. The result can be served from any
    CDN with zero server infrastructure.</p>

    <h2>Enabling prerender</h2>

    <p>Set <code>prerender = PrerenderOption.On</code> on your routes and provide
    a list of all URL entries to generate:</p>

    <pre><code>private val langs  = List("en", "ja")
private val guides = List("introduction", "installation", ...)
private val On     = PageOptions(prerender = PrerenderOption.On)

app.get(
  lang / "guide" / slug,
  On.copy(entries = for l &lt;- langs; g &lt;- guides yield s"/$$l/guide/$$g")
) { ctx =&gt; ... }</code></pre>

    <h2>Running the generator</h2>

    <p>Create a <code>generate</code> main method that calls <code>SsgGenerator.run</code>:</p>

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

    <p>Run it with sbt:</p>
    <pre><code>sbt "server/runMain generate"</code></pre>

    <h2>Output structure</h2>
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
  """

  // ── Adapters ──────────────────────────────────────────────────────────────

  private def adapters(base: String) = s"""
    <p>MeltKit adapters connect your app to a specific runtime environment.
    Choose the adapter that matches your deployment target.</p>

    <h2>http4s (JVM + Scala.js)</h2>

    <p>The <code>meltkit-adapter-http4s</code> module integrates MeltKit with
    http4s for production JVM deployments:</p>
    <pre><code>libraryDependencies += "io.github.takapi327" %% "meltkit-adapter-http4s" % "0.1.0-SNAPSHOT"</code></pre>

    <div class="code-block">
      <div class="code-block-header">
        <span class="code-block-dot"></span>Http4s setup
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

    <h2>Node.js</h2>

    <p>Deploy to Node.js with <code>meltkit-adapter-node</code>:</p>
    <pre><code>libraryDependencies += "io.github.takapi327" %%% "meltkit-adapter-node" % "0.1.0-SNAPSHOT"</code></pre>

    <h2>Browser (SPA)</h2>

    <p>For pure client-side SPA without a server, use
    <code>meltkit-adapter-browser</code>. It handles client-side routing and
    history management:</p>
    <pre><code>libraryDependencies += "io.github.takapi327" %%% "meltkit-adapter-browser" % "0.1.0-SNAPSHOT"</code></pre>

    <h2>Comparison</h2>

    <table class="api-table">
      <thead>
        <tr>
          <th>Adapter</th><th>Platform</th><th>SSR</th><th>SSG</th><th>SPA</th>
        </tr>
      </thead>
      <tbody>
        <tr><td><code>meltkit-adapter-http4s</code></td><td>JVM</td><td>✓</td><td>✓</td><td>via Vite</td></tr>
        <tr><td><code>meltkit-adapter-node</code></td><td>Node.js</td><td>✓</td><td>✓</td><td>via Vite</td></tr>
        <tr><td><code>meltkit-adapter-browser</code></td><td>Browser</td><td>—</td><td>—</td><td>✓</td></tr>
      </tbody>
    </table>
  """
