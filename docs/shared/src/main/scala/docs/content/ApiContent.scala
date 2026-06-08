/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package docs.content

import melt.runtime.TrustedHtml

object ApiContent:

  private val order = List(
    "template-syntax",
    "runtime",
    "meltkit",
    "meltkit-ssg",
    "compiler",
    "sbt-plugin"
  )

  private val titles = Map(
    "template-syntax" -> "Template Syntax",
    "runtime"         -> "Runtime API",
    "meltkit"         -> "MeltKit",
    "meltkit-ssg"     -> "Static Site Generation",
    "compiler"        -> "Compiler API",
    "sbt-plugin"      -> "sbt Plugin"
  )

  private val titlesJa = Map(
    "template-syntax" -> "テンプレート構文",
    "runtime"         -> "Runtime API",
    "meltkit"         -> "MeltKit",
    "meltkit-ssg"     -> "静的サイト生成",
    "compiler"        -> "コンパイラ API",
    "sbt-plugin"      -> "sbt プラグイン"
  )

  def title(slug: String): String = titles.getOrElse(slug, slug)

  def titleForLang(slug: String, lang: String): String =
    if lang == "ja" then titlesJa.getOrElse(slug, titles.getOrElse(slug, slug))
    else titles.getOrElse(slug, slug)

  def get(slug: String, basePath: String, lang: String): TrustedHtml =
    val base = s"$basePath/$lang"
    val idx  = order.indexOf(slug)
    val prev = if idx > 0 then Some(order(idx - 1)) else None
    val next = if idx >= 0 && idx < order.length - 1 then Some(order(idx + 1)) else None
    val body = if lang == "ja" then ApiContentJa.content(slug, base) else content(slug, base)
    TrustedHtml.unsafe(body + nav(prev, next, base, lang))

  private def nav(prev: Option[String], next: Option[String], base: String, lang: String = "en"): String =
    val (prevLabel, nextLabel) = if lang == "ja" then ("← 前のページ", "次のページ →") else ("← Previous", "Next →")
    val prevHtml               = prev
      .map(p => s"""<a href="$base/api/$p" class="doc-nav-item doc-nav-prev">
         |  <span class="doc-nav-dir">$prevLabel</span>
         |  <span class="doc-nav-label">${ titleForLang(p, lang) }</span>
         |</a>""".stripMargin)
      .getOrElse("")
    val nextHtml = next
      .map(n => s"""<a href="$base/api/$n" class="doc-nav-item doc-nav-next">
         |  <span class="doc-nav-dir">$nextLabel</span>
         |  <span class="doc-nav-label">${ titleForLang(n, lang) }</span>
         |</a>""".stripMargin)
      .getOrElse("")
    if prevHtml.isEmpty && nextHtml.isEmpty then ""
    else s"""<nav class="doc-nav">$prevHtml$nextHtml</nav>"""

  private def content(slug: String, base: String): String = slug match

    case "template-syntax" =>
      """
<h1 class="doc-title">Template Syntax</h1>
<p class="doc-lead">Melt templates are HTML-first with Scala expressions embedded via <code>{}</code> interpolation. This reference covers every construct the compiler understands.</p>

<h2>Text interpolation</h2>
<p>Wrap any Scala expression in <code>{}</code> to render it as escaped text.</p>
<pre class="code-block"><code>&lt;p&gt;Hello, {name}!&lt;/p&gt;
&lt;span&gt;{count * 2}&lt;/span&gt;
&lt;div&gt;{if loggedIn then "Welcome back" else "Please sign in"}&lt;/div&gt;</code></pre>
<div class="callout callout-tip"><strong>Tip:</strong> All interpolated strings are HTML-escaped automatically. To render raw HTML, use <code>bind:html</code>.</div>

<h2>Attribute binding</h2>
<p>Set attributes dynamically by wrapping the value in <code>{}</code>.</p>
<pre class="code-block"><code>&lt;a href={url}&gt;Link&lt;/a&gt;
&lt;input type="text" value={name} placeholder={hint}/&gt;
&lt;img src={avatarUrl} alt={altText}/&gt;</code></pre>

<h2>Boolean attributes</h2>
<p>Pass <code>true</code>/<code>false</code> to toggle boolean HTML attributes.</p>
<pre class="code-block"><code>&lt;input disabled={!isEnabled}/&gt;
&lt;button hidden={!showButton}&gt;Click&lt;/button&gt;</code></pre>

<h2>class: directive</h2>
<p>Conditionally toggle a CSS class with <code>class:name={condition}</code>.</p>
<pre class="code-block"><code>&lt;div class="card" class:active={isSelected}&gt;...&lt;/div&gt;
&lt;button class:loading={isPending} class:disabled={!canSubmit}&gt;Submit&lt;/button&gt;</code></pre>

<h2>style: directive</h2>
<p>Set individual CSS properties reactively with <code>style:property={value}</code>.</p>
<pre class="code-block"><code>&lt;div style:color={textColor} style:font-size="{fontSize}px"&gt;...&lt;/div&gt;
&lt;div style:opacity={opacity.toString}&gt;...&lt;/div&gt;</code></pre>

<h2>Event handling</h2>
<p>Use <code>on:event</code> or the short form <code>onevent</code> to attach event listeners.</p>
<pre class="code-block"><code>&lt;button onclick={_ =&gt; count += 1}&gt;+&lt;/button&gt;
&lt;input oninput={e =&gt; name.set(e.target.value)}/&gt;
&lt;form onsubmit={e =&gt; { e.preventDefault(); handleSubmit() }}&gt;...&lt;/form&gt;</code></pre>

<h2>bind: directive</h2>
<p>Two-way bind form elements to a <code>State</code> variable.</p>
<pre class="code-block"><code>&lt;input bind:value={name}/&gt;
&lt;input type="checkbox" bind:checked={agreed}/&gt;
&lt;select bind:value={selected}&gt;
  &lt;option value="a"&gt;A&lt;/option&gt;
  &lt;option value="b"&gt;B&lt;/option&gt;
&lt;/select&gt;</code></pre>

<h2>bind:html — raw HTML injection</h2>
<p>Render a <code>TrustedHtml</code> value as unescaped HTML. Requires an explicit trust annotation to prevent accidental XSS.</p>
<pre class="code-block"><code>// Static trusted content
&lt;div bind:html={TrustedHtml.unsafe("&lt;strong&gt;Hello&lt;/strong&gt;")}/&gt;

// Reactive trusted content
val html = State(TrustedHtml.unsafe("&lt;em&gt;initial&lt;/em&gt;"))
&lt;div bind:html={html}/&gt;</code></pre>

<h2>Control flow</h2>

<h3>if / else if / else</h3>
<pre class="code-block"><code>{if count &gt; 10 then
  &lt;p&gt;Big number!&lt;/p&gt;
else if count &gt; 0 then
  &lt;p&gt;Small number&lt;/p&gt;
else
  &lt;p&gt;Zero&lt;/p&gt;
}</code></pre>

<h3>List rendering</h3>
<p>Use <code>.map()</code> to iterate over collections. Add a <code>key</code> attribute for stable reconciliation.</p>
<pre class="code-block"><code>{items.map(item =&gt;
  &lt;li key={item.id}&gt;{item.name}&lt;/li&gt;
)}</code></pre>

<h2>Components</h2>
<p>Import and use other <code>.melt</code> components as HTML tags. Component names start with uppercase.</p>
<pre class="code-block"><code>&lt;Button label="Click me" onclick={_ =&gt; doSomething()}/&gt;
&lt;Card title="My Card"&gt;
  &lt;p&gt;Slot content goes here&lt;/p&gt;
&lt;/Card&gt;</code></pre>

<h2>Slots (children)</h2>
<p>Use the special <code>{children}</code> expression inside a component to render child content passed from the parent.</p>
<pre class="code-block"><code>// Inside Card.melt
&lt;div class="card"&gt;
  &lt;h2&gt;{props.title}&lt;/h2&gt;
  &lt;div class="card-body"&gt;{children}&lt;/div&gt;
&lt;/div&gt;</code></pre>

<h2>Special elements</h2>

<table class="api-table">
  <thead><tr><th>Element</th><th>Description</th></tr></thead>
  <tbody>
    <tr><td><code>&lt;Head&gt;</code></td><td>Renders children into the document <code>&lt;head&gt;</code></td></tr>
    <tr><td><code>&lt;Window&gt;</code></td><td>Attaches event listeners to <code>window</code></td></tr>
    <tr><td><code>&lt;Body&gt;</code></td><td>Attaches event listeners to <code>document.body</code></td></tr>
    <tr><td><code>&lt;Document&gt;</code></td><td>Attaches event listeners to <code>document</code></td></tr>
    <tr><td><code>&lt;Boundary&gt;</code></td><td>Error boundary — catches render errors</td></tr>
    <tr><td><code>&lt;Await&gt;</code></td><td>Resolves a <code>Future</code> and renders loading/success/error states</td></tr>
  </tbody>
</table>
"""

    case "runtime" =>
      """
<h1 class="doc-title">Runtime API</h1>
<p class="doc-lead">The Melt runtime provides reactive primitives, lifecycle hooks, and HTML escaping utilities. All shared APIs work on both Scala.js (browser) and JVM (SSR).</p>

<h2>State[A]</h2>
<p>A mutable reactive variable. On Scala.js, mutations notify all subscribers. On the JVM (SSR), it holds a static value used for the initial render.</p>

<table class="api-table">
  <thead><tr><th>Member</th><th>Description</th></tr></thead>
  <tbody>
    <tr><td><code>State(initial)</code></td><td>Create a new state with an initial value</td></tr>
    <tr><td><code>.value</code></td><td>Read the current value without tracking</td></tr>
    <tr><td><code>.set(v)</code></td><td>Replace the value and notify subscribers</td></tr>
    <tr><td><code>.update(f)</code></td><td>Update with a function <code>A =&gt; A</code></td></tr>
    <tr><td><code>.map(f)</code></td><td>Derive a <code>Signal[B]</code> that updates when state changes</td></tr>
    <tr><td><code>.memo(f)</code></td><td>Like <code>map</code>, but only emits when the derived value changes</td></tr>
    <tr><td><code>.flatMap(f)</code></td><td>Derive a signal with dynamic source switching</td></tr>
    <tr><td><code>.subscribe(f)</code></td><td>Subscribe to value changes; returns an unsubscribe function</td></tr>
    <tr><td><code>.signal</code></td><td>A read-only view of this state as a <code>Signal[A]</code></td></tr>
  </tbody>
</table>

<pre class="code-block"><code>val count   = State(0)
val doubled = count.map(_ * 2)
val isEven  = count.memo(_ % 2 == 0)

count.set(5)       // doubled = 10, isEven = false
count.update(_ + 1) // count = 6, doubled = 12, isEven = true</code></pre>

<div class="callout callout-tip"><strong>Tip:</strong> <code>State[A]</code> has an implicit conversion to <code>A</code>, so you can use it directly in expressions without calling <code>.value</code>.</div>

<h2>Signal[A]</h2>
<p>A read-only reactive value, typically derived from one or more <code>State</code> instances via <code>.map</code> or <code>.memo</code>.</p>

<table class="api-table">
  <thead><tr><th>Member</th><th>Description</th></tr></thead>
  <tbody>
    <tr><td><code>Signal.pure(v)</code></td><td>A frozen signal always holding <code>v</code></td></tr>
    <tr><td><code>.value</code></td><td>Read the current value</td></tr>
    <tr><td><code>.map(f)</code></td><td>Derive a new signal</td></tr>
    <tr><td><code>.memo(f)</code></td><td>Derive a signal that suppresses duplicate emissions</td></tr>
    <tr><td><code>.subscribe(f)</code></td><td>Subscribe to changes; returns an unsubscribe function</td></tr>
  </tbody>
</table>

<h2>StateExtensions (operator sugar)</h2>
<p>Numeric and collection operations on <code>State</code> variables.</p>
<pre class="code-block"><code>val n    = State(0)
val list = State(List.empty[String])

n += 1         // State[Int] increment
n -= 1         // decrement
list :+= "a"   // append to list
list = list.value.filter(_.nonEmpty)  // replace</code></pre>

<h2>onMount</h2>
<p>Register a callback to run after the component is mounted to the DOM. Return a cleanup function to run on unmount.</p>
<pre class="code-block"><code>import melt.runtime.onMount

onMount { ctx =&gt;
  val timer = setInterval(() =&gt; tick(), 1000)
  ctx.onCleanup(() =&gt; clearInterval(timer))
}</code></pre>
<div class="callout callout-info"><strong>Note:</strong> <code>onMount</code> callbacks only run on Scala.js. On the JVM they are no-ops, so SSR code never executes mount side effects.</div>

<h2>Batch</h2>
<p>Group multiple state mutations so subscribers are notified only once, after all updates in the block complete.</p>
<pre class="code-block"><code>import melt.runtime.Batch

Batch {
  x.set(1)
  y.set(2)
  z.set(3)
} // subscribers notified once, with all three values updated</code></pre>

<h2>TrustedHtml</h2>
<p>A wrapper type that marks a string as safe to render as raw HTML. Required by <code>bind:html</code> to prevent accidental XSS.</p>

<table class="api-table">
  <thead><tr><th>Member</th><th>Description</th></tr></thead>
  <tbody>
    <tr><td><code>TrustedHtml.unsafe(html)</code></td><td>Trust static, developer-controlled markup</td></tr>
    <tr><td><code>TrustedHtml.sanitize(html, fn)</code></td><td>Sanitize user input with a custom sanitizer</td></tr>
    <tr><td><code>.value</code></td><td>The underlying HTML string</td></tr>
  </tbody>
</table>

<pre class="code-block"><code>// Developer-controlled content — safe
val badge = TrustedHtml.unsafe("&lt;strong&gt;New&lt;/strong&gt;")

// User-provided content — sanitize first
val safeContent = TrustedHtml.sanitize(userInput, DOMPurify.sanitize(_))</code></pre>

<h2>Escape</h2>
<p>HTML, attribute, URL, and CSS escaping utilities. Generated code calls these automatically; you rarely need them directly.</p>

<table class="api-table">
  <thead><tr><th>Function</th><th>Use case</th></tr></thead>
  <tbody>
    <tr><td><code>Escape.html(v)</code></td><td>Escape for HTML text content</td></tr>
    <tr><td><code>Escape.attr(v)</code></td><td>Escape for HTML attribute values</td></tr>
    <tr><td><code>Escape.url(v)</code></td><td>Validate and escape URLs (blocks <code>javascript:</code>)</td></tr>
    <tr><td><code>Escape.cssValue(v)</code></td><td>Escape CSS property values</td></tr>
  </tbody>
</table>

<h2>MeltEffect[F[_]]</h2>
<p>Type class for bridging async effect types (e.g. <code>Future</code>) with the Melt runtime. A <code>given</code> instance for <code>Future</code> is provided automatically.</p>
<pre class="code-block"><code>// Using with Await component
val data = State[Option[String]](None)
// Await automatically uses MeltEffect[Future]</code></pre>
"""

    case "meltkit" =>
      """
<h1 class="doc-title">MeltKit</h1>
<p class="doc-lead">MeltKit is Melt's server framework — a type-safe routing DSL and request/response model that works on both Node.js and the JVM via http4s. It handles SSR, API routes, and static file serving.</p>

<div class="callout callout-info"><strong>Requires Scala 3.8+</strong> — MeltKit uses <code>NamedTuple</code> for type-safe path parameters, which requires Scala 3.8 or later.</div>

<h2>Installation</h2>
<pre class="code-block"><code>// build.sbt
libraryDependencies += "io.github.takapi327" %%% "meltkit" % "0.1.0"

// Pick your adapter:
libraryDependencies += "io.github.takapi327" %%% "meltkit-adapter-browser" % "0.1.0"  // Scala.js
libraryDependencies += "io.github.takapi327" %%% "meltkit-adapter-http4s"  % "0.1.0"  // JVM / Node</code></pre>

<h2>Defining routes</h2>
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

<h2>Path parameters</h2>
<p>Define typed path parameters with <code>param[T]("name")</code>. Parameters compose with <code>/</code> to build <code>PathSpec</code> values.</p>

<table class="api-table">
  <thead><tr><th>Expression</th><th>Matches</th><th>Params type</th></tr></thead>
  <tbody>
    <tr><td><code>"users"</code></td><td><code>/users</code></td><td><code>()</code></td></tr>
    <tr><td><code>param[Int]("id")</code></td><td><code>/:id</code></td><td><code>(id: Int)</code></td></tr>
    <tr><td><code>"users" / param[Int]("id")</code></td><td><code>/users/:id</code></td><td><code>(id: Int)</code></td></tr>
    <tr><td><code>param[String]("lang") / "guide" / param[String]("slug")</code></td><td><code>/:lang/guide/:slug</code></td><td><code>(lang: String, slug: String)</code></td></tr>
  </tbody>
</table>

<pre class="code-block"><code>val lang = param[String]("lang")
val id   = param[Int]("id")

get(lang / "posts" / id) { ctx =&gt;
  val language: String = ctx.params.lang
  val postId:   Int    = ctx.params.id
  ctx.json(fetchPost(postId))
}</code></pre>

<h2>MeltContext</h2>
<p>Every route handler receives a <code>MeltContext[F, P, B, R]</code>. Its type parameters are inferred from the route definition.</p>

<table class="api-table">
  <thead><tr><th>Member</th><th>Description</th></tr></thead>
  <tbody>
    <tr><td><code>ctx.params</code></td><td>Typed path parameters as a NamedTuple</td></tr>
    <tr><td><code>ctx.request</code></td><td>The HTTP request</td></tr>
    <tr><td><code>ctx.html(component)</code></td><td>Render an SSR component and return HTML response</td></tr>
    <tr><td><code>ctx.json(value)</code></td><td>Return a JSON response</td></tr>
    <tr><td><code>ctx.text(str)</code></td><td>Return a plain text response</td></tr>
    <tr><td><code>ctx.redirect(url)</code></td><td>Return a redirect response</td></tr>
    <tr><td><code>ctx.locals</code></td><td>Request-scoped storage (type-safe)</td></tr>
  </tbody>
</table>

<h2>HTTP methods</h2>
<pre class="code-block"><code>val app = MeltKit[IO]:
  get("api/users")    { ctx =&gt; ctx.json(getUsers()) }
  post("api/users")   { ctx =&gt; ctx.json(createUser(ctx.body)) }
  put("api/users/1")  { ctx =&gt; ctx.json(updateUser(ctx.body)) }
  delete("api/users/1") { ctx =&gt; ctx.json(deleteUser()) }</code></pre>

<h2>Middleware (use)</h2>
<pre class="code-block"><code>val app = MeltKit[IO]:
  use { (ctx, next) =&gt;
    // runs before every route
    println(s"Request: ${ctx.request.method} ${ctx.request.path}")
    next(ctx)
  }

  get("protected") { ctx =&gt;
    if ctx.locals.get(AuthKey).isEmpty
    then ctx.redirect("/login")
    else ctx.html(ProtectedPage())
  }</code></pre>

<h2>Template (HTML shell)</h2>
<p>The <code>Template</code> class wraps your <code>index.html</code> and injects SSR content via placeholder markers.</p>

<table class="api-table">
  <thead><tr><th>Placeholder</th><th>Replaced with</th></tr></thead>
  <tbody>
    <tr><td><code>%melt.head%</code></td><td>SSR-rendered <code>&lt;Head&gt;</code> content (meta, links, styles)</td></tr>
    <tr><td><code>%melt.body%</code></td><td>SSR-rendered body HTML</td></tr>
    <tr><td><code>%melt.title%</code></td><td>Page title from <code>&lt;Head&gt;&lt;title&gt;...&lt;/title&gt;&lt;/Head&gt;</code></td></tr>
    <tr><td><code>%melt.lang%</code></td><td>Language attribute value</td></tr>
    <tr><td><code>%melt.nonce%</code></td><td>CSP nonce for inline scripts</td></tr>
  </tbody>
</table>

<h2>ViteManifest</h2>
<p>Resolves asset paths from a Vite build manifest file, enabling cache-busted URLs in production.</p>
<pre class="code-block"><code>val manifest = ViteManifest.load("public/dist/.vite/manifest.json")
// Returns Some(ViteManifest) in prod, None in dev</code></pre>

<h2>Locals</h2>
<p>Request-scoped type-safe storage, similar to a typed context map.</p>
<pre class="code-block"><code>val UserKey = LocalKey[User]("user")

// In middleware:
ctx.locals.set(UserKey, currentUser)

// In route handler:
val user = ctx.locals.get(UserKey) // Option[User]</code></pre>
"""

    case "meltkit-ssg" =>
      """
<h1 class="doc-title">Static Site Generation</h1>
<p class="doc-lead">Melt can generate static HTML files at build time using the same MeltKit routing setup you use for SSR. This makes it easy to deploy documentation sites, blogs, and landing pages to any static host.</p>

<h2>How SSG works</h2>
<p>The SSG pipeline walks your MeltKit route tree, renders each page with the JVM SSR renderer, and writes the output to HTML files. The result is a folder of plain HTML files that can be served by any CDN or web server.</p>

<div class="callout callout-info"><strong>Same code, different output:</strong> Your <code>.melt</code> components and MeltKit routes do not change between SSR and SSG modes. The <code>sbt-meltkit</code> plugin handles the switch via the <code>meltMode</code> setting.</div>

<h2>Setup</h2>
<div class="steps">
  <div class="step">
    <div class="step-num">1</div>
    <div class="step-body">
      <strong>Set <code>meltMode</code> to <code>ssg</code></strong>
      <pre class="code-block"><code>// build.sbt
.settings(
  meltMode := MeltMode.SSG
)</code></pre>
    </div>
  </div>
  <div class="step">
    <div class="step-num">2</div>
    <div class="step-body">
      <strong>List all pages to generate</strong>
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
      <strong>Run the generator</strong>
      <pre class="code-block"><code>sbt myProject/meltkitSsgGenerate</code></pre>
    </div>
  </div>
</div>

<h2>SsgPage</h2>
<p>Describes a page to generate. At minimum, specify the path. You can also set a custom output filename.</p>
<pre class="code-block"><code>SsgPage("/en/guide/introduction")
SsgPage("/en/guide/introduction", outFile = "en/guide/introduction/index.html")</code></pre>

<h2>Dynamic pages</h2>
<p>For sites with many pages (e.g. from a CMS or markdown files), generate the page list programmatically.</p>
<pre class="code-block"><code>meltkitSsgPages := {
  val langs  = List("en", "ja")
  val guides = List("introduction", "installation", "quick-start")
  for lang &lt;- langs; slug &lt;- guides
  yield SsgPage(s"/$lang/guide/$slug")
}</code></pre>

<h2>Output directory</h2>
<p>Generated HTML files are written to <code>meltkitSsgOutputDir</code>, which defaults to <code>target/ssg</code>.</p>
<pre class="code-block"><code>meltkitSsgOutputDir := baseDirectory.value / "dist"</code></pre>

<h2>Client-side hydration</h2>
<p>SSG pages are static HTML by default. To add interactivity, include the Vite-built Scala.js bundle and configure hydration.</p>
<pre class="code-block"><code>// index.html
&lt;script type="module"&gt;
  import("./app.js").then(m =&gt; m.hydrate?.())
&lt;/script&gt;</code></pre>
<div class="callout callout-tip"><strong>Tip:</strong> The <code>meltHydrationRoot</code> sbt setting controls which component emits the <code>hydrate</code> export. Set it to your top-level component name for full-page hydration.</div>

<h2>Deploying</h2>
<p>Copy the SSG output directory to any static file host: GitHub Pages, Netlify, Vercel, Cloudflare Pages, or a plain S3 bucket.</p>
<pre class="code-block"><code># Example: deploy to GitHub Pages
sbt meltkitSsgGenerate
cp -r target/ssg/* docs/</code></pre>
"""

    case "compiler" =>
      """
<h1 class="doc-title">Compiler API</h1>
<p class="doc-lead">The Melt compiler transforms <code>.melt</code> source files into Scala code. This page documents the public API surface for integrating the compiler into custom tools, build systems, or language servers.</p>

<h2>MeltCompiler</h2>
<p>The primary entry point. Takes the text of a <code>.melt</code> file and returns generated Scala code plus any warnings or errors.</p>
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
  <thead><tr><th>Field</th><th>Type</th><th>Default</th><th>Description</th></tr></thead>
  <tbody>
    <tr><td><code>componentName</code></td><td><code>String</code></td><td>required</td><td>Scala class name for the generated component</td></tr>
    <tr><td><code>pkg</code></td><td><code>String</code></td><td><code>"components"</code></td><td>Scala package for the generated file</td></tr>
    <tr><td><code>mode</code></td><td><code>String</code></td><td><code>"auto"</code></td><td>Codegen mode: <code>spa</code>, <code>ssr</code>, or <code>auto</code></td></tr>
    <tr><td><code>hydration</code></td><td><code>Boolean</code></td><td><code>false</code></td><td>Emit <code>@JSExportTopLevel("hydrate")</code> exports</td></tr>
    <tr><td><code>hydrationRoot</code></td><td><code>Option[String]</code></td><td><code>None</code></td><td>Root component name for full-page hydration</td></tr>
    <tr><td><code>stylePreprocessor</code></td><td><code>Option[String]</code></td><td><code>None</code></td><td>Class name of a <code>StylePreprocessor</code> implementation</td></tr>
  </tbody>
</table>

<h2>Compilation pipeline</h2>
<p>The compiler processes files in these stages:</p>
<div class="steps">
  <div class="step">
    <div class="step-num">1</div>
    <div class="step-body"><strong>Parse</strong> — <code>MeltParser.parseWithWarnings()</code> splits the file into script, template, and style sections; parses each with <code>TemplateParser</code></div>
  </div>
  <div class="step">
    <div class="step-num">2</div>
    <div class="step-body"><strong>Semantic checks</strong> — Validates attribute names, tag names, raw text interpolation, binding context, and malformed expressions</div>
  </div>
  <div class="step">
    <div class="step-num">3</div>
    <div class="step-body"><strong>Security &amp; A11y checks</strong> — <code>SecurityChecker</code> warns about unsafe iframes, forms, and blank targets; <code>A11yChecker</code> warns about missing ARIA attributes</div>
  </div>
  <div class="step">
    <div class="step-num">4</div>
    <div class="step-body"><strong>CSS preprocessing</strong> — Compiles SCSS if configured, scopes CSS to the component</div>
  </div>
  <div class="step">
    <div class="step-num">5</div>
    <div class="step-body"><strong>AST → IR</strong> — <code>AstToIr.lower()</code> converts <code>TemplateNode</code> AST to the intermediate <code>IrNode</code> representation. <code>StaticHoistPass</code> extracts static subtrees for performance.</div>
  </div>
  <div class="step">
    <div class="step-num">6</div>
    <div class="step-body"><strong>Code generation</strong> — <code>SpaEmitter</code> (Scala.js DOM operations) or <code>SsrEmitter</code> (string builder) produces the final <code>.scala</code> source</div>
  </div>
</div>

<h2>MeltWarning</h2>
<pre class="code-block"><code>case class MeltWarning(
  message:  String,
  severity: Severity,  // Error | Warning | Info
  line:     Option[Int],
  column:   Option[Int]
)</code></pre>

<h2>AST types</h2>
<p>The compiler exposes its AST in <code>melt.ast.MeltAst</code>. You can traverse it to build custom analyses or transforms.</p>
<pre class="code-block"><code>import melt.ast.MeltAst.*

// TemplateNode variants:
// Element, Text, Expression, Component, InlineTemplate,
// Head, Window, Body, Document, DynamicElement,
// Boundary, KeyBlock, SnippetDef, RenderCall

// Attr variants:
// Static, Dynamic, Directive, EventHandler,
// BooleanAttr, Spread, Shorthand</code></pre>
"""

    case "sbt-plugin" =>
      """
<h1 class="doc-title">sbt Plugin</h1>
<p class="doc-lead">The <code>sbt-melt</code> plugin watches for <code>.melt</code> files in your project and compiles them to Scala sources automatically on every build. The <code>sbt-meltkit</code> plugin extends this with MeltKit server integration.</p>

<h2>Installation</h2>
<pre class="code-block"><code>// project/plugins.sbt
addSbtPlugin("io.github.takapi327" % "sbt-melt"    % "0.1.0")
addSbtPlugin("io.github.takapi327" % "sbt-meltkit" % "0.1.0")</code></pre>

<h2>sbt-melt</h2>
<p>Enable the plugin on any sbt project that contains <code>.melt</code> files.</p>
<pre class="code-block"><code>// build.sbt
lazy val client = (project in file("client"))
  .enablePlugins(ScalaJSPlugin, MeltPlugin)
  .settings(
    meltPackage := "components",
    // Optional: set codegen mode explicitly
    meltCodegenMode := "spa"  // "spa" | "ssr" | "auto"
  )</code></pre>

<h3>MeltPlugin settings</h3>
<table class="api-table">
  <thead><tr><th>Key</th><th>Type</th><th>Default</th><th>Description</th></tr></thead>
  <tbody>
    <tr><td><code>meltPackage</code></td><td><code>String</code></td><td><code>"components"</code></td><td>Scala package for all generated files</td></tr>
    <tr><td><code>meltSourceDirectories</code></td><td><code>Seq[File]</code></td><td>unmanagedSourceDirectories</td><td>Directories scanned for <code>.melt</code> files</td></tr>
    <tr><td><code>meltOutputDirectory</code></td><td><code>File</code></td><td><code>target/src_managed/melt</code></td><td>Where generated <code>.scala</code> files are written</td></tr>
    <tr><td><code>meltCodegenMode</code></td><td><code>String</code></td><td><code>"auto"</code></td><td>Codegen mode. <code>auto</code> selects <code>spa</code> for Scala.js, <code>ssr</code> otherwise</td></tr>
    <tr><td><code>meltHydration</code></td><td><code>Boolean</code></td><td><code>false</code></td><td>Emit <code>@JSExportTopLevel("hydrate")</code> on every component</td></tr>
    <tr><td><code>meltHydrationRoot</code></td><td><code>Option[String]</code></td><td><code>None</code></td><td>Root component name for full-page hydration (Approach A)</td></tr>
    <tr><td><code>meltCompilerClasspath</code></td><td><code>Seq[File]</code></td><td>auto-resolved</td><td>Override the compiler classpath (useful in monorepos)</td></tr>
    <tr><td><code>meltStylePreprocessor</code></td><td><code>Option[String]</code></td><td><code>None</code></td><td>Class name of a <code>StylePreprocessor</code> (e.g. <code>SassPreprocessor</code>)</td></tr>
    <tr><td><code>meltGenerate</code></td><td><code>Task[Seq[File]]</code></td><td>—</td><td>Compile all <code>.melt</code> files; returns generated <code>.scala</code> files</td></tr>
  </tbody>
</table>

<h2>sbt-meltkit</h2>
<p>Extends <code>sbt-melt</code> with MeltKit server integration: route generation, Vite manifest handling, and SSG.</p>
<pre class="code-block"><code>lazy val server = (project in file("server"))
  .enablePlugins(MeltkitPlugin)
  .settings(
    meltMode := MeltMode.SSR,
    meltkitViteDistDir := (client / baseDirectory).value / "dist"
  )</code></pre>

<h3>MeltkitPlugin settings</h3>
<table class="api-table">
  <thead><tr><th>Key</th><th>Type</th><th>Default</th><th>Description</th></tr></thead>
  <tbody>
    <tr><td><code>meltMode</code></td><td><code>MeltMode</code></td><td><code>Auto</code></td><td><code>SPA</code>, <code>SSR</code>, <code>SSG</code>, or <code>Auto</code></td></tr>
    <tr><td><code>meltkitViteDistDir</code></td><td><code>File</code></td><td>—</td><td>Path to Vite build output directory</td></tr>
    <tr><td><code>meltkitViteManifestPath</code></td><td><code>File</code></td><td><code>dist/.vite/manifest.json</code></td><td>Path to Vite manifest file</td></tr>
    <tr><td><code>meltkitSsgPages</code></td><td><code>List[SsgPage]</code></td><td><code>Nil</code></td><td>List of pages to generate for SSG</td></tr>
    <tr><td><code>meltkitSsgOutputDir</code></td><td><code>File</code></td><td><code>target/ssg</code></td><td>SSG output directory</td></tr>
    <tr><td><code>meltkitSsgGenerate</code></td><td><code>Task[Unit]</code></td><td>—</td><td>Generate all static pages</td></tr>
  </tbody>
</table>

<h2>Monorepo setup</h2>
<p>In a monorepo where the compiler is a project dependency, override <code>meltCompilerClasspath</code> to skip <code>publishLocal</code>.</p>
<pre class="code-block"><code>// build.sbt — point at compiled classes directly
meltCompilerClasspath := (codegenJVM / Compile / fullClasspath).value.files</code></pre>

<h2>SCSS support</h2>
<p>Enable SCSS preprocessing for <code>&lt;style lang="scss"&gt;</code> blocks.</p>
<pre class="code-block"><code>// project/plugins.sbt
addSbtPlugin("io.github.takapi327" % "sbt-melt" % "0.1.0")

// build.sbt
meltStylePreprocessor := Some(SassPreprocessor)</code></pre>

<h2>incremental compilation</h2>
<p>The plugin only recompiles <code>.melt</code> files that have changed since the last build. Generated <code>.scala</code> files are tracked as managed sources and fed into the normal Scala compilation pipeline automatically.</p>
"""

    case _ => s"""<h1 class="doc-title">${ titles.getOrElse(slug, slug) }</h1><p>Documentation coming soon.</p>"""
