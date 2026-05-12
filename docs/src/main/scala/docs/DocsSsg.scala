/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package docs

import docs.components.Layout
import docs.pages.{ ApiPage, ChangelogPage, ExamplePage, ExamplesPage, GuidePage, Home }
import meltkit.*
import meltkit.ssg.SyncSsgApp

object DocsSsg extends SyncSsgApp:

  override val basePath: String = ""

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

  kit.get("") { ctx =>
    ctx.render(
      Layout(
        Layout.Props(basePath = basePath, lang = "en"),
        children = () => Home(Home.Props(basePath = basePath, lang = "en"))
      )
    )
  }

  kit.get("" / lang) { ctx =>
    val l = ctx.params.lang
    ctx.render(
      Layout(
        Layout.Props(basePath = basePath, lang = l),
        children = () => Home(Home.Props(basePath = basePath, lang = l))
      )
    )
  }

  kit.get("" / lang / "guide" / guide) { ctx =>
    val l = ctx.params.lang
    val s = ctx.params.guide
    ctx.render(
      Layout(
        Layout.Props(basePath = basePath, lang = l, section = "guide", slug = s),
        children = () => GuidePage(GuidePage.Props(slug = s))
      )
    )
  }

  kit.get("" / lang / "api" / api) { ctx =>
    val l = ctx.params.lang
    val s = ctx.params.api
    ctx.render(
      Layout(
        Layout.Props(basePath = basePath, lang = l, section = "api", slug = s),
        children = () => ApiPage(ApiPage.Props(slug = s))
      )
    )
  }

  kit.get("" / lang / "examples") { ctx =>
    val l = ctx.params.lang
    ctx.render(
      Layout(Layout.Props(basePath = basePath, lang = l, section = "examples"), children = () => ExamplesPage())
    )
  }

  kit.get("" / lang / "examples" / example) { ctx =>
    val l = ctx.params.lang
    val s = ctx.params.example
    ctx.render(
      Layout(
        Layout.Props(basePath = basePath, lang = l, section = "examples", slug = s),
        children = () => ExamplePage(ExamplePage.Props(slug = s))
      )
    )
  }

  kit.get("" / lang / "changelog") { ctx =>
    val l = ctx.params.lang
    ctx.render(
      Layout(Layout.Props(basePath = basePath, lang = l, section = "changelog"), children = () => ChangelogPage())
    )
  }

  override val paths: List[String] =
    langs.flatMap { lang =>
      List(s"/$lang") ++
        guides.map(p => s"/$lang/guide/$p") ++
        apis.map(p => s"/$lang/api/$p") ++
        List(s"/$lang/examples") ++
        examples.map(p => s"/$lang/examples/$p") ++
        List(s"/$lang/changelog")
    } :+ "/"

  override val template: Template = Template.fromResource("index.html")

  override val manifest: ViteManifest = ViteManifest.empty
