/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.ssr

import java.io.FileNotFoundException
import java.nio.file.{ Files, Paths }

import munit.FunSuite

/** JVM-specific I/O tests for [[ViteManifest]]. */
class ViteManifestJvmSpec extends FunSuite:

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
