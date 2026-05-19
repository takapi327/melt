/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package docs

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import docs.pages.{ ApiPage, ChangelogPage, ExamplePage, ExamplesPage, GuidePage, Home }
import meltkit.*
import meltkit.ssg.*

private val basePath = ""

private val lang    = param[String]("lang")
private val guide   = param[String]("guide")
private val api     = param[String]("api")
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
private val apis = List(
  "template-syntax",
  "runtime",
  "meltkit",
  "meltkit-ssg",
  "compiler",
  "sbt-plugin"
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

  app.get(
    lang / "api" / api,
    On.copy(entries = for l <- langs; a <- apis yield s"/$l/api/$a")
  ) { ctx =>
    val l = ctx.params.lang
    val s = ctx.params.api
    Future.successful(
      ctx.render(
        ApiPage(ApiPage.Props(basePath = basePath, lang = l, slug = s))
      )
    )
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

  app

val app: MeltKit[Future] = createApp()

@main def generate(): Unit =
  val config = ServerConfig(
    outputDir = Some("docs-dist"),
    publicDir = Some("public"),
    template  = Template.fromResource("index.html")
  )
  SsgGenerator.run(app, config)

@main def server(): Unit =
  JdkServer
    .builder(app)
    .withPort(3000)
    .withTemplate(scala.io.Source.fromResource("index.html").mkString)
    .withManifest(generated.AssetManifest.manifest)
    .withClientDistDir(generated.AssetManifest.clientDistDir)
    .withPublicDir("public")
    .start()
    .foreach { server =>
      println(s"Docs server running at http://${ server.host }:${ server.port }")
    }
  Thread.currentThread().join()
