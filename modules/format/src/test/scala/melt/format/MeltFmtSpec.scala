/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.format

import java.nio.file.{ Files, Path }

class MeltFmtSpec extends munit.FunSuite:

  private val confFixture: Fixture[Path] = new Fixture[Path]("scalafmt-conf"):
    private var path:         Path = null
    def apply():              Path = path
    override def beforeAll(): Unit =
      path = Files.createTempFile("melt-scalafmt", ".conf")
      Files.writeString(path, "version = 3.11.0\nrunner.dialect = scala3\nmaxColumn = 120\n")
    override def afterAll(): Unit = Files.deleteIfExists(path)

  override def munitFixtures: Seq[Fixture[?]] = List(confFixture)

  private def fmt(src: String): String =
    MeltFmt.format(src, confFixture()) match
      case Right(s)  => s
      case Left(err) => fail(s"unexpected Left: $err")

  test("formats the script but leaves template and style untouched") {
    val src =
      "<script lang=\"scala\">\n" +
        "  val x=State(0)\n" +
        "  f(x)\n" +
        "</script>\n" +
        "<div class=\"c\">{x}</div>\n" +
        "<style>\n" +
        "  .c{color:red}\n" +
        "</style>\n"
    val out = fmt(src)
    // script normalised
    assert(out.contains("val x = State(0)"), out)
    // template preserved verbatim
    assert(out.contains("<div class=\"c\">{x}</div>"), out)
    // style preserved verbatim (Phase 1 passthrough — not reformatted)
    assert(out.contains("  .c{color:red}"), out)
    // tags intact
    assert(out.contains("<script lang=\"scala\">") && out.contains("</script>"), out)
  }

  test("idempotent on a full .melt source") {
    val src =
      "<script lang=\"scala\">\n  val a=1\n  g(a)\n</script>\n<p>{a}</p>\n"
    val once  = fmt(src)
    val twice = fmt(once)
    assertEquals(twice, once)
  }

  test("already-formatted source is unchanged") {
    val src =
      "<script lang=\"scala\">\n  val a = 1\n  g(a)\n</script>\n<p>{a}</p>\n"
    assertEquals(fmt(src), src)
  }
