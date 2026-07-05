/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import org.scalajs.dom

/** Regression tests for vuln-07: the SPA / hydration attribute-binding path
  * (`Bind.attr`) must apply the same URL-protocol validation as the server
  * (`Escape.url`), so that a dangerous scheme cannot be re-introduced on the
  * client after the server stripped it.
  */
class BindUrlAttrSpec extends munit.FunSuite:

  override def beforeEach(context: BeforeEach): Unit = MeltWarnings.mute()
  override def afterEach(context:  AfterEach):  Unit = MeltWarnings.resetHandler()

  test("Bind.attr strips javascript: from <a href>") {
    val a = dom.document.createElement("a")
    Bind.attr(a, "href", State("javascript:alert(1)"))
    assertEquals(a.getAttribute("href"), "")
  }

  test("Bind.attr keeps safe href verbatim (no entity escaping)") {
    val a = dom.document.createElement("a")
    Bind.attr(a, "href", State("https://example.com/p?a=1&b=2"))
    assertEquals(a.getAttribute("href"), "https://example.com/p?a=1&b=2")
  }

  test("Bind.attr re-validates on reactive update (hydration cannot re-inject)") {
    val href = State("https://example.com")
    val a    = dom.document.createElement("a")
    Bind.attr(a, "href", href)
    assertEquals(a.getAttribute("href"), "https://example.com")
    href.set("javascript:steal()")
    assertEquals(a.getAttribute("href"), "")
  }

  test("Bind.attr strips javascript: from <img src>") {
    val img = dom.document.createElement("img")
    Bind.attr(img, "src", State("javascript:alert(1)"))
    assertEquals(img.getAttribute("src"), "")
  }

  test("Bind.attr does not URL-validate non-URL attributes") {
    // title is not a URL attribute: value passes through unchanged.
    val div = dom.document.createElement("div")
    Bind.attr(div, "title", State("javascript:not-a-url"))
    assertEquals(div.getAttribute("title"), "javascript:not-a-url")
  }

  test("Bind.attr(Any) overload also validates URL attributes") {
    val a = dom.document.createElement("a")
    Bind.attr(a, "href", "javascript:alert(1)": Any)
    assertEquals(a.getAttribute("href"), "")
  }
