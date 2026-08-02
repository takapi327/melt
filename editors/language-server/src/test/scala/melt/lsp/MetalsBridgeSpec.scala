/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.lsp

import java.nio.file.Files

/** Tests for [[MetalsBridge]] lifecycle robustness (no live Metals required). */
class MetalsBridgeSpec extends munit.FunSuite:

  test("shutdown removes the temp workspace directory") {
    val bridge = new MetalsBridge
    val dir    = bridge.workspaceDir
    assert(Files.isDirectory(dir), s"workspace should exist after construction: $dir")
    bridge.shutdown()
    assert(!Files.exists(dir), s"workspace should be deleted after shutdown: $dir")
  }

  test("shutdown is idempotent") {
    val bridge = new MetalsBridge
    bridge.shutdown()
    bridge.shutdown() // must not throw even though the workspace is already gone
  }

  test("hoverForScript returns None when Metals is not running") {
    val bridge = new MetalsBridge
    try
      val vf = VirtualFileGenerator.generate(
        "<script lang=\"scala\">\n  val x = 1\n</script>",
        "Melt_test"
      )
      assertEquals(bridge.hoverForScript("file:///tmp/A.melt", vf, 1, 6), None)
    finally bridge.shutdown()
  }
