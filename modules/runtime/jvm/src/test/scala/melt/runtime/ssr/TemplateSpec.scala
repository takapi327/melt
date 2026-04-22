/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.ssr

import java.io.FileNotFoundException
import java.nio.file.Files

import munit.FunSuite

/** JVM-specific I/O tests for [[Template]]. */
class TemplateJvmSpec extends FunSuite:

  private val sampleResult = RenderResult(
    body       = "<main>hi</main>",
    head       = "<style>body{}</style>",
    css        = Set.empty,
    components = Set.empty
  )

  // ── Factory methods ────────────────────────────────────────────────────

  test("fromFile reads a UTF-8 template from disk") {
    val tmp = Files.createTempFile("melt-template", ".html")
    try
      Files.writeString(tmp, "<div>%melt.body% 日本語</div>")
      val t    = Template.fromFile(tmp)
      val html = t.render(sampleResult)
      assert(html.contains("<main>hi</main>"), html)
      assert(html.contains("日本語"), html)
    finally Files.deleteIfExists(tmp)
  }

  test("fromResource raises a clear error when the resource is missing") {
    val e = intercept[FileNotFoundException] {
      Template.fromResource("/this-does-not-exist.html")
    }
    assert(e.getMessage.contains("src/main/resources"), e.getMessage)
    assert(e.getMessage.contains("/this-does-not-exist.html"), e.getMessage)
  }

  test("fromResource normalises leading-slash-less paths") {
    val e = intercept[FileNotFoundException] {
      Template.fromResource("nope.html")
    }
    // The resolved path should have a leading slash in the error message.
    assert(e.getMessage.contains("/nope.html"), e.getMessage)
  }
