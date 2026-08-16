/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import scala.scalajs.js
import scala.scalajs.js.Dynamic.global as g

import meltkit.SsrRenderScope

/** End-to-end test of the streaming-SSR client swap in a real DOM (jsdom).
  *
  * The server (http4s / Node / Undertow) flushes the shell with each `<melt:await>`
  * boundary's `<!--melt:sb:ID-->…<!--/melt:sb:ID-->` fallback, then streams a
  * `<template>` + `window.__meltSwap("ID")` chunk per boundary. This test runs the
  * *real* bootstrap and chunk HTML produced by [[SsrRenderScope]] inside jsdom (with
  * scripts enabled) and asserts the swap leaves the DOM identical to what the blocking
  * `renderAsync` splice would produce — the one piece the JVM/Node unit tests can't
  * exercise. jsdom is required manually (this module runs under the plain Node env).
  */
class SwapE2ETest extends munit.FunSuite:

  /** Parses `html` in jsdom with inline scripts executed, returning `document`. */
  private def runInDom(html: String): js.Dynamic =
    val jsdom = g.require("jsdom")
    val dom   = js.Dynamic.newInstance(jsdom.JSDOM)(html, js.Dynamic.literal(runScripts = "dangerously"))
    dom.window.document

  /** Counts comment nodes whose data equals `data` (a boundary marker) in the doc. */
  private def commentCount(document: js.Dynamic, data: String): Int =
    val walker = document.createTreeWalker(document.body, 128) // NodeFilter.SHOW_COMMENT
    var n      = 0
    var node   = walker.nextNode()
    while node != null do
      if node.data.asInstanceOf[String] == data then n += 1
      node = walker.nextNode()
    n

  private def shell(id: String, fallback: String, resolved: String): String =
    val marked    = s"<!--melt:sb:$id-->$fallback<!--/melt:sb:$id-->"
    val bootstrap = SsrRenderScope.streamSwapBootstrap(None)
    val chunk     = SsrRenderScope.streamChunk(id, resolved, None)
    s"<!doctype html><html><head></head><body><main>$marked</main>$bootstrap$chunk</body></html>"

  test("a streamed chunk swaps its boundary fallback for the resolved branch"):
    val resolved = "<ul><li>1</li><li>2</li><li>3</li></ul>"
    val document = runInDom(shell("melt-sb-1", "<p class=\"loading\">Loading…</p>", resolved))

    val mainHtml = document.querySelector("main").innerHTML.asInstanceOf[String]
    // The <main> now holds exactly the resolved branch — identical to the blocking splice.
    assertEquals(mainHtml, resolved)
    // The template was consumed and removed; nothing left to swap.
    // (The body still *mentions* "melt:sb:" inside the bootstrap script source, so we
    // assert on the DOM, not the serialized text: no template elements, no comment
    // marker nodes remain.)
    assertEquals(document.querySelectorAll("template").length.asInstanceOf[Int], 0)
    assertEquals(commentCount(document, "melt:sb:melt-sb-1"), 0)
    assertEquals(commentCount(document, "/melt:sb:melt-sb-1"), 0)

  test("each boundary swaps independently when several stream in"):
    val rA        = "<span id=\"a\">A</span>"
    val rB        = "<span id=\"b\">B</span>"
    val bootstrap = SsrRenderScope.streamSwapBootstrap(None)
    val html      =
      "<!doctype html><html><head></head><body>" +
        s"<section><!--melt:sb:x1-->one<!--/melt:sb:x1--></section>" +
        s"<section><!--melt:sb:x2-->two<!--/melt:sb:x2--></section>" +
        bootstrap +
        SsrRenderScope.streamChunk("x1", rA, None) +
        SsrRenderScope.streamChunk("x2", rB, None) +
        "</body></html>"

    val document = runInDom(html)
    val sections = document.querySelectorAll("section")
    assertEquals(sections.selectDynamic("0").innerHTML.asInstanceOf[String], rA)
    assertEquals(sections.selectDynamic("1").innerHTML.asInstanceOf[String], rB)

  test("__meltSwap is a no-op when the boundary markers are absent (defensive)"):
    // A chunk whose fallback span was already removed (e.g. a parent boundary
    // replaced it) must not throw or corrupt the DOM.
    val bootstrap = SsrRenderScope.streamSwapBootstrap(None)
    val html      =
      "<!doctype html><html><head></head><body><main>kept</main>" +
        bootstrap +
        SsrRenderScope.streamChunk("gone", "<p>never</p>", None) +
        "</body></html>"

    val document = runInDom(html)
    // No throw, the kept content is intact, and the orphan template is left untouched
    // (its inert content never renders — a parent boundary already removed the span).
    assertEquals(document.querySelector("main").innerHTML.asInstanceOf[String], "kept")
    assertEquals(document.querySelectorAll("template").length.asInstanceOf[Int], 1)
