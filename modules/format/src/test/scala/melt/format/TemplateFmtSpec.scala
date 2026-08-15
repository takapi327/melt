/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.format

class TemplateFmtSpec extends munit.FunSuite:

  private def fmt(inner: String): String =
    TemplateFmt.format(inner).fold(err => fail(s"unexpected Left: $err"), identity)

  test("reformats a messy template (block children each on their own line)") {
    assertNoDiff(
      fmt("<ul><li>a</li><li>b</li></ul>"),
      """<ul>
        |  <li>a</li>
        |  <li>b</li>
        |</ul>""".stripMargin
    )
  }

  test("normalises attributes and re-indents nested blocks") {
    assertNoDiff(
      fmt("<div class='card'><h2>{title}</h2><button onclick={add}>Add</button></div>"),
      """<div class="card">
        |  <h2>{title}</h2>
        |  <button onclick={add}>Add</button>
        |</div>""".stripMargin
    )
  }

  test("InlineTemplate (map with HTML) is kept verbatim") {
    val src = "<ul>{items.map(i => <li>{i.name}</li>)}</ul>"
    assertEquals(fmt(src), src)
  }

  test("HTML comments are preserved and formatted (own line in block context)") {
    assertNoDiff(
      fmt("<div><!-- hero --><span>x</span></div>"),
      """<div>
        |  <!-- hero -->
        |  <span>x</span>
        |</div>""".stripMargin
    )
  }

  test("template with <melt:boundary> is left unchanged (unsupported node)") {
    val src = "<melt:boundary onerror={h}><p>x</p></melt:boundary>"
    assertEquals(fmt(src), src)
  }

  test("unparseable template ({@foo}) is left unchanged") {
    val src = "<div>{@foo bar}</div>"
    assertEquals(fmt(src), src)
  }

  test("idempotent") {
    val once = fmt("<section><h1>t</h1><p>hi <b>there</b></p></section>")
    assertEquals(fmt(once), once)
  }
