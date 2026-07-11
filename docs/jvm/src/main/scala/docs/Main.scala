/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package docs

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

import docs.pages.{ ChangelogPage, ExamplePage, ExamplesPage, GuidePage, Home, PlaygroundPage }
import docs.pages.api.{ Compiler, Meltkit, MeltkitSsg, Runtime, SbtPlugin, TemplateSyntaxApi }
import meltkit.*
import meltkit.ssg.*

private val basePath = sys.env.getOrElse("MELT_BASE_PATH", "").stripSuffix("/")

private val lang    = param[String]("lang")
private val guide   = param[String]("guide")
private val example = param[String]("example")

private val langs  = List("en", "ja")
private val guides = List(
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
private val examples = List("counter", "todo-app")

private val On = PageOptions(prerender = PrerenderOption.On)

private def createApp(): MeltKit[Future] =
  val app = MeltKit[Future]()

  app.get("", On) { ctx =>
    Future.successful(
      ctx.render(
        Home(Home.Props(basePath = basePath, lang = "en"))
      )
    )
  }

  app.get(lang, On.copy(entries = langs.map("/" + _))) { ctx =>
    val l = ctx.params.lang
    Future.successful(
      ctx.render(
        Home(Home.Props(basePath = basePath, lang = l))
      )
    )
  }

  app.get(
    lang / "guide" / guide,
    On.copy(entries = for l <- langs; g <- guides yield s"/$l/guide/$g")
  ) { ctx =>
    val l = ctx.params.lang
    val s = ctx.params.guide
    Future.successful(
      ctx.render(
        GuidePage(GuidePage.Props(basePath = basePath, lang = l, slug = s))
      )
    )
  }

  // API reference — one route per page (SvelteKit-style), no in-template slug switch.
  // Each page component wraps itself in <ApiLayout> (see pages/api/*.melt).
  app.get(lang / "api" / "template-syntax", On.copy(entries = langs.map(l => s"/$l/api/template-syntax"))) { ctx =>
    Future.successful(ctx.render(TemplateSyntaxApi(TemplateSyntaxApi.Props(basePath = basePath, lang = ctx.params.lang))))
  }
  app.get(lang / "api" / "runtime", On.copy(entries = langs.map(l => s"/$l/api/runtime"))) { ctx =>
    Future.successful(ctx.render(Runtime(Runtime.Props(basePath = basePath, lang = ctx.params.lang))))
  }
  app.get(lang / "api" / "meltkit", On.copy(entries = langs.map(l => s"/$l/api/meltkit"))) { ctx =>
    Future.successful(ctx.render(Meltkit(Meltkit.Props(basePath = basePath, lang = ctx.params.lang))))
  }
  app.get(lang / "api" / "meltkit-ssg", On.copy(entries = langs.map(l => s"/$l/api/meltkit-ssg"))) { ctx =>
    Future.successful(ctx.render(MeltkitSsg(MeltkitSsg.Props(basePath = basePath, lang = ctx.params.lang))))
  }
  app.get(lang / "api" / "compiler", On.copy(entries = langs.map(l => s"/$l/api/compiler"))) { ctx =>
    Future.successful(ctx.render(Compiler(Compiler.Props(basePath = basePath, lang = ctx.params.lang))))
  }
  app.get(lang / "api" / "sbt-plugin", On.copy(entries = langs.map(l => s"/$l/api/sbt-plugin"))) { ctx =>
    Future.successful(ctx.render(SbtPlugin(SbtPlugin.Props(basePath = basePath, lang = ctx.params.lang))))
  }

  app.get(
    lang / "examples",
    On.copy(entries = langs.map(l => s"/$l/examples"))
  ) { ctx =>
    val l = ctx.params.lang
    Future.successful(
      ctx.render(
        ExamplesPage(ExamplesPage.Props(basePath = basePath, lang = l))
      )
    )
  }

  app.get(
    lang / "examples" / example,
    On.copy(entries = for l <- langs; e <- examples yield s"/$l/examples/$e")
  ) { ctx =>
    val l = ctx.params.lang
    val s = ctx.params.example
    Future.successful(
      ctx.render(
        ExamplePage(ExamplePage.Props(basePath = basePath, lang = l, slug = s))
      )
    )
  }

  app.get(
    lang / "changelog",
    On.copy(entries = langs.map(l => s"/$l/changelog"))
  ) { ctx =>
    val l = ctx.params.lang
    Future.successful(
      ctx.render(
        ChangelogPage(ChangelogPage.Props(basePath = basePath, lang = l))
      )
    )
  }

  app.get(
    lang / "playground",
    On.copy(entries = langs.map(l => s"/$l/playground"))
  ) { ctx =>
    val l = ctx.params.lang
    Future.successful(
      ctx.render(
        PlaygroundPage(PlaygroundPage.Props(basePath = basePath, lang = l))
      )
    )
  }

  app

val app: MeltKit[Future] = createApp()

@main def generate(): Unit =
  val manifestPath = "../dist/.vite/manifest.json"
  val manifest     = Try(scala.io.Source.fromFile(manifestPath).mkString)
    .map(ViteManifest.fromString(_))
    .getOrElse {
      println(s"[warn] Vite manifest not found at $manifestPath — generating without JS bootstrap")
      ViteManifest.empty
    }
  val config = ServerConfig(
    outputDir = Some("docs-dist"),
    publicDir = Some("public"),
    assetsDir = Some("../dist/assets"),
    basePath  = basePath,
    manifest  = manifest,
    template  = Template.fromResource("index.html")
  )
  SsgGenerator.run(app, config)

@main def server(): Unit =
  UndertowServer
    .builder(app)
    .withPort(3000)
    .withTemplate(scala.io.Source.fromResource("index.html").mkString)
    .withManifest(generated.AssetManifest.manifest)
    .withClientDistDir(generated.AssetManifest.clientDistDir)
    .withPublicDir("public")
    .start()
    .onComplete {
      case scala.util.Success(server) =>
        println(s"Docs server running at http://${ server.host }:${ server.port }")
      case scala.util.Failure(e) =>
        System.err.println(s"[error] Failed to start server: ${ e.getMessage }")
        sys.exit(1)
    }
  Thread.currentThread().join()
