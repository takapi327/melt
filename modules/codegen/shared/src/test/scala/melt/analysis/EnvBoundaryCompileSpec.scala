/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.analysis

import melt.{ CompileMode, MeltCompiler }

/** Integration test that the server-only env guard is wired into the compiler and
  * gated by codegen mode: reading env in a component that compiles for the browser
  * (SPA) is a compile error, while the same component in SSR mode compiles (env is
  * read legitimately on the server).
  */
class EnvBoundaryCompileSpec extends munit.FunSuite:

  private val readsEnv =
    """<script lang="scala">
      |val key = sys.env("SECRET")
      |</script>
      |<div>{key}</div>""".stripMargin

  test("SPA (browser) codegen rejects a component that reads env") {
    val r = MeltCompiler.compile(readsEnv, "App.melt", "App", "", mode = CompileMode.SPA)
    assert(r.errors.nonEmpty, "expected a compile error")
    assert(r.errors.exists(_.message.contains("sys.env")), r.errors.map(_.message).mkString("; "))
  }

  test("SSR (server) codegen allows the same component to read env") {
    val r = MeltCompiler.compile(readsEnv, "App.melt", "App", "", mode = CompileMode.SSR)
    assert(r.errors.isEmpty, r.errors.map(_.message).mkString("; "))
  }
