/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.lsp

/** Tests for [[UriUtil.filename]] — percent-decoding document URIs to file names. */
class UriUtilSpec extends munit.FunSuite:

  test("plain file URI yields the basename") {
    assertEquals(UriUtil.filename("file:///proj/Counter.melt"), "Counter.melt")
  }

  test("percent-encoded spaces are decoded") {
    assertEquals(UriUtil.filename("file:///proj/My%20File.melt"), "My File.melt")
  }

  test("encoded segments in the directory and name are decoded") {
    assertEquals(UriUtil.filename("file:///a/b%20c/Page%2B1.melt"), "Page+1.melt")
  }

  test("falls back gracefully for a bare filesystem path") {
    assertEquals(UriUtil.filename("/proj/Counter.melt"), "Counter.melt")
  }
