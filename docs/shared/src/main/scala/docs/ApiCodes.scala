/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package docs

object ApiCodes:

  // ── Template Syntax ──────────────────────────────────────────────────────────

  val templateTextInterp: String =
    """|<p>Hello, {name}!</p>
       |<span>{count * 2}</span>
       |<div>{if loggedIn then "Welcome back" else "Please sign in"}</div>""".stripMargin

  val templateAttrBinding: String =
    """|<a href={url}>Link</a>
       |<input type="text" value={name} placeholder={hint}/>
       |<img src={avatarUrl} alt={altText}/>""".stripMargin

  val templateBoolAttr: String =
    """|<input disabled={!isEnabled}/>
       |<button hidden={!showButton}>Click</button>""".stripMargin

  val templateClassDir: String =
    """|<div class="card" class:active={isSelected}>...</div>
       |<button class:loading={isPending} class:disabled={!canSubmit}>Submit</button>""".stripMargin

  val templateStyleDir: String =
    """<div style:color={textColor} style:font-size={fontSize + "px"}>...</div>"""

  val templateEventHandler: String =
    """|<button onclick={_ => count += 1}>+</button>
       |<input oninput={e => name.set(e.target.value)}/>
       |<form onsubmit={e => { e.preventDefault(); handleSubmit() }}>...</form>""".stripMargin

  val templateBindDir: String =
    """|<input bind:value={name}/>
       |<input type="checkbox" bind:checked={agreed}/>
       |<select bind:value={selected}>
       |  <option value="a">A</option>
       |  <option value="b">B</option>
       |</select>""".stripMargin

  val templateBindHtml: String =
    """|// Static trusted content
       |<div bind:innerHTML={TrustedHtml.unsafe("<strong>Hello</strong>")}/>
       |
       |// Reactive trusted content
       |val html = State(TrustedHtml.unsafe("<em>initial</em>"))
       |<div bind:innerHTML={html}/>""".stripMargin

  val templateIfElse: String =
    """|{if count > 10 then
       |  <p>Big number!</p>
       |else if count > 0 then
       |  <p>Small number</p>
       |else
       |  <p>Zero</p>
       |}""".stripMargin

  val templateListRender: String =
    """|{items.map(item =>
       |  <li key={item.id}>{item.name}</li>
       |)}""".stripMargin

  val templateComponent: String =
    """|<Button label="Click me" onclick={_ => doSomething()}/>
       |<Card title="My Card">
       |  <p>Slot content goes here</p>
       |</Card>""".stripMargin

  val templateSlot: String =
    """|// Inside Card.melt
       |<div class="card">
       |  <h2>{props.title}</h2>
       |  <div class="card-body">{children}</div>
       |</div>""".stripMargin

  // ── Runtime ──────────────────────────────────────────────────────────────────

  val runtimeStateExample: String =
    """|val count   = State(0)
       |val doubled = count.map(_ * 2)
       |val isEven  = count.memo(_ % 2 == 0)
       |
       |count.set(5)       // doubled = 10, isEven = false
       |count.update(_ + 1) // count = 6, doubled = 12, isEven = true""".stripMargin

  val runtimeStateExtensions: String =
    """|val n    = State(0)
       |val list = State(List.empty[String])
       |
       |n += 1                                   // State[Int] increment
       |n -= 1                                   // decrement
       |list.append("a")                         // append to list
       |list.set(list.value.filter(_.nonEmpty))  // replace whole list""".stripMargin

  val runtimeOnMount: String =
    """|import melt.runtime.onMount
       |
       |onMount { ctx =>
       |  val timer = setInterval(() => tick(), 1000)
       |  ctx.onCleanup(() => clearInterval(timer))
       |}""".stripMargin

  val runtimeBatch: String =
    """|import melt.runtime.Batch
       |
       |Batch {
       |  x.set(1)
       |  y.set(2)
       |  z.set(3)
       |} // subscribers notified once, with all three values updated""".stripMargin

  val runtimeSafeHtml: String =
    """|// Developer-controlled content — safe
       |val badge = TrustedHtml.unsafe("<strong>New</strong>")
       |
       |// User-provided content — sanitize first
       |val safeContent = TrustedHtml.sanitize(userInput, DOMPurify.sanitize(_))""".stripMargin

  // ── MeltKit ──────────────────────────────────────────────────────────────────

  val meltkitInstall: String =
    """|// build.sbt
       |libraryDependencies += "io.github.takapi327" %% "meltkit" % "0.1.0-SNAPSHOT"
       |
       |// Pick your adapter:
       |libraryDependencies += "io.github.takapi327" %% "meltkit-adapter-browser" % "0.1.0-SNAPSHOT"  // Scala.js
       |libraryDependencies += "io.github.takapi327" %% "meltkit-adapter-http4s"  % "0.1.0-SNAPSHOT"  // JVM / Node""".stripMargin

  val meltkitRoutes: String =
    """|import meltkit.*
       |
       |val app = MeltKit[IO]()
       |
       |app.get("") { ctx =>
       |  ctx.render(AppPage(AppPage.Props()))
       |}
       |
       |val lang = param[String]("lang")
       |app.get(lang) { ctx =>
       |  val l = ctx.params.lang
       |  ctx.render(AppPage(AppPage.Props(lang = l)))
       |}
       |
       |val slug = param[String]("slug")
       |app.get(lang / "guide" / slug) { ctx =>
       |  ctx.render(GuidePage(GuidePage.Props(lang = ctx.params.lang, slug = ctx.params.slug)))
       |}""".stripMargin

  val meltkitPathParams: String =
    """|val lang = param[String]("lang")
       |val id   = param[Int]("id")
       |
       |app.get(lang / "posts" / id) { ctx =>
       |  val language: String = ctx.params.lang
       |  val postId:   Int    = ctx.params.id
       |  ctx.json(fetchPost(postId))
       |}""".stripMargin

  val meltkitHttpMethods: String =
    """|val app = MeltKit[IO]()
       |
       |app.get("api/users")      { ctx => ctx.json(getUsers()) }
       |app.post("api/users")     { ctx => ctx.json(createUser(ctx.body)) }
       |app.put("api/users/1")    { ctx => ctx.json(updateUser(ctx.body)) }
       |app.delete("api/users/1") { ctx => ctx.json(deleteUser()) }""".stripMargin

  val meltkitMiddleware: String =
    """|val app = MeltKit[IO]()
       |
       |app.use { (event, resolve) =>
       |  // runs before every route
       |  println(s"Request: ${event.method} ${event.requestPath}")
       |  resolve()
       |}
       |
       |app.get("protected") { ctx =>
       |  if ctx.locals.get(AuthKey).isEmpty
       |  then ctx.redirect("/login")
       |  else ctx.render(ProtectedPage())
       |}""".stripMargin

  val meltkitFormActions: String =
    """|import meltkit.*
       |
       |app.use(ServerHook.csrf[IO]())   // reject cross-origin form POSTs
       |
       |// single default action
       |app.page("login")(
       |  render = (_, form) => LoginPage(LoginPage.Props(form = form)),
       |  action = ctx =>
       |    ctx.body.form[LoginForm].map {
       |      case Right(f) if f.email.contains("@") => ActionResult.Redirect("/dashboard")
       |      case Right(f)                          => fail(422, f.copy(errors = List("invalid email")))
       |      case Left(e)                           => fail(400, LoginForm("", "", errors = List(e.message)))
       |    }
       |)
       |
       |// named actions: one form, `formaction="?/save"` / `?/publish`
       |app.page("posts")(
       |  render  = (_, form) => PostEditorPage(PostEditorPage.Props(form = form)),
       |  actions = {
       |    case ("save", ctx)    => ctx.body.form[PostForm].map(_ => ActionResult.Redirect("/result/draft"))
       |    case ("publish", ctx) => ctx.body.form[PostForm].map(_ => ActionResult.Redirect("/result/published"))
       |  }
       |)""".stripMargin

  val meltkitViteManifest: String =
    """|// From a manifest file (JVM / Node — requires import meltkit.syntax.*)
       |val manifest = ViteManifest.fromFile("public/dist/.vite/manifest.json")
       |
       |// Or parse a manifest string directly
       |val manifest2 = ViteManifest.fromString(jsonText)""".stripMargin

  val meltkitLocals: String =
    """|val UserKey = LocalKey.make[User]
       |
       |// In middleware:
       |ctx.locals.set(UserKey, currentUser)
       |
       |// In route handler:
       |val user = ctx.locals.get(UserKey) // Option[User]""".stripMargin

  // ── MeltKit SSG ──────────────────────────────────────────────────────────────

  val ssgStep1: String =
    """|import meltkit.*
       |
       |// Opt each route into prerendering with PageOptions
       |val On = PageOptions(prerender = PrerenderOption.On)
       |
       |app.get("", On)                        { ctx => ctx.render(HomePage()) }
       |app.get("en/guide/introduction", On)   { ctx => ctx.render(GuidePage()) }
       |app.get("en/guide/installation", On)   { ctx => ctx.render(GuidePage()) }""".stripMargin

  val ssgStep2: String =
    """|import meltkit.ssg.*
       |
       |@main def generate(): Unit =
       |  val config = ServerConfig(
       |    template  = Template.fromResource("index.html"),
       |    manifest  = ViteManifest.fromFile("dist/.vite/manifest.json"),
       |    outputDir = Some("dist"),
       |    publicDir = Some("public"),
       |    assetsDir = Some("../dist/assets")
       |  )
       |  SsgGenerator.run(app, config)""".stripMargin

  val ssgStep3: String = "sbt \"server/runMain generate\""

  val ssgPageExample: String =
    """|// Static route — one file derived from the path segments
       |app.get("en/guide/introduction", PageOptions(prerender = PrerenderOption.On)) { ctx => ... }
       |
       |// Dynamic route — supply concrete paths via entries
       |app.get(lang / "guide" / slug, PageOptions(
       |  prerender = PrerenderOption.On,
       |  entries   = List("/en/guide/introduction", "/ja/guide/introduction")
       |)) { ctx => ... }""".stripMargin

  val ssgDynamicPages: String =
    """|val On = PageOptions(prerender = PrerenderOption.On)
       |
       |val langs  = List("en", "ja")
       |val guides = List("introduction", "installation", "quick-start")
       |
       |app.get(lang / "guide" / slug, On.copy(
       |  entries = for l <- langs; g <- guides yield s"/$l/guide/$g"
       |)) { ctx => ... }""".stripMargin

  val ssgOutputDir: String =
    """|ServerConfig(
       |  template  = Template.fromResource("index.html"),
       |  outputDir = Some("dist")   // where the static HTML files are written
       |)""".stripMargin

  val ssgHydration: String =
    """|// index.html
       |<script type="module">
       |  import("./app.js").then(m => m.hydrate?.())
       |</script>""".stripMargin

  val ssgDeploy: String =
    """|# Example: deploy to GitHub Pages
       |sbt "server/runMain generate"
       |cp -r dist/* docs/""".stripMargin

  // ── Compiler ─────────────────────────────────────────────────────────────────

  val compilerExample: String =
    """|import melt.MeltCompiler
       |import melt.CompileMode
       |
       |val source = \"\"\"
       |  <script lang="scala">
       |  case class Props(name: String = "world")
       |  </script>
       |  <p>Hello, {props.name}!</p>
       |\"\"\".stripMargin
       |
       |val result = MeltCompiler.compile(
       |  source     = source,
       |  filename   = "Greeting.melt",
       |  objectName = "Greeting",
       |  pkg        = "components",
       |  mode       = CompileMode.SPA   // SPA | SSR
       |)
       |
       |result.warnings.foreach(w => println("[warn] " + w.message))
       |result.scalaCode.foreach(println)""".stripMargin

  val compilerWarning: String =
    """|case class CompileResult(
       |  scalaCode: Option[String],
       |  scopedCss: Option[String],
       |  errors:    List[CompileError],
       |  warnings:  List[CompileWarning]
       |)
       |
       |case class CompileWarning(
       |  message:  String,
       |  line:     Int,
       |  column:   Int,
       |  filename: String
       |)""".stripMargin

  val compilerAstTypes: String =
    """|import melt.ast.MeltAst.*
       |
       |// TemplateNode variants:
       |// Element, Text, Expression, Component, InlineTemplate,
       |// Head, Window, Body, Document, DynamicElement,
       |// Boundary, KeyBlock, SnippetDef, RenderCall
       |
       |// Attr variants:
       |// Static, Dynamic, Directive, EventHandler,
       |// BooleanAttr, Spread, Shorthand""".stripMargin

  // ── sbt Plugin ───────────────────────────────────────────────────────────────

  val sbtPluginInstall: String =
    """|// project/plugins.sbt
       |addSbtPlugin("io.github.takapi327" % "sbt-melt"    % "0.1.0-SNAPSHOT")
       |addSbtPlugin("io.github.takapi327" % "sbt-meltkit" % "0.1.0-SNAPSHOT")""".stripMargin

  val sbtMeltConfig: String =
    """|// build.sbt
       |lazy val client = (project in file("client"))
       |  .enablePlugins(ScalaJSPlugin, MeltPlugin)
       |  .settings(
       |    meltPackage := "components",
       |    // Optional: set codegen mode explicitly
       |    meltCodegenMode := "spa"  // "spa" | "ssr" | "auto"
       |  )""".stripMargin

  val sbtMeltkitConfig: String =
    """|lazy val server = (project in file("server"))
       |  .enablePlugins(MeltkitPlugin)
       |  .settings(
       |    meltMode := Some(Http4s),   // Browser | Node | Http4s
       |    meltkitViteDistDir := (client / baseDirectory).value / "dist"
       |  )""".stripMargin

  val sbtMonorepoSetup: String =
    """|// build.sbt — depend on the codegen project directly
       |lazy val `sbt-melt` = MeltSbtPluginProject("sbt-melt", "plugins/sbt-melt")
       |  .dependsOn(codegen.jvm)""".stripMargin

  val sbtScssSetup: String =
    """|// project/plugins.sbt
       |addSbtPlugin("io.github.takapi327" % "sbt-melt" % "0.1.0-SNAPSHOT")
       |
       |// build.sbt
       |meltStylePreprocessor := Some(SassPreprocessor)""".stripMargin
