/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.analysis

class EnvCheckerSpec extends munit.FunSuite:

  private def check(source: String) = EnvChecker.checkErrors(source)

  test("no error when a client component reads no env") {
    assertEquals(check("val label = props.name\nval n = count.value"), Nil)
  }

  test("error on sys.env with the correct line and a helpful message") {
    val src =
      """<script lang="scala">
        |val key = sys.env("API_KEY")
        |</script>""".stripMargin
    val result = check(src)
    assertEquals(result.length, 1)
    assertEquals(result.head._2, 2) // line 2
    assert(result.head._1.contains("sys.env"), result.head._1)
    assert(result.head._1.contains("server"), result.head._1)
  }

  test("error on System.getenv") {
    val result = check("val k = System.getenv(\"SECRET\")")
    assertEquals(result.length, 1)
    assert(result.head._1.contains("System.getenv"), result.head._1)
  }

  test("error on a PrivateEnv member access") {
    val result = check("val db = PrivateEnv.required[String](\"DATABASE_URL\")")
    assertEquals(result.length, 1)
    assert(result.head._1.contains("PrivateEnv"), result.head._1)
    assert(result.head._1.contains("server-only"), result.head._1)
  }

  test("catches env access inside a template expression / handler, not just <script>") {
    val src = """<a href={sys.env("BASE")}>x</a>"""
    assertEquals(check(src).length, 1)
  }

  test("does not match unrelated identifiers") {
    // `sys.environment` / `myPrivateEnvVar` / a bare `PrivateEnv` mention are not reads
    assertEquals(check("val e = sys.environment\nval v = myPrivateEnvVar\n// PrivateEnv is server-only"), Nil)
  }

  test("reports each occurrence with its own line") {
    val src =
      """val a = sys.env("A")
        |val b = 1
        |val c = System.getenv("C")""".stripMargin
    val result = check(src)
    assertEquals(result.length, 2)
    assertEquals(result(0)._2, 1)
    assertEquals(result(1)._2, 3)
  }
