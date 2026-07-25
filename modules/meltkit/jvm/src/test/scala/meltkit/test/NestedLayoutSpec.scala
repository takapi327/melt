/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import melt.runtime.render.RenderResult

import meltkit.*

/** Nested-layout composition (`app.layout` / `layoutsFor` / `wrapLayouts`) — the
  * Phase 1 SSR-only path. Layouts are represented here by plain `(() => RenderResult)
  * => RenderResult` wrappers (a real `.melt` layout compiles to exactly this apply). */
class NestedLayoutSpec extends munit.FunSuite:

  private def app() = MeltKit[[A] =>> A]()

  test("layoutsFor returns every matching layout, outermost (shortest prefix) first") {
    val a = app()
    a.layout("")(c => RenderResult("<root>" + c().body + "</root>", ""))
    a.layout("dashboard")(c => RenderResult("<dash>" + c().body + "</dash>", ""))
    a.layout("admin")(c => RenderResult("<admin>" + c().body + "</admin>", ""))
    assertEquals(a.layoutsFor("dashboard/stats").length, 2) // root + dashboard
    assertEquals(a.layoutsFor("admin").length, 2)           // root + admin
    assertEquals(a.layoutsFor("other").length, 1)           // root only
  }

  test("wrapLayouts nests the page inside matching layouts, outermost around innermost") {
    val a = app()
    a.layout("")(c => RenderResult("<root>" + c().body + "</root>", ""))
    a.layout("dashboard")(c => RenderResult("<dash>" + c().body + "</dash>", ""))
    val out = a.wrapLayouts("dashboard/stats", () => RenderResult("<page/>", ""))
    assertEquals(out.body, "<root><dash><page/></dash></root>")
  }

  test("wrapLayouts leaves the page unchanged when no layout matches") {
    val a   = app()
    val out = a.wrapLayouts("x", () => RenderResult("<page/>", ""))
    assertEquals(out.body, "<page/>")
  }

  test("the empty-prefix layout wraps every page (root layout)") {
    val a = app()
    a.layout("")(c => RenderResult("[" + c().body + "]", ""))
    assertEquals(a.wrapLayouts("anything/deep", () => RenderResult("X", "")).body, "[X]")
  }

  test("a deeper prefix does not apply to a shallower path") {
    val a = app()
    a.layout("dashboard/settings")(c => RenderResult("<s>" + c().body + "</s>", ""))
    assertEquals(a.layoutsFor("dashboard").length, 0)
    assertEquals(a.layoutsFor("dashboard/settings/profile").length, 1)
  }

  test("app.layout wraps a {children}-component apply via an explicit lambda (L8)") {
    // Mirrors the SSR apply the codegen emits for a `{children}` layout component:
    //   def apply(children: () => RenderResult = () => RenderResult.empty): RenderResult
    // The wrap is written as an explicit lambda `c => Layout(children = c)` — the
    // named-arg placeholder form `Layout(children = _)` is NOT valid Scala.
    val a = app()
    a.layout("x")(c => StubLayout(children = c))
    assertEquals(a.wrapLayouts("x", () => RenderResult("<p/>", "")).body, "<layout><p/></layout>")
  }

  object StubLayout:
    def apply(children: () => RenderResult = () => RenderResult.empty): RenderResult =
      RenderResult("<layout>" + children().body + "</layout>", "")
