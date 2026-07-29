/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package example

import components.*
import meltkit.*
import meltkit.ssg.SsgGenerator

/** Nested layouts demo (SSR static site + router-driven hydration).
  *
  * `app.layout(prefix)(c => Layout(children = c))` registers a `{children}` layout
  * by path prefix: the empty prefix is the root layout, deeper prefixes nest inside
  * it. Each page is composed inside its matching layouts during SSR.
  *
  * `routerHydration = Some("app")` makes the generated HTML boot a single hydrate
  * entry (`@JSExportTopLevel("hydrate", moduleID = "app")` in the JS `Main`) that
  * hydrates the whole composed tree, instead of one hydrate call per component.
  *
  * Run with `sbt "nested-layoutsJVM/run"`; HTML is written under `dist/`.
  */
object Main:

  private val shell: Template =
    Template.fromString(
      "<!doctype html><html><head><meta charset=\"utf-8\">%melt.head%</head><body>%melt.body%</body></html>"
    )

  // This SSG example uses MeltPlugin, so the manifest and entry moduleID are written
  // by hand. A MeltkitPlugin server instead sets `meltkitRouterHydration := Some("app")`
  // in build.sbt and reads `MeltKitConfig.manifest` / `MeltKitConfig.routerHydration`
  // (generated from the real client build) — no hardcoding.
  private val manifest: ViteManifest =
    ViteManifest.fromEntries(Map("scalajs:app.js" -> ViteManifest.Entry(file = "app.js")))

  def main(args: Array[String]): Unit =
    val app = MeltKit[[A] =>> A]()

    // AppShell wraps every page; DashboardLayout nests inside it under /dashboard.
    app.layout("")(c => AppShell(children = c))
    app.layout("dashboard")(c => DashboardLayout(children = c))

    val prerender = PageOptions(prerender = PrerenderOption.On)
    app.get("", prerender)(ctx => ctx.render(HomePage()))
    app.get("dashboard/stats", prerender)(ctx => ctx.render(StatsPage()))

    val out = args.headOption.getOrElse("dist")
    SsgGenerator.run(
      app,
      ServerConfig(
        template        = shell,
        manifest        = manifest,
        outputDir       = Some(out),
        routerHydration = Some("app")
      )
    )
    println(s"[nested-layouts] Generated static site to '$out'")
