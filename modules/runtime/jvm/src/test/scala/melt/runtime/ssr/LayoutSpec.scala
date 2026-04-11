/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.ssr

import munit.FunSuite

/** Phase A tests for [[Layout]] — §12.3.8 UTF-8 / viewport contract. */
class LayoutSpec extends FunSuite:

  private val empty = RenderResult.empty

  test("document emits a proper doctype and html opening") {
    val html = Layout.document(empty)
    assert(html.startsWith("<!DOCTYPE html>"), html.take(80))
    assert(html.contains("""<html lang="en">"""), html.take(80))
  }

  test("charset meta is the first child of head") {
    val html  = Layout.document(empty)
    val headI = html.indexOf("<head>")
    val metaI = html.indexOf("<meta charset=\"UTF-8\">")
    assert(headI >= 0 && metaI > headI, s"head at $headI, meta at $metaI\n$html")
    // No other <meta> or <title> can appear before the charset.
    val between = html.substring(headI + "<head>".length, metaI).trim
    assert(between.isEmpty, s"found content before charset: '$between'")
  }

  test("viewport meta is always emitted") {
    val html = Layout.document(empty)
    assert(
      html.contains("""<meta name="viewport" content="width=device-width, initial-scale=1">"""),
      html
    )
  }

  test("title is HTML-escaped") {
    val html = Layout.document(empty, title = "<script>")
    assert(html.contains("<title>&lt;script&gt;</title>"), html)
    assert(!html.contains("<title><script>"), html)
  }

  test("empty title omits the <title> tag entirely") {
    val html = Layout.document(empty, title = "")
    assert(!html.contains("<title>"), html)
  }

  test("result.head is injected before extraHead") {
    val result = RenderResult(
      body       = "<main/>",
      head       = "<meta name=\"component\">",
      css        = Set.empty,
      components = Set.empty
    )
    val html = Layout.document(result, extraHead = "<meta name=\"extra\">")
    val compI = html.indexOf("<meta name=\"component\">")
    val extraI = html.indexOf("<meta name=\"extra\">")
    assert(compI >= 0 && extraI >= 0 && compI < extraI, s"\n$html")
  }

  test("body content is inserted verbatim inside <body>") {
    val result = RenderResult(
      body       = "<main><h1>Hi</h1></main>",
      head       = "",
      css        = Set.empty,
      components = Set.empty
    )
    val html = Layout.document(result)
    assert(html.contains("<body>"), html)
    assert(html.contains("<main><h1>Hi</h1></main>"), html)
  }

  test("custom lang attribute is reflected in <html>") {
    val html = Layout.document(empty, lang = "ja")
    assert(html.contains("""<html lang="ja">"""), html)
  }
