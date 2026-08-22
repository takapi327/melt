/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import melt.runtime.render.RenderResult

import meltkit.{ Template, ViteManifest }

/** The hydration-bootstrap switch in [[Template.render]]: per-component fan-out
  * (default) vs a single router-driven entry (`routerEntry = Some(...)`), used by
  * nested layouts where one app entry hydrates the whole composed tree. */
class TemplateRouterHydrationTest extends munit.FunSuite:

  private val manifest = ViteManifest.fromEntries(
    Map(
      "scalajs:app.js"   -> ViteManifest.Entry(file = "assets/app-abc.js"),
      "scalajs:home.js"  -> ViteManifest.Entry(file = "assets/home-def.js"),
      "scalajs:stats.js" -> ViteManifest.Entry(file = "assets/stats-ghi.js")
    )
  )
  private val tmpl   = Template.fromString("<html><head>%melt.head%</head><body>%melt.body%</body></html>")
  private val result = RenderResult(body = "<main>x</main>", head = "", components = Set("home", "stats"))

  private def imports(html: String): Int = "import\\(".r.findAllIn(html).size

  test("per-component bootstrap (routerEntry = None): one hydrate import per component") {
    val html = tmpl.render(result, manifest, "", "en", "", Map.empty, None)
    assert(html.contains("assets/home-def.js"), html)
    assert(html.contains("assets/stats-ghi.js"), html)
    assert(!html.contains("assets/app-abc.js"), html)
    assertEquals(imports(html), 2) // one import().hydrate() per component
  }

  test("router-driven bootstrap (routerEntry = Some): a single entry hydrate import") {
    val html = tmpl.render(result, manifest, "", "en", "", Map.empty, None, routerEntry = Some("app"))
    assert(html.contains("assets/app-abc.js"), html) // the single app entry chunk
    assertEquals(imports(html), 1)                   // no per-component fan-out
    assert(html.contains("m.hydrate?.()"), html)     // still the hydrate call
  }

  test("router-driven bootstrap with an unknown entry emits no bootstrap") {
    val html = tmpl.render(result, manifest, "", "en", "", Map.empty, None, routerEntry = Some("missing"))
    assertEquals(imports(html), 0)
  }

  test("hydrate bootstrap recovers from a stale/missing chunk by reloading once") {
    val html = tmpl.render(result, manifest, "", "en", "", Map.empty, None, routerEntry = Some("app"))
    // A failed dynamic import (stale entry referencing a removed hashed chunk) triggers
    // a throttled one-shot reload so the browser refetches the fresh entry.
    assert(html.contains(".catch("), html)
    assert(html.contains("location.reload()"), html)
  }
