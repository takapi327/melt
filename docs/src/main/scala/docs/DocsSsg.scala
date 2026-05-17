/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package docs

import java.nio.file.Paths

import docs.components.Layout
import docs.pages.{ ApiPage, ChangelogPage, ExamplePage, ExamplesPage, GuidePage, Home }
import meltkit.*
import meltkit.ssg.*
import meltkit.ssg.SsgRunner.given

object DocsSsg:

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

  def buildApp(): MeltKit[[A] =>> A] =
    val app = MeltKit[[A] =>> A]()

    app.get("", On) { ctx =>
      ctx.render(
        Layout(
          Layout.Props(basePath = basePath, lang = "en"),
          children = () => Home(Home.Props(basePath = basePath, lang = "en"))
        )
      )
    }

    app.get(lang, On.copy(entries = langs.map("/" + _))) { ctx =>
      val l = ctx.params.lang
      ctx.render(
        Layout(
          Layout.Props(basePath = basePath, lang = l),
          children = () => Home(Home.Props(basePath = basePath, lang = l))
        )
      )
    }

    app.get(
      lang / "guide" / guide,
      On.copy(entries = for l <- langs; g <- guides yield s"/$l/guide/$g")
    ) { ctx =>
      val l = ctx.params.lang
      val s = ctx.params.guide
      ctx.render(
        Layout(
          Layout.Props(basePath = basePath, lang = l, section = "guide", slug = s),
          children = () => GuidePage(GuidePage.Props(slug = s))
        )
      )
    }

    app.get(
      lang / "api" / api,
      On.copy(entries = for l <- langs; a <- apis yield s"/$l/api/$a")
    ) { ctx =>
      val l = ctx.params.lang
      val s = ctx.params.api
      ctx.render(
        Layout(
          Layout.Props(basePath = basePath, lang = l, section = "api", slug = s),
          children = () => ApiPage(ApiPage.Props(slug = s))
        )
      )
    }

    app.get(
      lang / "examples",
      On.copy(entries = langs.map(l => s"/$l/examples"))
    ) { ctx =>
      val l = ctx.params.lang
      ctx.render(
        Layout(Layout.Props(basePath = basePath, lang = l, section = "examples"), children = () => ExamplesPage())
      )
    }

    app.get(
      lang / "examples" / example,
      On.copy(entries = for l <- langs; e <- examples yield s"/$l/examples/$e")
    ) { ctx =>
      val l = ctx.params.lang
      val s = ctx.params.example
      ctx.render(
        Layout(
          Layout.Props(basePath = basePath, lang = l, section = "examples", slug = s),
          children = () => ExamplePage(ExamplePage.Props(slug = s))
        )
      )
    }

    app.get(
      lang / "changelog",
      On.copy(entries = langs.map(l => s"/$l/changelog"))
    ) { ctx =>
      val l = ctx.params.lang
      ctx.render(
        Layout(Layout.Props(basePath = basePath, lang = l, section = "changelog"), children = () => ChangelogPage())
      )
    }

    app

@main def generate(): Unit =
  val config = SsgConfig(
    outputDir = Paths.get("docs-dist"),
    template  = Template.fromFile(Paths.get("docs/src/main/resources/index.html"))
  )
  SsgGenerator.run(DocsSsg.buildApp(), config)
