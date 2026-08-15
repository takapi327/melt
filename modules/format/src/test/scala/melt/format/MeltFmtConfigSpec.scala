/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.format

import java.nio.file.{ Files, Path }

class MeltFmtConfigSpec extends munit.FunSuite:

  private def tmpDir(): Path = Files.createTempDirectory("meltfmt-cfg")

  private def write(dir: Path, name: String, content: String): Path =
    val p = dir.resolve(name)
    Files.writeString(p, content)
    p

  test("no file → all defaults") {
    val dir = tmpDir()
    assertEquals(MeltFmtConfig.loadFrom(dir.resolve("x.melt")), Right(MeltFmtConfig.default))
    assertEquals(MeltFmtConfig.default.css.indent, 2)
    assertEquals(MeltFmtConfig.default.css.selectorList, SelectorListStyle.Newline)
  }

  test("reads melt.css.indent and selectorList") {
    val dir = tmpDir()
    write(dir, ".meltfmt.conf", """melt { css { indent = 4, selectorList = "single-line" } }""")
    val cfg = MeltFmtConfig.loadFrom(dir.resolve("a/b/x.melt")).fold(fail(_), identity)
    assertEquals(cfg.css.indent, 4)
    assertEquals(cfg.css.selectorList, SelectorListStyle.SingleLine)
  }

  test("missing keys fall back to defaults") {
    val dir = tmpDir()
    write(dir, ".meltfmt.conf", """melt { css { indent = 3 } }""")
    val cfg = MeltFmtConfig.loadFrom(dir.resolve("x.melt")).fold(fail(_), identity)
    assertEquals(cfg.css.indent, 3)
    assertEquals(cfg.css.selectorList, SelectorListStyle.Newline) // default
    assertEquals(cfg.script.indent, 2)                            // default
  }

  test("reads melt.script.indent (defaults to 2)") {
    val dir = tmpDir()
    assertEquals(MeltFmtConfig.default.script.indent, 2)
    write(dir, ".meltfmt.conf", """melt { script { indent = 4 } }""")
    val cfg = MeltFmtConfig.loadFrom(dir.resolve("x.melt")).fold(fail(_), identity)
    assertEquals(cfg.script.indent, 4)
    assertEquals(cfg.css.indent, 2) // css untouched → default
  }

  test("reads melt.template.content (defaults to inline)") {
    val dir = tmpDir()
    assertEquals(MeltFmtConfig.default.template.content, TemplateContentStyle.Inline)
    write(dir, ".meltfmt.conf", """melt { template { content = "expanded" } }""")
    val cfg = MeltFmtConfig.loadFrom(dir.resolve("x.melt")).fold(fail(_), identity)
    assertEquals(cfg.template.content, TemplateContentStyle.Expanded)
  }

  test("include \".scalafmt.conf\" is tolerated (scalafmt keys ignored)") {
    val dir = tmpDir()
    write(dir, ".scalafmt.conf", "version = 3.11.0\nrunner.dialect = scala3\nmaxColumn = 120\n")
    write(dir, ".meltfmt.conf", "include \".scalafmt.conf\"\nmelt { css { indent = 2 } }\n")
    val cfg = MeltFmtConfig.loadFrom(dir.resolve("x.melt")).fold(fail(_), identity)
    assertEquals(cfg.css.indent, 2) // reads melt.* fine even with scalafmt keys merged in
  }

  test("find walks up the directory tree") {
    val dir  = tmpDir()
    val deep = Files.createDirectories(dir.resolve("a/b/c"))
    write(dir, ".meltfmt.conf", """melt { css { indent = 8 } }""")
    val found = MeltFmtConfig.find(deep.resolve("Comp.melt"))
    assert(found.isDefined, found.toString)
    assertEquals(MeltFmtConfig.load(found.get).fold(fail(_), identity).css.indent, 8)
  }

  test("malformed HOCON → Left") {
    val dir = tmpDir()
    val p   = write(dir, ".meltfmt.conf", "melt { css { indent = ")
    assert(MeltFmtConfig.load(p).isLeft)
  }
