/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.format

import java.nio.file.{ Files, Path }

class ScriptFormatterSpec extends munit.FunSuite:

  // A self-contained scalafmt config (mirrors the repo: scala3, align) so the
  // test does not depend on cwd. Uses the repo's pinned version.
  private val confFixture: Fixture[Path] = new Fixture[Path]("scalafmt-conf"):
    private var path: Path = null
    def apply(): Path = path
    override def beforeAll(): Unit =
      path = Files.createTempFile("melt-scalafmt", ".conf")
      Files.writeString(
        path,
        """|version = 3.11.0
           |runner.dialect = scala3
           |maxColumn = 120
           |align.preset = more
           |""".stripMargin
      )
    override def afterAll(): Unit = Files.deleteIfExists(path)

  override def munitFixtures: Seq[Fixture[?]] = List(confFixture)

  private def fmt(inner: String): String =
    new ScriptFormatter(confFixture()).format(inner) match
      case Right(s)  => s
      case Left(err) => fail(s"unexpected Left: $err")

  test("formats a script with a top-level statement (no dialect override needed)") {
    val out = fmt("\n  val users = State(List.empty[User])\n  Api.fetchUsers(users)\n")
    assert(out.contains("Api.fetchUsers(users)"), out)                 // top-level stmt survives
    assert(out.contains("val users = State(List.empty[User])"), out)
    assert(out.startsWith("\n  val users"), out)                       // indent level (2) preserved
    assert(out.endsWith("\n"), out)
  }

  test("reformats messy code (spacing normalised, = aligned)") {
    val out = fmt("\n  val x=State(0)\n  val yy   =State(1)\n")
    assert(out.contains("val x") && out.contains("State(0)"), out)
    assert(out.contains("val yy") && out.contains("State(1)"), out)
    assert(!out.contains("=State"), out)                               // spacing normalised
    assert(out.linesIterator.filter(_.trim.nonEmpty).forall(_.startsWith("  ")), out) // indent 2
  }

  test("preserves Melt string-imports (not valid Scala) at the top") {
    val out = fmt("\n  import \"/styles/global.css\"\n  val path=Router.currentPath\n")
    assert(out.contains("import \"/styles/global.css\""), out)
    assert(out.contains("val path = Router.currentPath"), out)
  }

  test("idempotent: second format is a no-op") {
    val once  = fmt("\n  val a=1\n  val bb=2\n  f(a)\n")
    val twice = fmt(once)
    assertEquals(twice, once)
  }

  test("column-0 script (docs style) is normalised to a 2-space indent") {
    val out = fmt("\nval t = GuideI18n(props.lang).ssr\nval base = props.basePath\n")
    assert(out.contains("GuideI18n(props.lang).ssr"), out)
    // Every script body is normalised to a 2-space left margin (the compiler's
    // SectionSplitter dedents scripts, so this compiles — see the design memo).
    assert(out.linesIterator.filter(_.trim.nonEmpty).forall(_.startsWith("  ")), out)
    assert(!out.linesIterator.exists(l => l.startsWith("   ") && l.trim.nonEmpty), out) // exactly 2 at top level
  }

  test("Left on a genuine syntax error") {
    val res = new ScriptFormatter(confFixture()).format("\n  val x = = = \n")
    assert(res.isLeft, res.toString)
  }
