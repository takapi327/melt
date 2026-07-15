/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package docs

object GuideCodes:

  // ── Introduction ──────────────────────────────────────────────────────────

  val introCounter: String =
    """|<script lang="scala">
       |  val count   = State(0)
       |  val doubled = count.map(_ * 2)
       |</script>
       |
       |<div class="counter">
       |  <h1>{count}</h1>
       |  <p>doubled: {doubled}</p>
       |  <button onclick={_ => count += 1}>+</button>
       |  <button onclick={_ => count -= 1}>-</button>
       |</div>
       |
       |<style>
       |  h1 { font-size: 4rem; color: #d6526a; }
       |</style>""".stripMargin

  // ── Installation ──────────────────────────────────────────────────────────

  val installPluginsSbt: String =
    """addSbtPlugin("io.github.takapi327" % "sbt-melt" % "0.1.0-SNAPSHOT")"""

  val installBuildSbt: String =
    """|// Scala.js frontend module
       |lazy val client = project
       |  .in(file("client"))
       |  .enablePlugins(ScalaJSPlugin, MeltPlugin)
       |  .settings(
       |    scalaVersion := "3.3.7"
       |  )""".stripMargin

  val installProjectLayout: String =
    """|my-project/
       |├── build.sbt
       |├── project/
       |│   └── plugins.sbt
       |└── client/
       |    └── src/main/scala/
       |        └── myapp/
       |            ├── Counter.melt      ← your component
       |            └── App.melt""".stripMargin

  val installViteConfig: String =
    """|import { defineConfig } from 'vite'
       |
       |export default defineConfig({
       |  publicDir: 'public',
       |  build: {
       |    rollupOptions: {
       |      input: 'index.html',
       |    }
       |  }
       |})""".stripMargin

  // ── Quick Start ───────────────────────────────────────────────────────────

  val quickStartCounterMelt: String =
    """|<script lang="scala">
       |  val count = State(0)
       |</script>
       |
       |<div>
       |  <p>Count: {count}</p>
       |  <button onclick={_ => count += 1}>Increment</button>
       |  <button onclick={_ => count.set(0)}>Reset</button>
       |</div>""".stripMargin

  val quickStartMainScala: String =
    """|import org.scalajs.dom
       |import melt.runtime.Mount
       |import scala.scalajs.js.annotation.JSExportTopLevel
       |
       |@JSExportTopLevel("main")
       |def main(): Unit =
       |  Mount(dom.document.getElementById("app").asInstanceOf[dom.Element], Counter(Counter.Props()))""".stripMargin

  val quickStartIndexHtml: String =
    """|<!DOCTYPE html>
       |<html lang="en">
       |  <head><meta charset="UTF-8" /><title>My App</title></head>
       |  <body>
       |    <div id="app"></div>
       |    <script type="module">
       |      import { main } from './target/.../main.js'
       |      main()
       |    </script>
       |  </body>
       |</html>""".stripMargin

  val quickStartRunCmds: String =
    """|# Compile with sbt
       |sbt client/fastLinkJS
       |
       |# Serve with Vite (or any static server)
       |npx vite""".stripMargin

  // ── Components ────────────────────────────────────────────────────────────

  val componentsGreetingMelt: String =
    """|<!-- 1. Script section: Scala logic -->
       |<script lang="scala">
       |  case class Props(name: String = "World")
       |  val greeting = "Hello"
       |</script>
       |
       |<!-- 2. Template: HTML with embedded expressions -->
       |<p class="msg">{greeting}, {props.name}!</p>
       |
       |<!-- 3. Style section: scoped CSS -->
       |<style>
       |  .msg { font-size: 1.25rem; color: #d6526a; }
       |</style>""".stripMargin

  val componentsButtonMelt: String =
    """|<script lang="scala">
       |  case class Props(
       |    label:    String  = "Click me",
       |    disabled: Boolean = false
       |  )
       |</script>
       |
       |<button disabled={props.disabled}>{props.label}</button>""".stripMargin

  val componentsPropsAccess: String =
    """|props.label    // String
       |props.disabled // Boolean""".stripMargin

  val componentsAppMelt: String =
    """|<script lang="scala">
       |  import myapp.Button
       |</script>
       |
       |<div>
       |  <Button label="Save" />
       |  <Button label="Cancel" disabled={true} />
       |</div>""".stripMargin

  val componentsCardMelt: String =
    """|<div class="card">
       |  {children}
       |</div>
       |
       |<style>
       |  .card { padding: 24px; border: 1px solid #ccc; border-radius: 8px; }
       |</style>""".stripMargin

  val componentsCardUsage: String =
    """|<!-- Usage -->
       |<Card>
       |  <h2>Title</h2>
       |  <p>Any content here.</p>
       |</Card>""".stripMargin

  // ── Template Syntax ───────────────────────────────────────────────────────

  val templateExprExample: String =
    """|<p>{count}</p>
       |<p>{count.map(_ * 2)}</p>
       |<p>{"Hello, " + props.name + "!"}</p>""".stripMargin

  val templateAttrExample: String =
    """|<img src={imageUrl} alt={props.alt} />
       |<input type="text" placeholder={hint} />""".stripMargin

  val templateTwoWayExample: String =
    """|<script lang="scala">
       |  val name = State("")
       |</script>
       |
       |<input type="text" bind:value={name} placeholder="Your name" />
       |<p>Hello, {name}!</p>""".stripMargin

  val templateClassExample: String =
    """<button class:active={isActive}>Click me</button>"""

  val templateClassMultiExample: String =
    """<div class="item" class:selected={isSelected} class:disabled={isDisabled}>"""

  val templateStyleExample: String =
    """<div style:color={textColor} style:font-size={fontSize + "px"}>"""

  val templateEventsExample: String =
    """|<button onclick={_ => count += 1}>+</button>
       |<input oninput={e => name.set(e.target.value)} />
       |<form onsubmit={e => { e.preventDefault(); submit() }}>""".stripMargin

  val templateSpreadExample: String =
    """|<script lang="scala">
       |  val attrs = Map("role" -> "button", "aria-label" -> "Close")
       |</script>
       |
       |<div {...attrs}></div>""".stripMargin

  val templateRefExample: String =
    """|<script lang="scala">
       |  import melt.runtime.Ref
       |  val inputRef = Ref.empty[dom.html.Input]
       |</script>
       |
       |<input bind:this={inputRef} />
       |<button onclick={_ => inputRef.foreach(_.focus())}>Focus</button>""".stripMargin

  // ── Reactivity ────────────────────────────────────────────────────────────

  val reactivityStateExample: String =
    """|val count   = State(0)          // State[Int]
       |val name    = State("Alice")    // State[String]
       |val items   = State(List[String]()) // State[List[String]]""".stripMargin

  val reactivityStateRead: String =
    """|val n: Int = count.value  // explicit
       |val n: Int = count        // implicit conversion also works""".stripMargin

  val reactivityMutateExample: String =
    """|count.set(10)             // replace
       |count.update(_ + 1)       // transform
       |count += 1                // shorthand for Int/Long/Double
       |count -= 1
       |name += " Smith"          // string append
       |
       |// List operations
       |items.append("new item")
       |items.prepend("first")
       |items.removeWhere(_.isEmpty)
       |items.clear()""".stripMargin

  val reactivitySignalExample: String =
    """|val doubled: Signal[Int]    = count.map(_ * 2)
       |val upper:   Signal[String] = name.map(_.toUpperCase)
       |val isEmpty: Signal[Boolean] = items.map(_.isEmpty)""".stripMargin

  val reactivitySignalTemplate: String =
    """<p>{count} × 2 = {doubled}</p>"""

  val reactivityDomExample: String =
    """|<script lang="scala">
       |  val count = State(0)
       |</script>
       |
       |<!-- Only this text node re-renders when count changes -->
       |<p>Count: {count}</p>
       |
       |<!-- This element is static and never re-renders -->
       |<p>This text never changes.</p>""".stripMargin

  // ── Computed ──────────────────────────────────────────────────────────────

  val computedMapExample: String =
    """|val count:   State[Int]   = State(0)
       |val doubled: Signal[Int]  = count.map(_ * 2)
       |val label:   Signal[String] = count.map(n => if n == 0 then "zero" else s"$n")""".stripMargin

  val computedMapTemplate: String =
    """|<p>{count} doubled is {doubled}</p>
       |<p>Label: {label}</p>""".stripMargin

  val computedFlatMapExample: String =
    """|val mode   = State("spa")
       |val output = source.flatMap { code =>
       |  mode.map { m =>
       |    compile(code, m)  // re-runs when either source OR mode changes
       |  }
       |}""".stripMargin

  val computedMemoExample: String =
    """|val isEven: Signal[Boolean] = count.memo(_ % 2 == 0)
       |// Only triggers updates when the boolean flips, not on every count change""".stripMargin

  val computedCombineExample: String =
    """|val firstName = State("Alice")
       |val lastName  = State("Smith")
       |val fullName  = firstName.flatMap(f => lastName.map(l => s"$f $l"))""".stripMargin

  // ── Effects ───────────────────────────────────────────────────────────────

  val effectsBasicExample: String =
    """|<script lang="scala">
       |  val query = State("")
       |
       |  effect(query) { q =>
       |    println(s"Searching for: $q")
       |    fetchResults(q)
       |  }
       |</script>""".stripMargin

  val effectsMultiExample: String =
    """|val x = State(0)
       |val y = State(0)
       |
       |effect(x, y) { (xVal, yVal) =>
       |  println(s"position: ($xVal, $yVal)")
       |  updatePosition(xVal, yVal)
       |}""".stripMargin

  val effectsCleanupExample: String =
    """|val enabled = State(false)
       |
       |effect(enabled) { _ =>
       |  val id = js.timers.setInterval(1000) { count += 1 }
       |  onCleanup(() => js.timers.clearInterval(id))
       |}""".stripMargin

  // ── Events ────────────────────────────────────────────────────────────────

  val eventsBasicHandlers: String =
    """|<button onclick={_ => count += 1}>Increment</button>
       |<button onclick={_ => count.set(0)}>Reset</button>""".stripMargin

  val eventsObjExample: String =
    """|<input oninput={e => name.set(e.target.asInstanceOf[dom.html.Input].value)} />
       |<form onsubmit={e => { e.preventDefault(); submit() }}></form>""".stripMargin

  val eventsBindValueExample: String =
    """|val text = State("")
       |// ...
       |<input type="text" bind:value={text} />""".stripMargin

  val eventsWindowExample: String =
    """<melt:window onkeydown={e => handleShortcut(e)} />"""

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  val lifecycleOnMountExample: String =
    """|<script lang="scala">
       |  import melt.runtime.Ref
       |
       |  val canvasRef = Ref.empty[dom.html.Canvas]
       |
       |  onMount {
       |    canvasRef.foreach { canvas =>
       |      // canvas is now in the DOM — safe to measure or draw
       |      val ctx = canvas.getContext("2d")
       |      // ...
       |    }
       |  }
       |</script>
       |
       |<canvas bind:this={canvasRef}></canvas>""".stripMargin

  val lifecycleCleanupExample: String =
    """|onMount {
       |  val id = js.timers.setInterval(1000) { tick() }
       |  onCleanup(() => js.timers.clearInterval(id))
       |}""".stripMargin

  val lifecycleEffectCleanupExample: String =
    """|val id = State[Option[Int]](None)
       |
       |effect(id) { idOpt =>
       |  idOpt.foreach { currentId =>
       |    val ws = new WebSocket(s"wss://api.example.com/feed/$currentId")
       |    onCleanup(() => ws.close())
       |  }
       |}""".stripMargin

  // ── Control Flow ──────────────────────────────────────────────────────────

  val controlFlowCondExample: String =
    """|<script lang="scala">
       |  val loggedIn = State(false)
       |</script>
       |
       |{loggedIn.map { logged =>
       |  if logged then
       |    <p>Welcome back!</p>
       |  else
       |    <a href="/login">Sign in</a>
       |}}""".stripMargin

  val controlFlowListExample: String =
    """|<script lang="scala">
       |  case class Item(id: Int, name: String)
       |  val items = State(List(Item(1, "Apple"), Item(2, "Banana")))
       |</script>
       |
       |<ul>
       |  {items.map(_.map((item: Item) =>
       |    <li>{item.id}: {item.name}</li>
       |  ))}
       |</ul>""".stripMargin

  val controlFlowKeyBlockExample: String =
    """|<melt:key this={selectedId}>
       |  <DetailPanel id={selectedId} />
       |</melt:key>""".stripMargin

  val controlFlowEmptyExample: String =
    """|{items.map { list =>
       |  if list.isEmpty then
       |    <p class="empty">No items yet.</p>
       |  else
       |    <ul>{list.map((item: Item) => <li>{item.name}</li>)}</ul>
       |}}""".stripMargin

  // ── Special Elements ──────────────────────────────────────────────────────

  val specialHeadExample: String =
    """|<melt:head>
       |  <title>{pageTitle}</title>
       |  <meta name="description" content={description} />
       |</melt:head>""".stripMargin

  val specialWindowBodyExample: String =
    """|<melt:window
       |  onkeydown={e => handleKey(e)}
       |  onresize={_ => recalcLayout()}
       |/>
       |
       |<melt:body
       |  onmousedown={e => trackClick(e)}
       |/>""".stripMargin

  val specialBoundaryExample: String =
    """|<melt:boundary>
       |  <melt:pending>
       |    <p>Loading...</p>
       |  </melt:pending>
       |  <melt:failed let:error>
       |    <p>Error: {error.message}</p>
       |  </melt:failed>
       |  <AsyncComponent />
       |</melt:boundary>""".stripMargin

  val specialElementExample: String =
    """|<script lang="scala">
       |  val tag = State("div")
       |</script>
       |
       |<melt:element this={tag} class="wrapper">
       |  {children}
       |</melt:element>""".stripMargin

  val specialSnippetExample: String =
    """|<script lang="scala">
       |  // snippets are defined in the template section below
       |</script>
       |
       |{#snippet badge(label: String)}
       |  <span class="badge">{label}</span>
       |{/snippet}
       |
       |<div>
       |  {@render badge("New")}
       |  {@render badge("Hot")}
       |</div>""".stripMargin

  // ── Transitions ───────────────────────────────────────────────────────────

  val transitionsTweenExample: String =
    """|<script lang="scala">
       |  import melt.runtime.motion.Tween
       |
       |  val opacity = Tween(0.0, duration = 400)
       |  opacity.subscribe { v => /* reactive DOM updates via effect */ }
       |</script>
       |
       |<button onclick={_ => opacity.set(1.0)}>Fade in</button>
       |<button onclick={_ => opacity.set(0.0)}>Fade out</button>""".stripMargin

  val transitionsSpringExample: String =
    """|import melt.runtime.motion.Spring
       |
       |val smooth = Spring(0.0, stiffness = 0.15, damping = 0.8)
       |smooth.set(100.0)""".stripMargin

  val transitionsCssExample: String =
    """|<!-- Template -->
       |<div class="panel" class:open={isOpen}>
       |  {children}
       |</div>
       |
       |<!-- Style -->
       |<style>
       |  .panel {
       |    max-height: 0;
       |    overflow: hidden;
       |    transition: max-height 0.3s ease;
       |  }
       |  .panel.open { max-height: 500px; }
       |</style>""".stripMargin

  // ── Trusted HTML ──────────────────────────────────────────────────────────

  val trustedHtmlXssExample: String =
    """|val userInput = "<script>alert('xss')</script>"
       |// ...
       |<p>{userInput}</p>
       |// Renders: <p>&lt;script&gt;...&lt;/p>  ← SAFE""".stripMargin

  val trustedHtmlUnsafeExample: String =
    """|<script lang="scala">
       |  import melt.runtime.TrustedHtml
       |
       |  // Only use for content you trust!
       |  val richContent = TrustedHtml.unsafe("<strong>Bold</strong> text")
       |</script>
       |
       |<div bind:innerHTML={richContent}></div>""".stripMargin

  val trustedHtmlSanitizeExample: String =
    """|import melt.runtime.TrustedHtml
       |
       |val safeHtml = TrustedHtml.sanitize(
       |  userMarkdown,
       |  html => mySanitizer.clean(html)  // your sanitizer here
       |)""".stripMargin

  val trustedUrlExample: String =
    """|import melt.runtime.TrustedUrl
       |
       |val link = TrustedUrl.unsafe("https://example.com")
       |// ...
       |<a href={link}>Visit</a>""".stripMargin

  // ── CSS ───────────────────────────────────────────────────────────────────

  val cssScopedCardMelt: String =
    """|<div class="card"><p>Content</p></div>
       |<style>
       |  /* Only applies to elements in THIS component */
       |  .card { border: 1px solid #ccc; }
       |  p     { color: grey; }
       |</style>""".stripMargin

  val cssGeneratedHtml: String =
    """|<div class="card" data-melt-abc123>
       |  <p data-melt-abc123>Content</p>
       |</div>""".stripMargin

  val cssDynamicStyleExample: String =
    """|<script lang="scala">
       |  val hue = State(200)
       |</script>
       |
       |<div style:background-color={"hsl(" + hue + ", 60%, 50%)"}></div>
       |<input type="range" bind:value={hue} min="0" max="360" />""".stripMargin

  val cssCustomPropExample: String =
    """|<script lang="scala">
       |  val progress = State(0.0)
       |</script>
       |
       |<div class="bar" style:--progress={progress + "%"}></div>
       |
       |<style>
       |  .bar::before {
       |    width: var(--progress);
       |    background: var(--accent);
       |  }
       |</style>""".stripMargin

  val cssScssExample: String =
    """|<style lang="scss">
       |  $primary: #d6526a;
       |
       |  .card {
       |    &:hover { background: lighten($primary, 40%); }
       |    &__title { color: $primary; }
       |  }
       |</style>""".stripMargin

  // ── Testing ───────────────────────────────────────────────────────────────

  val testingDepExample: String =
    """libraryDependencies += "io.github.takapi327" %% "melt-testkit" % "0.1.0-SNAPSHOT" % Test"""

  val testingCounterSpec: String =
    """|import melt.testkit.*
       |
       |class CounterSpec extends MeltSuite:
       |
       |  test("counter starts at zero") {
       |    val c = mount(Counter(Counter.Props()))
       |    assertEquals(c.text("h1"), "0")
       |  }
       |
       |  test("increment button updates count") {
       |    val c = mount(Counter(Counter.Props()))
       |    c.click("button:first-child")
       |    assertEquals(c.text("h1"), "1")
       |  }
       |
       |  test("reset returns to zero") {
       |    val c = mount(Counter(Counter.Props()))
       |    c.click("button:first-child")
       |    c.click("button:last-child")
       |    assertEquals(c.text("h1"), "0")
       |  }""".stripMargin

  val testingFormProbe: String =
    """|import meltkit.adapter.http4s.FormProbe
       |import meltkit.adapter.http4s.Http4sAdapter.given
       |
       |val probe = FormProbe(app)
       |
       |// native redirect (Post/Redirect/Get)
       |probe.submit("login", fields = Map("email" -> "a@b.com", "password" -> "secret")).map { r =>
       |  assertEquals(r.status, 303)
       |  assertEquals(r.location, Some("/dashboard"))
       |}
       |
       |// use:enhance → JSON envelope; named action via ?/publish
       |probe.submit("posts", action = "publish", enhance = true).map(r => assert(r.contains("\"redirect\"")))
       |
       |// CSRF: a cross-site Origin (≠ Host) is rejected
       |probe.submit("login", origin = Some("https://evil.example"), host = Some("localhost:3000"))
       |     .map(r => assertEquals(r.status, 403))""".stripMargin

  val testingFormEnhance: String =
    """|import melt.testkit.*
       |
       |class LoginSpec extends MeltSuite:
       |  test("a validation failure shows the error without a reload") {
       |    val page = mount(LoginPage(LoginPage.Props()))
       |    val body = EnhanceResult.failure(422, "{\"errors\":[\"invalid email\"]}")
       |    val stub = FetchStub.install(body = body)
       |    page.userEvent.submit("form")
       |    // ...await the microtask, then:
       |    assertEquals(page.text(".error"), "invalid email")
       |    stub.restore()
       |  }""".stripMargin

  val testingFormCodec: String =
    """|import melt.runtime.forms.codec.FieldCodec
       |import meltkit.FormData
       |import meltkit.codec.FormDataDecoder
       |
       |// a codec round-trips: decode(encode(a)) == a
       |assertEquals(FieldCodec[Email].roundTrip(Email("a@b.com")), Right(Email("a@b.com")))
       |
       |// a form body decodes to the case class
       |assertEquals(
       |  FormDataDecoder[LoginForm].decode(FormData.parse("email=a@b.com&password=x").toOption.get),
       |  Right(LoginForm("a@b.com", "x"))
       |)""".stripMargin

  // ── Routing ───────────────────────────────────────────────────────────────

  val routingDepExample: String =
    """libraryDependencies += "io.github.takapi327" %% "meltkit-adapter-http4s" % "0.1.0-SNAPSHOT""""

  val routingMainScala: String =
    """|import meltkit.*
       |import scala.concurrent.Future
       |import scala.concurrent.ExecutionContext.Implicits.global
       |
       |val app = MeltKit[Future]()
       |
       |// Static route
       |app.get("") { ctx =>
       |  Future.successful(ctx.render(Home(Home.Props())))
       |}
       |
       |// Dynamic route with a path parameter
       |private val id = param[String]("id")
       |
       |app.get("users" / id) { ctx =>
       |  val userId = ctx.params.id
       |  Future.successful(ctx.render(UserPage(UserPage.Props(id = userId))))
       |}""".stripMargin

  val routingPathParamsExample: String =
    """|private val lang    = param[String]("lang")
       |private val section = param[String]("section")
       |
       |app.get(lang / "guide" / section) { ctx =>
       |  val l = ctx.params.lang
       |  val s = ctx.params.section
       |  Future.successful(ctx.render(GuidePage(GuidePage.Props(lang = l, slug = s))))
       |}""".stripMargin

  val routingPageOptionsExample: String =
    """|val opts = PageOptions(
       |  ssr       = true,         // render on the server
       |  csr       = true,         // hydrate on the client
       |  prerender = PrerenderOption.On,  // generate at build time
       |  entries   = List("/en/guide/introduction", "/ja/guide/introduction")
       |)
       |
       |app.get(lang / "guide" / slug, opts) { ctx => ... }""".stripMargin

  // ── SSR ───────────────────────────────────────────────────────────────────

  val ssrBuildSbt: String =
    """|// build.sbt (server module)
       |lazy val server = project
       |  .enablePlugins(MeltkitPlugin)
       |  .settings(meltMode := Some(Http4s))
       |
       |// build.sbt (client module — for hydration)
       |lazy val client = project
       |  .enablePlugins(MeltkitPlugin)
       |  .settings(meltMode := Some(Browser))""".stripMargin

  val ssrRouteExample: String =
    """|app.get("blog" / slug, PageOptions(ssr = true, csr = true)) { ctx =>
       |  fetchPost(ctx.params.slug).map { post =>
       |    ctx.render(BlogPost(BlogPost.Props(post = post)))
       |  }
       |}""".stripMargin

  val ssrPropsExample: String =
    """|case class Props(title: String, count: Int)
       |// Codec is derived automatically for case classes with simple types""".stripMargin

  val ssrViteConfig: String =
    """|// vite.config.mjs
       |export default defineConfig({
       |  build: {
       |    rollupOptions: {
       |      preserveEntrySignatures: 'exports-only'
       |    }
       |  }
       |})""".stripMargin

  // ── SSG ───────────────────────────────────────────────────────────────────

  val ssgPrerenderExample: String =
    """|private val langs  = List("en", "ja")
       |private val guides = List("introduction", "installation", ...)
       |private val On     = PageOptions(prerender = PrerenderOption.On)
       |
       |app.get(
       |  lang / "guide" / slug,
       |  On.copy(entries = for l <- langs; g <- guides yield s"/$l/guide/$g")
       |) { ctx => ... }""".stripMargin

  val ssgMainScala: String =
    """|import meltkit.*
       |import meltkit.ssg.*
       |import meltkit.syntax.*
       |import java.nio.file.Path
       |
       |@main def generate(): Unit =
       |  val config = ServerConfig(
       |    outputDir = Some("dist"),
       |    publicDir = Some("public"),
       |    assetsDir = Some("../dist/assets"),
       |    manifest  = ViteManifest.fromFile(Path.of("../dist/.vite/manifest.json")),
       |    template  = Template.fromResource("index.html")
       |  )
       |  SsgGenerator.run(app, config)""".stripMargin

  val ssgRunCmd: String =
    """sbt "server/runMain generate""""

  val ssgOutputStructure: String =
    """|dist/
       |├── index.html
       |├── en/
       |│   ├── guide/
       |│   │   ├── introduction/index.html
       |│   │   └── ...
       |│   └── ...
       |└── assets/
       |    ├── main.js
       |    └── main.css""".stripMargin

  // ── Adapters ──────────────────────────────────────────────────────────────

  val adaptersHttp4sDep: String =
    """libraryDependencies += "io.github.takapi327" %% "meltkit-adapter-http4s" % "0.1.0-SNAPSHOT""""

  val adaptersHttp4sSetup: String =
    """|import meltkit.adapter.http4s.Http4sAdapter
       |import org.http4s.ember.server.EmberServerBuilder
       |import cats.effect.IO
       |
       |val routes = Http4sAdapter.routes[IO](app)
       |
       |EmberServerBuilder.default[IO]
       |  .withHttpApp(routes.orNotFound)
       |  .build
       |  .useForever""".stripMargin

  val adaptersNodeDep: String =
    """libraryDependencies += "io.github.takapi327" %% "meltkit-adapter-node" % "0.1.0-SNAPSHOT""""

  val adaptersBrowserDep: String =
    """libraryDependencies += "io.github.takapi327" %% "meltkit-adapter-browser" % "0.1.0-SNAPSHOT""""

  // ── Form Actions ────────────────────────────────────────────────────────────

  val formActionsServerSingle: String =
    """|import meltkit.*
       |
       |case class LoginForm(email: String, password: String, errors: List[String] = Nil)
       |  derives FormDataDecoder, PropsCodec
       |
       |// One form → one default `action`. GET renders with `form = None`;
       |// POST runs `action`, which returns an ActionResult.
       |app.page("login")(
       |  render = (_, form) => LoginPage(LoginPage.Props(form = form)),
       |  action = ctx =>
       |    ctx.body.form[LoginForm].map {
       |      case Right(f) if !f.email.contains("@") =>
       |        fail(422, f.copy(errors = List("Enter a valid email address")))
       |      case Right(_)  => ActionResult.Redirect("/dashboard")   // 303 PRG
       |      case Left(err) => fail(400, LoginForm("", "", List(err.message)))
       |    }
       |)""".stripMargin

  val formActionsServerNamed: String =
    """|// Multiple submit buttons on one form:
       |//   <button formaction="?/save">   /   <button formaction="?/publish">
       |// `actions` is a partial function over (actionName, ctx); both cases
       |// share the same PostForm.
       |app.page("posts")(
       |  render  = (_, form) => PostEditorPage(PostEditorPage.Props(form = form)),
       |  actions = {
       |    case ("save", ctx) =>
       |      ctx.body.form[PostForm].map {
       |        case Right(_)  => ActionResult.Redirect("/result/draft")
       |        case Left(err) => fail(400, PostForm("", "", List(err.message)))
       |      }
       |    case ("publish", ctx) =>
       |      ctx.body.form[PostForm].map {
       |        case Right(f) if f.body.length < 10 =>
       |          fail(422, f.copy(errors = List("Body is too short to publish")))
       |        case Right(_)  => ActionResult.Redirect("/result/published")
       |        case Left(err) => fail(400, PostForm("", "", List(err.message)))
       |      }
       |  }
       |)""".stripMargin

  val formActionsControls: String =
    """|<!-- text input: name + seeded value -->
       |<input {...form.text(_.email)} type="email"/>
       |
       |<!-- checkbox: name + checked (from a Boolean field) -->
       |<input {...form.checkbox(_.remember)}/>
       |
       |<!-- radio: value + checked when the field equals this option -->
       |<input {...form.radio(_.role, "admin")}/> Admin
       |<input {...form.radio(_.role, "user")}/> User
       |
       |<!-- select + option: name on the select, selected on the matching option -->
       |<select {...form.select(_.role)}>
       |  <option {...form.option(_.role, "admin")}>admin</option>
       |  <option {...form.option(_.role, "user")}>user</option>
       |</select>
       |
       |<!-- textarea: one-way child interpolation (escaped on SSR) -->
       |<textarea name={form.nameOf(_.bio)}>{form.data.value.bio}</textarea>""".stripMargin

  val formActionsCustomCodec: String =
    """|import melt.runtime.forms.codec.FieldCodec
       |
       |enum Role:
       |  case Admin, User
       |
       |// One codec drives both server decode and form.text encode.
       |given FieldCodec[Role] = FieldCodec[String].eimap {
       |  case "admin" => Right(Role.Admin)
       |  case "user"  => Right(Role.User)
       |  case other   => Left(s"invalid role: $other")
       |}(_.toString.toLowerCase)
       |
       |// A wrapper type via a single field selector:
       |case class Email(value: String)
       |given FieldCodec[Email] =
       |  FieldCodec[String].eimap(s =>
       |    if s.contains("@") then Right(Email(s)) else Left("invalid email")
       |  )(_.value)
       |
       |// Now usable in a form type:
       |case class SignupForm(email: Email, role: Role) derives FormDataDecoder, PropsCodec
       |
       |// Nested case classes decode from hierarchical `field.subfield` keys:
       |case class Address(city: String, zip: String) derives FormDataDecoder, PropsCodec
       |case class User(name: String, address: Address) derives FormDataDecoder, PropsCodec
       |// <input name="address.city"/> etc. — or name={form.nameOf(_.address.city)}""".stripMargin

  val formActionsCsrf: String =
    """|import meltkit.*
       |
       |// Reject state-changing form POSTs whose Origin does not match the server.
       |app.use(ServerHook.csrf[IO]())
       |
       |// Allow extra origins (e.g. a separate front-end host):
       |app.use(ServerHook.csrf[IO](CsrfConfig(trustedOrigins = Set("https://app.example.com"))))""".stripMargin

  val formActionsClient: String =
    """|<script lang="scala">
       |import melt.runtime.forms.{ Form, text }
       |import meltkit.enhance
       |
       |case class Props(form: Option[LoginForm] = None)
       |
       |val form = Form(props.form.getOrElse(LoginForm("", "", Nil)))
       |</script>
       |
       |<!-- Plain <form method="post"> works with JS disabled (native POST → PRG).
       |     use:enhance upgrades it to a fetch that updates `form` in place. -->
       |<form method="post" use:enhance={form}>
       |  <!-- `{...form.text(_.email)}` spreads `name` + the seeded `value` from
       |       one type-checked selector; `_.emial` is a compile error, and the
       |       name always matches the field the server decodes. -->
       |  <input {...form.text(_.email)} type="email"/>
       |
       |  <!-- Reactivity: pass a State/Signal (subscribes), not a `.value`
       |       (reads once). The `if form.data.value.… then` conditional makes
       |       `form.data` the reactive source, so validation errors re-render. -->
       |  {if form.data.value.errors.nonEmpty then
       |     <p class="error">{form.data.value.errors.head}</p>
       |   else <span></span>}
       |
       |  <button type="submit" disabled={form.submitting}>
       |    {form.submitting.map(busy => if busy then "Signing in…" else "Sign in")}
       |  </button>
       |</form>""".stripMargin

  // ── Server Functions ────────────────────────────────────────────────────────

  val serverFunctionsContract: String =
    """|// shared: one type-safe contract, compiled for both server and client
       |object Api:
       |  val list = ServerFn.query[Unit, List[Post]]("posts.list")
       |  val like = ServerFn.command[Int, Post]("posts.like")""".stripMargin

  val serverFunctionsServe: String =
    """|// server (JVM): implement each function. `impl` may use a DB or secrets —
       |// it never reaches the browser bundle.
       |app.serve(Api.list) { (_, ctx)  => postRepo.all }
       |app.serve(Api.like) { (id, ctx) => postRepo.incLike(id) }
       |
       |// loader: seed the query as a page prop
       |app.get("") { ctx =>
       |  postRepo.all.map(posts => ctx.render(PostsPage(PostsPage.Props(posts = posts))))
       |}""".stripMargin

  val serverFunctionsClient: String =
    """|<script lang="scala">
       |import melt.runtime.Async
       |import meltkit.*
       |
       |case class Props(posts: List[Post] = Nil)
       |
       |// Seeded from the loader prop: SSR renders the list, the client hydrates
       |// it as Async.Done with no flash and no extra fetch.
       |val posts = Api.list.seeded(props.posts)
       |</script>
       |
       |{posts.state.value match
       |  case Async.Loading    => <p>Loading…</p>
       |  case Async.Failed(e)  => <p class="error">{e.getMessage}</p>
       |  case Async.Done(list) => <ul>{list.map(p => <li>{p.title}</li>)}</ul>
       |}
       |<button onclick={_ => posts.refresh()}>Reload</button>""".stripMargin

  val serverFunctionsSingleFlight: String =
    """|// In an event handler. One round-trip both likes the post and refreshes
       |// the list; the like count bumps immediately and rolls back on failure.
       |<button onclick={_ =>
       |  Api.like.dispatch(post.id)
       |    .optimistic(posts)(list =>
       |      for p <- list yield if p.id == post.id then p.copy(likes = p.likes + 1) else p)
       |    .run()
       |}>Like</button>""".stripMargin

  val serverFunctionsIssues: String =
    """|// A form model carrying per-field issues (Map[String, List[String]]).
       |case class NewPost(title: String, body: String, errors: Map[String, List[String]] = Map.empty)
       |  derives FormDataDecoder, PropsCodec
       |
       |// server action: return per-field messages on failure
       |app.page("new")(
       |  render = (_, form: Option[NewPost]) => NewPostPage(NewPostPage.Props(form = form)),
       |  action = ctx => ctx.body.form[NewPost].flatMap {
       |    case Right(f) if f.title.trim.isEmpty =>
       |      IO.pure(fail(422, f.copy(errors = Map("title" -> List("Title is required")))))
       |    case Right(f) => save(f).as(ActionResult.Redirect("/"))
       |    case Left(e)  => IO.pure(fail(400, NewPost("", "", Map("_form" -> e.messages))))
       |  }
       |)
       |
       |// component: show each field's issue next to its input
       |{if form.data.value.errors.contains("title") then
       |   <p class="error">{form.data.value.errors("title").head}</p>
       | else <span></span>}""".stripMargin
