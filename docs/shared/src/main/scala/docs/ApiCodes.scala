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
       |<div bind:html={TrustedHtml.unsafe("<strong>Hello</strong>")}/>
       |
       |// Reactive trusted content
       |val html = State(TrustedHtml.unsafe("<em>initial</em>"))
       |<div bind:html={html}/>""".stripMargin

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
       |n += 1         // State[Int] increment
       |n -= 1         // decrement
       |list :+= "a"   // append to list
       |list = list.value.filter(_.nonEmpty)  // replace""".stripMargin

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

  val runtimeTrustedHtml: String =
    """|// Developer-controlled content — safe
       |val badge = TrustedHtml.unsafe("<strong>New</strong>")
       |
       |// User-provided content — sanitize first
       |val safeContent = TrustedHtml.sanitize(userInput, DOMPurify.sanitize(_))""".stripMargin

  // ── MeltKit ──────────────────────────────────────────────────────────────────

  val meltkitInstall: String =
    """|// build.sbt
       |libraryDependencies += "io.github.takapi327" %%% "meltkit" % "0.1.0"
       |
       |// Pick your adapter:
       |libraryDependencies += "io.github.takapi327" %%% "meltkit-adapter-browser" % "0.1.0"  // Scala.js
       |libraryDependencies += "io.github.takapi327" %%% "meltkit-adapter-http4s"  % "0.1.0"  // JVM / Node""".stripMargin

  val meltkitRoutes: String =
    """|import meltkit.*
       |
       |val app = MeltKit[IO]:
       |  get("") { ctx =>
       |    ctx.html(AppPage(AppPage.Props()))
       |  }
       |
       |  val lang = param[String]("lang")
       |  get(lang) { ctx =>
       |    val l = ctx.params.lang
       |    ctx.html(AppPage(AppPage.Props(lang = l)))
       |  }
       |
       |  val slug = param[String]("slug")
       |  get(lang / "guide" / slug) { ctx =>
       |    ctx.html(GuidePage(GuidePage.Props(lang = ctx.params.lang, slug = ctx.params.slug)))
       |  }""".stripMargin

  val meltkitPathParams: String =
    """|val lang = param[String]("lang")
       |val id   = param[Int]("id")
       |
       |get(lang / "posts" / id) { ctx =>
       |  val language: String = ctx.params.lang
       |  val postId:   Int    = ctx.params.id
       |  ctx.json(fetchPost(postId))
       |}""".stripMargin

  val meltkitHttpMethods: String =
    """|val app = MeltKit[IO]:
       |  get("api/users")    { ctx => ctx.json(getUsers()) }
       |  post("api/users")   { ctx => ctx.json(createUser(ctx.body)) }
       |  put("api/users/1")  { ctx => ctx.json(updateUser(ctx.body)) }
       |  delete("api/users/1") { ctx => ctx.json(deleteUser()) }""".stripMargin

  val meltkitMiddleware: String =
    """|val app = MeltKit[IO]:
       |  use { (ctx, next) =>
       |    // runs before every route
       |    println(s"Request: ${ctx.request.method} ${ctx.request.path}")
       |    next(ctx)
       |  }
       |
       |  get("protected") { ctx =>
       |    if ctx.locals.get(AuthKey).isEmpty
       |    then ctx.redirect("/login")
       |    else ctx.html(ProtectedPage())
       |  }""".stripMargin

  val meltkitViteManifest: String =
    """|val manifest = ViteManifest.load("public/dist/.vite/manifest.json")
       |// Returns Some(ViteManifest) in prod, None in dev""".stripMargin

  val meltkitLocals: String =
    """|val UserKey = LocalKey[User]("user")
       |
       |// In middleware:
       |ctx.locals.set(UserKey, currentUser)
       |
       |// In route handler:
       |val user = ctx.locals.get(UserKey) // Option[User]""".stripMargin

  // ── MeltKit SSG ──────────────────────────────────────────────────────────────

  val ssgStep1: String =
    """|// build.sbt
       |.settings(
       |  meltMode := MeltMode.SSG
       |)""".stripMargin

  val ssgStep2: String =
    """|.settings(
       |  meltkitSsgPages := List(
       |    SsgPage("/"),
       |    SsgPage("/en/guide/introduction"),
       |    SsgPage("/en/guide/installation"),
       |    SsgPage("/ja/guide/introduction")
       |  )
       |)""".stripMargin

  val ssgStep3: String = "sbt myProject/meltkitSsgGenerate"

  val ssgPageExample: String =
    """|SsgPage("/en/guide/introduction")
       |SsgPage("/en/guide/introduction", outFile = "en/guide/introduction/index.html")""".stripMargin

  val ssgDynamicPages: String =
    """|meltkitSsgPages := {
       |  val langs  = List("en", "ja")
       |  val guides = List("introduction", "installation", "quick-start")
       |  for lang <- langs; slug <- guides
       |  yield SsgPage(s"/$lang/guide/$slug")
       |}""".stripMargin

  val ssgOutputDir: String = "meltkitSsgOutputDir := baseDirectory.value / \"dist\""

  val ssgHydration: String =
    """|// index.html
       |<script type="module">
       |  import("./app.js").then(m => m.hydrate?.())
       |</script>""".stripMargin

  val ssgDeploy: String =
    """|# Example: deploy to GitHub Pages
       |sbt meltkitSsgGenerate
       |cp -r target/ssg/* docs/""".stripMargin

  // ── Compiler ─────────────────────────────────────────────────────────────────

  val compilerExample: String =
    """|import melt.MeltCompiler
       |import melt.MeltCompiler.Config
       |
       |val source = \"\"\"
       |  <script lang="scala">
       |  case class Props(name: String = "world")
       |  </script>
       |  <p>Hello, {props.name}!</p>
       |\"\"\".stripMargin
       |
       |val config = Config(
       |  componentName = "Greeting",
       |  pkg           = "components",
       |  mode          = "spa"   // "spa" | "ssr" | "auto"
       |)
       |
       |val result = MeltCompiler.compile(source, config)
       |result.warnings.foreach(w => println("[warn] " + w.message))
       |println(result.code)""".stripMargin

  val compilerWarning: String =
    """|case class MeltWarning(
       |  message:  String,
       |  severity: Severity,  // Error | Warning | Info
       |  line:     Option[Int],
       |  column:   Option[Int]
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
       |addSbtPlugin("io.github.takapi327" % "sbt-melt"    % "0.1.0")
       |addSbtPlugin("io.github.takapi327" % "sbt-meltkit" % "0.1.0")""".stripMargin

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
       |    meltMode := MeltMode.SSR,
       |    meltkitViteDistDir := (client / baseDirectory).value / "dist"
       |  )""".stripMargin

  val sbtMonorepoSetup: String =
    """|// build.sbt — point at compiled classes directly
       |meltCompilerClasspath := (codegenJVM / Compile / fullClasspath).value.files""".stripMargin

  val sbtScssSetup: String =
    """|// project/plugins.sbt
       |addSbtPlugin("io.github.takapi327" % "sbt-melt" % "0.1.0")
       |
       |// build.sbt
       |meltStylePreprocessor := Some(SassPreprocessor)""".stripMargin
