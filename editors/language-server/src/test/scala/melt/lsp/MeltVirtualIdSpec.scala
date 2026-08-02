/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.lsp

/** Tests for [[MeltVirtualId]] — distinct, stable virtual identities per .melt URI
  * so same-basename documents no longer collide in Metals' virtual workspace.
  */
class MeltVirtualIdSpec extends munit.FunSuite:

  test("same-basename URIs get distinct object names and filenames") {
    val a = "file:///proj/a/index.melt"
    val b = "file:///proj/b/index.melt"
    assertNotEquals(MeltVirtualId.objectName(a), MeltVirtualId.objectName(b))
    assertNotEquals(MeltVirtualId.fileBaseName(a), MeltVirtualId.fileBaseName(b))
  }

  test("identifiers are stable across calls for the same URI") {
    val u = "file:///proj/Counter.melt"
    assertEquals(MeltVirtualId.objectName(u), MeltVirtualId.objectName(u))
    assertEquals(MeltVirtualId.fileBaseName(u), MeltVirtualId.fileBaseName(u))
  }

  test("object name is a valid Scala identifier") {
    val u = "file:///proj/My-Weird.Page.melt"
    assert(MeltVirtualId.objectName(u).matches("[A-Za-z_][A-Za-z0-9_]*"), MeltVirtualId.objectName(u))
  }

  test("filename keeps the original basename for readability and is a safe identifier") {
    val u    = "file:///proj/My-Page.melt"
    val base = MeltVirtualId.fileBaseName(u)
    assert(base.startsWith("My_Page_"), base)
    assert(base.matches("[A-Za-z0-9_]+"), base)
  }
