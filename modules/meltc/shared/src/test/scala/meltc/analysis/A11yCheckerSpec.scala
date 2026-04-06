/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.analysis

import meltc.MeltCompiler

class A11yCheckerSpec extends munit.FunSuite:

  private def warnings(src: String): List[String] =
    val result = MeltCompiler.compile(src, "Test.melt", "Test", "")
    result.warnings.map(_.message)

  // ── img alt ──

  test("img without alt produces warning with line number") {
    val src        = "<div>\n  <img src=\"photo.png\" />\n</div>"
    val result     = meltc.MeltCompiler.compile(src, "Test.melt", "Test", "")
    val imgWarning = result.warnings.find(_.message.contains("alt"))
    assert(imgWarning.isDefined, result.warnings.toString)
    assertEquals(imgWarning.get.line, 2) // <img> is on line 2
  }

  test("img without alt produces warning") {
    val w = warnings("""<img src="photo.png" />""")
    assert(w.exists(_.contains("alt")), w.toString)
  }

  test("img with alt produces no warning") {
    val w = warnings("""<img src="photo.png" alt="A photo" />""")
    assert(!w.exists(_.contains("<img>")), w.toString)
  }

  // ── heading content ──

  test("empty heading produces warning") {
    val w = warnings("<h1></h1>")
    assert(w.exists(_.contains("<h1>")), w.toString)
  }

  test("heading with text content produces no warning") {
    val w = warnings("<h1>Title</h1>")
    assert(!w.exists(_.contains("<h1>")), w.toString)
  }

  // ── click on non-interactive ──

  test("div with onclick but no role/tabindex produces warning") {
    val w = warnings("<div onclick={handler}></div>")
    assert(w.exists(_.contains("role")), w.toString)
  }

  test("button with onclick produces no a11y warning") {
    val w = warnings("<button onclick={handler}>Click</button>")
    assert(!w.exists(_.contains("role")), w.toString)
  }

  test("div with onclick and role + tabindex produces no warning") {
    val w = warnings("""<div onclick={handler} role="button" tabindex="0"></div>""")
    assert(!w.exists(_.contains("<div>")), w.toString)
  }

  // ── redundant role ──

  test("button with role=button produces warning") {
    val w = warnings("""<button role="button">Click</button>""")
    assert(w.exists(_.contains("redundant")), w.toString)
  }

  // ── video without track ──

  test("video without track produces warning") {
    val w = warnings("<video src=\"v.mp4\"></video>")
    assert(w.exists(_.contains("<track>")), w.toString)
  }

  test("video with track produces no warning") {
    val w = warnings("""<video src="v.mp4"><track kind="captions" /></video>""")
    assert(!w.exists(_.contains("<video>")), w.toString)
  }
