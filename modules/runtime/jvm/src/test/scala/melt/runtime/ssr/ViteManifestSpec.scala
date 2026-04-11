/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.ssr

import java.io.FileNotFoundException
import java.nio.file.{ Files, Paths }

import munit.FunSuite

/** Phase C tests for [[ViteManifest]]: JSON parsing + recursive
  * dependency resolution (§C1).
  */
class ViteManifestSpec extends FunSuite:

  // ── Minimal JSON parsing ──────────────────────────────────────────────

  test("fromString parses an empty manifest") {
    val m = ViteManifest.fromString("{}")
    assertEquals(m.chunksFor("counter"), Nil)
    assertEquals(m.cssFor("counter"), Nil)
  }

  test("fromString parses a single entry") {
    val json =
      """{
        |  "scalajs:counter.js": {
        |    "file": "assets/counter-a3f8c2.js",
        |    "isEntry": true
        |  }
        |}""".stripMargin
    val m = ViteManifest.fromString(json)
    assertEquals(m.chunksFor("counter"), List("assets/counter-a3f8c2.js"))
    assertEquals(m.cssFor("counter"), Nil)
  }

  test("fromString resolves recursive imports") {
    val json =
      """{
        |  "scalajs:counter.js": {
        |    "file": "assets/counter-a3.js",
        |    "imports": ["_shared-7e.js"],
        |    "isEntry": true
        |  },
        |  "_shared-7e.js": {
        |    "file": "assets/shared-7e.js"
        |  }
        |}""".stripMargin
    val m = ViteManifest.fromString(json)
    // Shared chunk is emitted first, owning chunk last.
    assertEquals(
      m.chunksFor("counter"),
      List("assets/shared-7e.js", "assets/counter-a3.js")
    )
  }

  test("fromString collects CSS files from all transitively imported entries") {
    val json =
      """{
        |  "scalajs:counter.js": {
        |    "file": "assets/counter-a3.js",
        |    "imports": ["_shared-7e.js"],
        |    "css": ["assets/counter-b1.css"]
        |  },
        |  "_shared-7e.js": {
        |    "file": "assets/shared-7e.js",
        |    "css": ["assets/base-c2.css"]
        |  }
        |}""".stripMargin
    val m = ViteManifest.fromString(json)
    assertEquals(
      m.cssFor("counter"),
      List("assets/base-c2.css", "assets/counter-b1.css")
    )
  }

  test("fromString deduplicates repeated imports") {
    // counter -> shared, todo -> shared; resolving both must not
    // produce shared twice for the same moduleID lookup.
    val json =
      """{
        |  "scalajs:counter.js": {
        |    "file": "assets/counter.js",
        |    "imports": ["_shared.js", "_shared.js"]
        |  },
        |  "_shared.js": { "file": "assets/shared.js" }
        |}""".stripMargin
    val m = ViteManifest.fromString(json)
    assertEquals(
      m.chunksFor("counter"),
      List("assets/shared.js", "assets/counter.js")
    )
  }

  test("fromString tolerates circular imports") {
    val json =
      """{
        |  "scalajs:counter.js": {
        |    "file": "a.js",
        |    "imports": ["_b.js"]
        |  },
        |  "_b.js": {
        |    "file": "b.js",
        |    "imports": ["scalajs:counter.js"]
        |  }
        |}""".stripMargin
    val m      = ViteManifest.fromString(json)
    val chunks = m.chunksFor("counter")
    // The important property is termination and correct dedup — the
    // exact order depends on the traversal but each file must appear
    // at most once.
    assertEquals(chunks.toSet, Set("a.js", "b.js"))
    assertEquals(chunks.length, chunks.distinct.length)
  }

  test("unknown moduleID yields empty chunk list") {
    val json = """{ "scalajs:counter.js": { "file": "assets/counter.js" } }"""
    val m    = ViteManifest.fromString(json)
    assertEquals(m.chunksFor("todo"), Nil)
    assertEquals(m.cssFor("todo"), Nil)
  }

  test("uriPrefix override lets non-scalajs bundlers plug in") {
    // Keys are always `<prefix>:<moduleId>.js` — the prefix is exactly
    // what precedes the colon.
    val json = """{ "app:counter.js": { "file": "assets/counter.js" } }"""
    val m    = ViteManifest.fromString(json, uriPrefix = "app")
    assertEquals(m.chunksFor("counter"), List("assets/counter.js"))
  }

  // ── Parser edge cases ────────────────────────────────────────────────

  test("parser handles nested whitespace and newlines") {
    val json =
      """|{
         |    "scalajs:x.js" :
         |      { "file"  :  "a.js"  }
         |}""".stripMargin
    val m = ViteManifest.fromString(json)
    assertEquals(m.chunksFor("x"), List("a.js"))
  }

  test("parser handles escaped characters in strings") {
    val json = """{ "scalajs:x.js": { "file": "a\\b.js" } }"""
    val m    = ViteManifest.fromString(json)
    assertEquals(m.chunksFor("x"), List("a\\b.js"))
  }

  test("parser rejects malformed JSON with a clear error") {
    intercept[IllegalArgumentException] {
      ViteManifest.fromString("{ not-json }")
    }
  }

  // ── I/O ──────────────────────────────────────────────────────────────

  test("load raises FileNotFoundException with a helpful message") {
    val e = intercept[FileNotFoundException] {
      ViteManifest.load("/nope/manifest.json")
    }
    assert(e.getMessage.contains("vite build"), e.getMessage)
  }

  test("loadFromPath reads a real file") {
    val tmp = Files.createTempFile("melt-manifest", ".json")
    try
      Files.writeString(tmp, """{ "scalajs:counter.js": { "file": "a.js" } }""")
      val m = ViteManifest.loadFromPath(tmp)
      assertEquals(m.chunksFor("counter"), List("a.js"))
    finally Files.deleteIfExists(tmp)
  }

  // ── empty / fromEntries ───────────────────────────────────────────────

  test("empty manifest returns nothing for any moduleID") {
    assertEquals(ViteManifest.empty.chunksFor("anything"), Nil)
    assertEquals(ViteManifest.empty.cssFor("anything"), Nil)
  }

  test("fromEntries constructs a manifest from a plain map") {
    val m = ViteManifest.fromEntries(
      Map(
        "scalajs:counter.js" -> ViteManifest.Entry(
          file    = "assets/counter.js",
          imports = List("_shared.js")
        ),
        "_shared.js" -> ViteManifest.Entry(file = "assets/shared.js")
      )
    )
    assertEquals(m.chunksFor("counter"), List("assets/shared.js", "assets/counter.js"))
  }
