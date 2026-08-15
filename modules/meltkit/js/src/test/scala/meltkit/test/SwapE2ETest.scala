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
  * exercise.
  */
class SwapE2ETest extends munit.FunSuite:

  /** Renders `bodyHtml` into the ambient jsdom document and returns that `document`.
    *
    * `JSDOMNodeJSEnv` already runs this code inside a jsdom window configured with
    * `runScripts: "dangerously"`, so the boundary bootstrap executes as it would in a
    * browser. Each `<script>` is re-created before insertion because scripts assigned
    * through `innerHTML` are inert; document order is preserved, so the bootstrap still
    * runs before the chunks that call into it.
    */
  private def runInDom(bodyHtml: String): js.Dynamic =
    val document = g.document
    document.body.innerHTML = bodyHtml
    val scripts = document.body.querySelectorAll("script")
    val total   = scripts.length.asInstanceOf[Int]
    for i <- 0 until total do
      val inert = scripts.selectDynamic(i.toString)
      val live  = document.createElement("script")
      live.textContent = inert.textContent
      inert.parentNode.replaceChild(live, inert)
    document

  /** Counts comment nodes whose data equals `data` (a boundary marker) in the doc.
    *
    * `128` is `NodeFilter.SHOW_COMMENT`.
    */
  private def commentCount(document: js.Dynamic, data: String): Int =
    val walker = document.createTreeWalker(document.body, 128)
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
    s"<main>$marked</main>$bootstrap$chunk"

  test("a streamed chunk swaps its boundary fallback for the resolved branch"):
    val resolved = "<ul><li>1</li><li>2</li><li>3</li></ul>"
    val document = runInDom(shell("melt-sb-1", "<p class=\"loading\">Loading…</p>", resolved))

    val mainHtml = document.querySelector("main").innerHTML.asInstanceOf[String]
    assertEquals(mainHtml, resolved)
    assertEquals(document.querySelectorAll("template").length.asInstanceOf[Int], 0)
    assertEquals(commentCount(document, "melt:sb:melt-sb-1"), 0)
    assertEquals(commentCount(document, "/melt:sb:melt-sb-1"), 0)

  test("each boundary swaps independently when several stream in"):
    val rA        = "<span id=\"a\">A</span>"
    val rB        = "<span id=\"b\">B</span>"
    val bootstrap = SsrRenderScope.streamSwapBootstrap(None)
    val html      =
      s"<section><!--melt:sb:x1-->one<!--/melt:sb:x1--></section>" +
        s"<section><!--melt:sb:x2-->two<!--/melt:sb:x2--></section>" +
        bootstrap +
        SsrRenderScope.streamChunk("x1", rA, None) +
        SsrRenderScope.streamChunk("x2", rB, None)

    val document = runInDom(html)
    val sections = document.querySelectorAll("section")
    assertEquals(sections.selectDynamic("0").innerHTML.asInstanceOf[String], rA)
    assertEquals(sections.selectDynamic("1").innerHTML.asInstanceOf[String], rB)

  /** A chunk whose fallback span was already removed — e.g. a parent boundary replaced
    * it — must not throw or corrupt the DOM, and its orphan template stays inert.
    */
  test("__meltSwap is a no-op when the boundary markers are absent (defensive)"):
    val bootstrap = SsrRenderScope.streamSwapBootstrap(None)
    val html      =
      "<main>kept</main>" +
        bootstrap +
        SsrRenderScope.streamChunk("gone", "<p>never</p>", None)

    val document = runInDom(html)
    assertEquals(document.querySelector("main").innerHTML.asInstanceOf[String], "kept")
    assertEquals(document.querySelectorAll("template").length.asInstanceOf[Int], 1)
